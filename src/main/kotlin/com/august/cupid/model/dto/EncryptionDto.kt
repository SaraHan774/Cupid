package com.august.cupid.model.dto

import jakarta.validation.constraints.*
import java.time.LocalDateTime
import java.util.*

/**
 * Pre-Key Bundle DTO
 * Contains all keys needed to initiate a session with a user (X3DH)
 *
 * SECURITY: This contains ONLY public keys, never private keys
 */
data class PreKeyBundleDto(
    @field:NotNull(message = "User ID is required")
    val userId: UUID,

    @field:NotNull(message = "Device ID is required")
    @field:Min(value = 1, message = "Device ID must be at least 1")
    val deviceId: Int,

    @field:NotNull(message = "Registration ID is required")
    @field:Min(value = 1, message = "Registration ID must be positive")
    @field:Max(value = 16383, message = "Registration ID must be 14-bit")
    val registrationId: Int,

    @field:NotBlank(message = "Identity key is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Identity key must be valid Base64")
    val identityKey: String,

    @field:NotNull(message = "Signed pre-key is required")
    val signedPreKey: SignedPreKeyDto,

    /**
     * One-time pre-key (optional, may be null if exhausted)
     */
    val oneTimePreKey: OneTimePreKeyDto?
)

/**
 * Signed Pre-Key DTO
 */
data class SignedPreKeyDto(
    @field:NotNull(message = "Signed pre-key ID is required")
    @field:Min(value = 0, message = "Signed pre-key ID must be non-negative")
    val keyId: Int,

    @field:NotBlank(message = "Public key is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Public key must be valid Base64")
    val publicKey: String,

    @field:NotBlank(message = "Signature is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Signature must be valid Base64")
    val signature: String
)

/**
 * One-Time Pre-Key DTO
 */
data class OneTimePreKeyDto(
    @field:NotNull(message = "Pre-key ID is required")
    @field:Min(value = 0, message = "Pre-key ID must be non-negative")
    val keyId: Int,

    @field:NotBlank(message = "Public key is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Public key must be valid Base64")
    val publicKey: String
)

/**
 * Encrypted Message DTO
 * Used for sending/receiving encrypted messages
 */
data class EncryptedMessageDto(
    @field:NotNull(message = "Sender ID is required")
    val senderId: UUID,

    @field:NotNull(message = "Recipient ID is required")
    val recipientId: UUID,

    @field:NotNull(message = "Device ID is required")
    @field:Min(value = 1, message = "Device ID must be at least 1")
    val deviceId: Int,

    @field:NotBlank(message = "Encrypted content is required")
    @field:Size(max = 1000000, message = "Encrypted content too large (max 1MB)")
    val encryptedContent: String,

    /**
     * Message type (for Signal Protocol message versioning)
     */
    @field:NotNull(message = "Message type is required")
    @field:Min(value = 1, message = "Invalid message type")
    @field:Max(value = 3, message = "Invalid message type")
    val messageType: Int,

    /**
     * Registration ID for recipient verification
     */
    @field:NotNull(message = "Registration ID is required")
    val registrationId: Int
)

/**
 * Key Registration Request DTO
 * Used when a user registers their keys with the server
 */
data class KeyRegistrationRequest(
    @field:NotBlank(message = "Identity key is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Identity key must be valid Base64")
    val identityPublicKey: String,

    @field:NotNull(message = "Registration ID is required")
    @field:Min(value = 1, message = "Registration ID must be positive")
    @field:Max(value = 16383, message = "Registration ID must be 14-bit")
    val registrationId: Int,

    @field:NotNull(message = "Device ID is required")
    @field:Min(value = 1, message = "Device ID must be at least 1")
    val deviceId: Int = 1,

    @field:NotNull(message = "Signed pre-key is required")
    val signedPreKey: SignedPreKeyUploadDto,

    @field:NotNull(message = "One-time pre-keys are required")
    @field:Size(min = 1, max = 100, message = "Must provide 1-100 one-time pre-keys")
    val oneTimePreKeys: List<OneTimePreKeyUploadDto>
)

/**
 * Signed Pre-Key Upload DTO
 */
data class SignedPreKeyUploadDto(
    @field:NotNull(message = "Key ID is required")
    @field:Min(value = 0, message = "Key ID must be non-negative")
    val keyId: Int,

    @field:NotBlank(message = "Public key is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Public key must be valid Base64")
    val publicKey: String,

    @field:NotBlank(message = "Signature is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Signature must be valid Base64")
    val signature: String
)

/**
 * One-Time Pre-Key Upload DTO
 */
data class OneTimePreKeyUploadDto(
    @field:NotNull(message = "Key ID is required")
    @field:Min(value = 0, message = "Key ID must be non-negative")
    val keyId: Int,

    @field:NotBlank(message = "Public key is required")
    @field:Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Public key must be valid Base64")
    val publicKey: String
)

/**
 * Key Replenishment Request DTO
 * Used to add more one-time pre-keys
 */
data class KeyReplenishmentRequest(
    @field:NotNull(message = "One-time pre-keys are required")
    @field:Size(min = 1, max = 100, message = "Must provide 1-100 one-time pre-keys")
    val oneTimePreKeys: List<OneTimePreKeyUploadDto>
)

/**
 * Key Status Response DTO
 * Information about the user's current key status
 */
data class KeyStatusResponse(
    val userId: UUID,
    val deviceId: Int,
    val hasIdentityKey: Boolean,
    val hasSignedPreKey: Boolean,
    val signedPreKeyExpiry: LocalDateTime?,
    val availableOneTimePreKeys: Int,
    val identityKeyCreatedAt: LocalDateTime?
)

/**
 * Session Initialization Request DTO
 * Used to create a new session with a recipient
 */
data class SessionInitRequest(
    @field:NotNull(message = "Recipient ID is required")
    val recipientId: UUID,

    @field:NotNull(message = "Device ID is required")
    @field:Min(value = 1, message = "Device ID must be at least 1")
    val recipientDeviceId: Int = 1
)

/**
 * Session Initialization Response DTO
 * Contains the pre-key bundle for establishing a session
 */
data class SessionInitResponse(
    val success: Boolean,
    val preKeyBundle: PreKeyBundleDto?,
    val message: String?
)

/**
 * Encryption Response DTO
 * Result of encrypting a message
 */
data class EncryptionResponse(
    val success: Boolean,
    val encryptedMessage: EncryptedMessageDto?,
    val error: String? = null
)

/**
 * Decryption Response DTO
 * Result of decrypting a message
 */
data class DecryptionResponse(
    val success: Boolean,
    val plaintext: String?,
    val error: String? = null
)

/**
 * Key Rotation Status DTO
 * Status of key rotation operations
 */
data class KeyRotationStatus(
    val rotated: Boolean,
    val newSignedPreKeyId: Int?,
    val newPreKeysCount: Int,
    val message: String
)
