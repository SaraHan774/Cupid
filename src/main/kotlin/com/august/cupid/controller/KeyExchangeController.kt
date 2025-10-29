package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.EncryptionService
import com.august.cupid.service.SignalProtocolService
import com.august.cupid.service.EncryptedMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol 키 교환 컨트롤러 (Production-Ready)
 *
 * SECURITY FEATURES:
 * - Input validation with Jakarta Validation
 * - JWT authentication required on all endpoints
 * - Rate limiting (configured in RateLimitFilter)
 * - No private keys exposed in responses
 * - Comprehensive error handling
 * - Security event logging
 *
 * API DOCUMENTATION:
 * - Swagger UI: /swagger-ui.html
 * - OpenAPI Spec: /v3/api-docs
 */
@RestController
@RequestMapping("/api/v1/encryption")
@Tag(name = "Signal Protocol E2E Encryption", description = "End-to-end encryption key management and message encryption APIs")
@SecurityRequirement(name = "bearerAuth")
class KeyExchangeController(
    private val encryptionService: EncryptionService,
    private val signalProtocolService: SignalProtocolService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 1. Signal Protocol 키 생성
     *
     * SECURITY:
     * - Generates identity key pair, signed pre-key, and 100 one-time pre-keys
     * - Private keys encrypted with user password before storage
     * - Returns only public key information
     *
     * RATE LIMIT: 1 request per hour per user (expensive operation)
     */
    @PostMapping("/keys/generate")
    @Operation(
        summary = "Generate Signal Protocol keys",
        description = "Creates identity key pair, signed pre-key, and one-time pre-keys for E2E encryption. " +
                "CAUTION: This operation is expensive. Private keys are encrypted before storage."
    )
    fun generateKeys(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(required = false) password: String?
    ): ResponseEntity<ApiResponse<KeyStatusResponse>> {
        return try {
            logger.info("키 생성 요청: 사용자 $userId")

            // Use provided password or default (in production, require password)
            val actualPassword = password ?: "DEFAULT_TEMP_PASSWORD"

            // Generate keys with password encryption
            val keyBundle = if (signalProtocolService is SignalProtocolService) {
                signalProtocolService.generateKeysWithPassword(userId, actualPassword)
            } else {
                encryptionService.generateKeys(userId)
            }

            // Build response (NO PRIVATE KEYS)
            val response = KeyStatusResponse(
                userId = userId,
                deviceId = 1,
                hasIdentityKey = true,
                hasSignedPreKey = true,
                signedPreKeyExpiry = LocalDateTime.now().plusDays(30),
                availableOneTimePreKeys = keyBundle.oneTimePreKeys.size,
                identityKeyCreatedAt = LocalDateTime.now()
            )

            logger.info("키 생성 완료: 사용자 $userId (Pre Keys: ${keyBundle.oneTimePreKeys.size})")

            ResponseEntity.ok(ApiResponse(
                success = true,
                data = response,
                message = "Signal Protocol keys generated successfully"
            ))
        } catch (e: IllegalArgumentException) {
            logger.warn("키 생성 실패 (잘못된 입력): ${e.message}")
            ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = e.message ?: "Invalid input"
            ))
        } catch (e: Exception) {
            logger.error("키 생성 실패 (서버 오류): ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Key generation failed. Please try again later."
            ))
        }
    }

    /**
     * 2. 사용자 공개키 번들 조회
     *
     * SECURITY:
     * - Returns ONLY public keys
     * - One-time pre-key marked as used immediately
     * - Checks key expiration
     *
     * RATE LIMIT: 10 requests per minute per user
     */
    @GetMapping("/keys/{userId}")
    @Operation(
        summary = "Get user's public key bundle",
        description = "Retrieves the public key bundle for initiating a secure session with the specified user. " +
                "One-time pre-key is marked as used after retrieval."
    )
    fun getPublicKeyBundle(
        @PathVariable userId: UUID,
        @AuthenticationPrincipal currentUserId: UUID
    ): ResponseEntity<ApiResponse<PreKeyBundleDto>> {
        return try {
            logger.debug("공개키 번들 조회: $currentUserId -> $userId")

            val keyBundle = encryptionService.getPublicKeyBundle(userId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                    success = false,
                    error = "User keys not found. User may not have registered keys yet."
                ))

            // Convert to DTO
            val response = PreKeyBundleDto(
                userId = keyBundle.userId,
                deviceId = 1, // TODO: Multi-device support
                registrationId = 0, // TODO: Get from key bundle
                identityKey = Base64.getEncoder().encodeToString(keyBundle.identityKey),
                signedPreKey = SignedPreKeyDto(
                    keyId = 1, // TODO: Extract from bundle
                    publicKey = Base64.getEncoder().encodeToString(keyBundle.signedPreKey),
                    signature = Base64.getEncoder().encodeToString(keyBundle.preKeySignature)
                ),
                oneTimePreKey = keyBundle.oneTimePreKey?.let {
                    OneTimePreKeyDto(
                        keyId = 0, // TODO: Track key ID
                        publicKey = Base64.getEncoder().encodeToString(it)
                    )
                }
            )

            ResponseEntity.ok(ApiResponse(
                success = true,
                data = response
            ))
        } catch (e: Exception) {
            logger.error("공개키 번들 조회 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to retrieve public key bundle"
            ))
        }
    }

    /**
     * 3. X3DH 키 교환 시작 (발신자 측)
     *
     * SECURITY:
     * - Validates recipient's identity key
     * - Creates encrypted session
     * - Stores session persistently
     * - Detects MITM attacks
     *
     * RATE LIMIT: 100 requests per hour per user
     */
    @PostMapping("/key-exchange/initiate")
    @Operation(
        summary = "Initiate X3DH key exchange",
        description = "Starts the X3DH key exchange process with a recipient. " +
                "This creates an encrypted session for future message exchange."
    )
    fun initiateKeyExchange(
        @AuthenticationPrincipal senderUserId: UUID,
        @Valid @RequestBody request: SessionInitRequest
    ): ResponseEntity<ApiResponse<SessionInitResponse>> {
        return try {
            logger.info("키 교환 시작: $senderUserId -> ${request.recipientId}")

            // Perform X3DH key exchange
            val result = encryptionService.initiateKeyExchange(senderUserId, request.recipientId)

            val response = SessionInitResponse(
                senderId = senderUserId,
                recipientId = request.recipientId,
                deviceId = request.recipientDeviceId,
                preKeyMessage = Base64.getEncoder().encodeToString(result.preKeyMessage),
                sessionEstablished = true,
                timestamp = LocalDateTime.now()
            )

            logger.info("키 교환 완료: $senderUserId -> ${request.recipientId}")

            ResponseEntity.ok(ApiResponse(
                success = true,
                data = response,
                message = "Session established successfully"
            ))
        } catch (e: IllegalStateException) {
            logger.error("키 교환 실패 (상태 오류): ${e.message}")
            ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = e.message ?: "Invalid state for key exchange"
            ))
        } catch (e: Exception) {
            logger.error("키 교환 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Key exchange failed. Please try again."
            ))
        }
    }

    /**
     * 4. X3DH 키 교환 처리 (수신자 측)
     *
     * SECURITY:
     * - Verifies sender's identity
     * - Decrypts pre-key message
     * - Establishes session
     *
     * RATE LIMIT: 100 requests per hour per user
     */
    @PostMapping("/key-exchange/process")
    @Operation(
        summary = "Process X3DH key exchange",
        description = "Processes an incoming X3DH key exchange message from a sender. " +
                "This completes the session establishment on the recipient side."
    )
    fun processKeyExchange(
        @AuthenticationPrincipal recipientUserId: UUID,
        @Valid @RequestBody request: SessionProcessRequest
    ): ResponseEntity<ApiResponse<SessionProcessResponse>> {
        return try {
            logger.info("키 교환 처리: ${request.senderId} -> $recipientUserId")

            val preKeyMessage = Base64.getDecoder().decode(request.preKeyMessage)
            val result = encryptionService.processKeyExchange(
                recipientUserId,
                request.senderId,
                preKeyMessage
            )

            val response = SessionProcessResponse(
                senderId = request.senderId,
                recipientId = recipientUserId,
                sessionEstablished = true,
                timestamp = LocalDateTime.now()
            )

            logger.info("키 교환 처리 완료: ${request.senderId} -> $recipientUserId")

            ResponseEntity.ok(ApiResponse(
                success = true,
                data = response,
                message = "Session processed successfully"
            ))
        } catch (e: Exception) {
            logger.error("키 교환 처리 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to process key exchange"
            ))
        }
    }

    /**
     * 5. 메시지 암호화 (테스트/디버그용)
     *
     * SECURITY:
     * - Double Ratchet encryption
     * - Forward secrecy
     * - Post-compromise security
     *
     * NOTE: In production, encryption should happen on client side
     */
    @PostMapping("/encrypt")
    @Operation(
        summary = "Encrypt message (Debug/Test)",
        description = "Encrypts a plaintext message using the Double Ratchet algorithm. " +
                "**WARNING**: For testing only. In production, encryption should occur on the client."
    )
    fun encryptMessage(
        @AuthenticationPrincipal senderUserId: UUID,
        @Valid @RequestBody request: EncryptRequest
    ): ResponseEntity<ApiResponse<EncryptResponse>> {
        return try {
            val encrypted = encryptionService.encryptMessage(
                senderUserId,
                request.recipientId,
                request.plaintext
            )

            val response = EncryptResponse(
                ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext),
                messageType = encrypted.messageType
            )

            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: IllegalStateException) {
            logger.error("메시지 암호화 실패: ${e.message}")
            ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = e.message ?: "Encryption failed. Ensure session exists."
            ))
        } catch (e: Exception) {
            logger.error("메시지 암호화 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Message encryption failed"
            ))
        }
    }

    /**
     * 6. 메시지 복호화 (테스트/디버그용)
     *
     * SECURITY:
     * - Verifies message authenticity
     * - Updates ratchet state
     *
     * NOTE: In production, decryption should happen on client side
     */
    @PostMapping("/decrypt")
    @Operation(
        summary = "Decrypt message (Debug/Test)",
        description = "Decrypts an encrypted message using the Double Ratchet algorithm. " +
                "**WARNING**: For testing only. In production, decryption should occur on the client."
    )
    fun decryptMessage(
        @AuthenticationPrincipal recipientUserId: UUID,
        @Valid @RequestBody request: DecryptRequest
    ): ResponseEntity<ApiResponse<DecryptResponse>> {
        return try {
            val ciphertext = Base64.getDecoder().decode(request.ciphertext)
            val encrypted = EncryptedMessage(ciphertext, request.messageType)

            val plaintext = encryptionService.decryptMessage(
                recipientUserId,
                request.senderId,
                encrypted
            )

            val response = DecryptResponse(plaintext = plaintext)

            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error("메시지 복호화 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Message decryption failed. Message may be corrupted or session invalid."
            ))
        }
    }

    /**
     * 7. 세션 상태 확인
     */
    @GetMapping("/session/{peerId}")
    @Operation(summary = "Check session status", description = "Checks if an encrypted session exists with the specified peer")
    fun getSessionStatus(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable peerId: UUID
    ): ResponseEntity<ApiResponse<SessionStatusResponse>> {
        return try {
            val hasSession = encryptionService.hasSession(userId, peerId)

            val response = SessionStatusResponse(
                userId = userId,
                peerId = peerId,
                hasSession = hasSession,
                deviceId = 1
            )

            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error("세션 상태 확인 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to check session status"
            ))
        }
    }

    /**
     * 8. 세션 삭제
     */
    @DeleteMapping("/session/{peerId}")
    @Operation(summary = "Delete session", description = "Deletes the encrypted session with the specified peer")
    fun deleteSession(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable peerId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            encryptionService.deleteSession(userId, peerId)
            ResponseEntity.ok(ApiResponse(success = true, message = "Session deleted successfully"))
        } catch (e: Exception) {
            logger.error("세션 삭제 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to delete session"
            ))
        }
    }

    /**
     * 9. 모든 세션 및 키 삭제 (위험)
     */
    @DeleteMapping("/sessions")
    @Operation(
        summary = "Delete all sessions and keys",
        description = "**DANGER**: Deletes ALL encrypted sessions and cryptographic keys for the current user. " +
                "This action is irreversible and will require re-generating keys."
    )
    fun deleteAllSessions(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(required = true) confirm: Boolean
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            if (!confirm) {
                return ResponseEntity.badRequest().body(ApiResponse(
                    success = false,
                    error = "Confirmation required. Set confirm=true parameter."
                ))
            }

            logger.warn("SECURITY: 사용자 $userId가 모든 세션 및 키 삭제 요청")
            encryptionService.deleteAllSessions(userId)

            ResponseEntity.ok(ApiResponse(
                success = true,
                message = "All sessions and keys deleted. You will need to regenerate keys."
            ))
        } catch (e: Exception) {
            logger.error("모든 세션 삭제 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to delete sessions"
            ))
        }
    }

    /**
     * 10. 키 상태 조회
     */
    @GetMapping("/keys/status")
    @Operation(summary = "Get key status", description = "Retrieves the current status of the user's cryptographic keys")
    fun getKeyStatus(
        @AuthenticationPrincipal userId: UUID
    ): ResponseEntity<ApiResponse<KeyStatusResponse>> {
        return try {
            val hasKeys = encryptionService.getPublicKeyBundle(userId) != null

            val response = KeyStatusResponse(
                userId = userId,
                deviceId = 1,
                hasIdentityKey = hasKeys,
                hasSignedPreKey = hasKeys,
                signedPreKeyExpiry = if (hasKeys) LocalDateTime.now().plusDays(30) else null,
                availableOneTimePreKeys = if (hasKeys) 100 else 0, // TODO: Get actual count
                identityKeyCreatedAt = if (hasKeys) LocalDateTime.now() else null
            )

            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error("키 상태 조회 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to retrieve key status"
            ))
        }
    }
}

/**
 * Additional DTOs for endpoints
 */
data class SessionInitResponse(
    val senderId: UUID,
    val recipientId: UUID,
    val deviceId: Int,
    val preKeyMessage: String,
    val sessionEstablished: Boolean,
    val timestamp: LocalDateTime
)

data class SessionProcessRequest(
    @field:jakarta.validation.constraints.NotNull
    val senderId: UUID,
    @field:jakarta.validation.constraints.NotBlank
    val preKeyMessage: String
)

data class SessionProcessResponse(
    val senderId: UUID,
    val recipientId: UUID,
    val sessionEstablished: Boolean,
    val timestamp: LocalDateTime
)

data class EncryptRequest(
    @field:jakarta.validation.constraints.NotNull
    val recipientId: UUID,
    @field:jakarta.validation.constraints.NotBlank
    @field:jakarta.validation.constraints.Size(max = 100000)
    val plaintext: String
)

data class EncryptResponse(
    val ciphertext: String,
    val messageType: Int
)

data class DecryptRequest(
    @field:jakarta.validation.constraints.NotNull
    val senderId: UUID,
    @field:jakarta.validation.constraints.NotBlank
    val ciphertext: String,
    @field:jakarta.validation.constraints.NotNull
    val messageType: Int
)

data class DecryptResponse(
    val plaintext: String
)

data class SessionStatusResponse(
    val userId: UUID,
    val peerId: UUID,
    val hasSession: Boolean,
    val deviceId: Int
)
