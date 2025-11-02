package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.security.RateLimit
import com.august.cupid.security.RateLimitKeyType
import com.august.cupid.service.KeyBackupService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Key Backup/Recovery 컨트롤러
 * 
 * 키 백업 및 복구를 위한 REST API 엔드포인트 제공
 * 경로: /api/v1/keys/backup*
 */
@RestController
@RequestMapping("/api/v1/keys")
@Tag(name = "Key Backup & Recovery", description = "Signal Protocol key backup and recovery APIs")
@SecurityRequirement(name = "bearerAuth")
class KeyBackupController(
    private val keyBackupService: KeyBackupService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Authentication에서 userId 추출 (헬퍼 메서드)
     */
    private fun getUserIdFromAuthentication(authentication: Authentication?): UUID? {
        if (authentication == null || authentication.name.isBlank()) {
            return null
        }
        return try {
            UUID.fromString(authentication.name)
        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 사용자 ID 형식: ${authentication.name}")
            null
        }
    }

    /**
     * 키 백업 생성
     * POST /api/v1/keys/backup
     *
     * SECURITY:
     * - 백업 데이터는 별도의 백업 비밀번호로 암호화
     * - AES-256-GCM 암호화 사용
     * - SHA-256 해시로 무결성 검증
     *
     * RATE LIMIT: 5 requests per hour per user
     */
    @PostMapping("/backup")
    @RateLimit(requests = 5, windowMinutes = 60, keyType = RateLimitKeyType.USER)
    @Operation(
        summary = "Create key backup",
        description = "Creates an encrypted backup of the user's Signal Protocol keys. " +
                "The backup is encrypted with a separate backup password (different from user password). " +
                "Backups expire after 90 days by default (configurable)."
    )
    fun createBackup(
        authentication: Authentication,
        @Valid @RequestBody request: KeyBackupRequest
    ): ResponseEntity<ApiResponse<KeyBackupResponse>> {
        return try {
            val userId = getUserIdFromAuthentication(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse(success = false, error = "인증이 필요합니다.", errorCode = "UNAUTHORIZED")
            )
            logger.info("키 백업 생성 요청: 사용자 $userId")

            val response = keyBackupService.createBackup(userId, request)

            ResponseEntity.ok(ApiResponse(
                success = true,
                data = response,
                message = "키 백업이 성공적으로 생성되었습니다"
            ))
        } catch (e: IllegalArgumentException) {
            logger.warn("키 백업 생성 실패 (잘못된 입력): ${e.message}")
            ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = e.message ?: "Invalid backup request"
            ))
        } catch (e: SecurityException) {
            logger.error("키 백업 생성 실패 (보안 오류): ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Key backup creation failed due to security error: ${e.message}"
            ))
        } catch (e: Exception) {
            logger.error("키 백업 생성 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Key backup creation failed. Please try again later."
            ))
        }
    }

    /**
     * 키 백업 복구
     * POST /api/v1/keys/backup/restore
     *
     * SECURITY:
     * - 백업 비밀번호로 복호화
     * - 백업 무결성 검증 (SHA-256 해시)
     * - 복구 시 백업 사용 표시 (재사용 방지)
     *
     * RATE LIMIT: 3 requests per hour per user
     */
    @PostMapping("/backup/restore")
    @RateLimit(requests = 3, windowMinutes = 60, keyType = RateLimitKeyType.USER)
    @Operation(
        summary = "Restore keys from backup",
        description = "Restores Signal Protocol keys from an encrypted backup. " +
                "Requires the backup password. After restore, the backup is marked as used and cannot be reused for security."
    )
    fun restoreBackup(
        authentication: Authentication,
        @Valid @RequestBody request: KeyBackupRestoreRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            val userId = getUserIdFromAuthentication(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse(success = false, error = "인증이 필요합니다.", errorCode = "UNAUTHORIZED")
            )
            logger.info("키 백업 복구 요청: 사용자 $userId, 백업 ID ${request.backupId}")

            val success = keyBackupService.restoreBackup(userId, request)

            if (success) {
                ResponseEntity.ok(ApiResponse(
                    success = true,
                    message = "키 백업이 성공적으로 복구되었습니다"
                ))
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                    success = false,
                    error = "Key backup restore failed"
                ))
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("키 백업 복구 실패 (잘못된 입력): ${e.message}")
            ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = e.message ?: "Invalid restore request"
            ))
        } catch (e: SecurityException) {
            logger.error("키 백업 복구 실패 (보안 오류): ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Key backup restore failed due to security error: ${e.message}"
            ))
        } catch (e: Exception) {
            logger.error("키 백업 복구 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Key backup restore failed. Please try again later."
            ))
        }
    }

    /**
     * 키 백업 목록 조회
     * GET /api/v1/keys/backup
     *
     * RATE LIMIT: 10 requests per minute per user
     */
    @GetMapping("/backup")
    @RateLimit(requests = 10, windowMinutes = 1, keyType = RateLimitKeyType.USER)
    @Operation(
        summary = "Get backup list",
        description = "Retrieves a list of all backups for the current user, including active and expired backups."
    )
    fun getBackupList(
        authentication: Authentication
    ): ResponseEntity<ApiResponse<KeyBackupListResponse>> {
        return try {
            val userId = getUserIdFromAuthentication(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse(success = false, error = "인증이 필요합니다.", errorCode = "UNAUTHORIZED")
            )
            val response = keyBackupService.getBackupList(userId)

            ResponseEntity.ok(ApiResponse(
                success = true,
                data = response
            ))
        } catch (e: Exception) {
            logger.error("백업 목록 조회 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to retrieve backup list"
            ))
        }
    }

    /**
     * 키 백업 삭제
     * DELETE /api/v1/keys/backup/{backupId}
     *
     * RATE LIMIT: 10 requests per hour per user
     */
    @DeleteMapping("/backup/{backupId}")
    @RateLimit(requests = 10, windowMinutes = 60, keyType = RateLimitKeyType.USER)
    @Operation(
        summary = "Delete backup",
        description = "Deletes a specific backup by ID. Only backups owned by the current user can be deleted."
    )
    fun deleteBackup(
        authentication: Authentication,
        @PathVariable backupId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            val userId = getUserIdFromAuthentication(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse(success = false, error = "인증이 필요합니다.", errorCode = "UNAUTHORIZED")
            )
            keyBackupService.deleteBackup(userId, backupId)

            ResponseEntity.ok(ApiResponse(
                success = true,
                message = "백업이 성공적으로 삭제되었습니다"
            ))
        } catch (e: IllegalArgumentException) {
            logger.warn("백업 삭제 실패 (잘못된 입력): ${e.message}")
            ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = e.message ?: "Invalid request"
            ))
        } catch (e: Exception) {
            logger.error("백업 삭제 실패: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "Failed to delete backup"
            ))
        }
    }
}

