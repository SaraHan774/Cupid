package com.august.cupid.controller

import com.august.cupid.model.dto.ApiResponse
import com.august.cupid.repository.KeyRotationHistoryRepository
import com.august.cupid.repository.SecurityAuditLogRepository
import com.august.cupid.repository.SignalPreKeyRepository
import com.august.cupid.repository.SignalSessionRepository
import com.august.cupid.repository.UserKeysRepository
import com.august.cupid.service.EncryptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

/**
 * 관리자 대시보드 데이터 컨트롤러
 * 
 * 기능:
 * - 키 통계 조회
 * - 사용자 암호화 상태 조회
 * - 모니터링 메트릭 제공
 * 
 * SECURITY: 관리자 권한 필요 (@PreAuthorize("hasRole('ADMIN')"))
 */
@Tag(name = "Admin - Dashboard", description = "관리자 대시보드 데이터 API")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
class AdminDashboardController(
    private val encryptionService: EncryptionService,
    private val userKeysRepository: UserKeysRepository,
    private val signalPreKeyRepository: SignalPreKeyRepository,
    private val signalSessionRepository: SignalSessionRepository,
    private val keyRotationHistoryRepository: KeyRotationHistoryRepository,
    private val securityAuditLogRepository: SecurityAuditLogRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 키 통계 조회
     * 
     * @return 키 통계 정보
     */
    @Operation(
        summary = "키 통계 조회",
        description = "전체 키 통계를 조회합니다 (총 키 개수, 활성 세션 수 등)"
    )
    @GetMapping("/keys/statistics")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getKeyStatistics(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info("키 통계 조회 요청")
            
            val now = LocalDateTime.now()
            
            // 전체 사용자 키 개수
            val totalUserKeys = userKeysRepository.count()
            
            // 활성 키 개수 (만료되지 않은 키)
            val activeUserKeys = userKeysRepository.findAll()
                .count { it.expiresAt == null || it.expiresAt.isAfter(now) }
            
            // 전체 One-Time Pre-Keys 개수
            val totalPreKeys = signalPreKeyRepository.count()
            
            // 사용 가능한 One-Time Pre-Keys 개수
            val availablePreKeys = signalPreKeyRepository.findAll()
                .count { !it.isUsed && (it.expiresAt == null || it.expiresAt.isAfter(now)) }
            
            // 활성 세션 개수
            val activeSessions = signalSessionRepository.count()
            
            // 최근 24시간 내 키 생성 수
            val recentKeyGenerations = userKeysRepository.findAll()
                .count { it.createdAt.isAfter(now.minusDays(1)) }
            
            // 최근 키 회전 이력
            val recentRotations = keyRotationHistoryRepository.findSuccessfulRotationsSince(now.minusDays(7)).size
            
            val statistics = mapOf(
                "totalUserKeys" to totalUserKeys,
                "activeUserKeys" to activeUserKeys,
                "totalPreKeys" to totalPreKeys,
                "availablePreKeys" to availablePreKeys,
                "usedPreKeys" to (totalPreKeys - availablePreKeys),
                "activeSessions" to activeSessions,
                "recentKeyGenerations24h" to recentKeyGenerations,
                "recentRotations7d" to recentRotations,
                "preKeyUtilizationRate" to if (totalPreKeys > 0) {
                    ((totalPreKeys - availablePreKeys).toDouble() / totalPreKeys * 100)
                } else {
                    0.0
                }
            )
            
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = statistics,
                message = "키 통계 조회 성공"
            ))
        } catch (e: Exception) {
            logger.error("키 통계 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "키 통계 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 사용자 암호화 상태 조회
     * 
     * @param userId 사용자 ID (선택적, 없으면 전체 사용자)
     * @return 사용자 암호화 상태
     */
    @Operation(
        summary = "사용자 암호화 상태 조회",
        description = "특정 사용자 또는 전체 사용자의 암호화 상태를 조회합니다"
    )
    @GetMapping("/users/encryption-status")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getUserEncryptionStatus(
        @RequestParam(required = false)
        @Parameter(description = "사용자 ID (선택적, 없으면 전체 사용자 통계)")
        userId: UUID?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info("사용자 암호화 상태 조회: userId={}", userId)
            
            if (userId != null) {
                // 특정 사용자 상태
                val keyStatus = encryptionService.getKeyStatus(userId)
                
                val status = mapOf(
                    "userId" to userId.toString(),
                    "hasIdentityKey" to keyStatus.hasIdentityKey,
                    "hasSignedPreKey" to keyStatus.hasSignedPreKey,
                    "signedPreKeyExpiry" to keyStatus.signedPreKeyExpiry?.toString(),
                    "availableOneTimePreKeys" to keyStatus.availableOneTimePreKeys,
                    "identityKeyCreatedAt" to keyStatus.identityKeyCreatedAt?.toString(),
                    "status" to when {
                        !keyStatus.hasIdentityKey -> "NO_KEYS"
                        keyStatus.availableOneTimePreKeys < 20 -> "LOW_PRE_KEYS"
                        keyStatus.signedPreKeyExpiry != null && keyStatus.signedPreKeyExpiry.isBefore(LocalDateTime.now().plusDays(7)) -> "KEYS_EXPIRING_SOON"
                        else -> "HEALTHY"
                    }
                )
                
                ResponseEntity.ok(ApiResponse(
                    success = true,
                    data = status,
                    message = "사용자 암호화 상태 조회 성공"
                ))
            } else {
                // 전체 사용자 통계
                val allUsers = userKeysRepository.findAll()
                val now = LocalDateTime.now()
                
                val usersWithKeys = allUsers.count { it.expiresAt == null || it.expiresAt.isAfter(now) }
                val usersWithoutKeys = allUsers.size - usersWithKeys
                
                val usersWithLowPreKeys = allUsers.count { userKey ->
                    val status = encryptionService.getKeyStatus(userKey.user.id)
                    status.availableOneTimePreKeys < 20
                }
                
                val usersWithExpiringKeys = allUsers.count { userKey ->
                    val status = encryptionService.getKeyStatus(userKey.user.id)
                    status.signedPreKeyExpiry != null && status.signedPreKeyExpiry.isBefore(now.plusDays(7))
                }
                
                val statistics = mapOf(
                    "totalUsers" to allUsers.size,
                    "usersWithKeys" to usersWithKeys,
                    "usersWithoutKeys" to usersWithoutKeys,
                    "usersWithLowPreKeys" to usersWithLowPreKeys,
                    "usersWithExpiringKeys" to usersWithExpiringKeys,
                    "encryptionCoverage" to if (allUsers.isNotEmpty()) {
                        (usersWithKeys.toDouble() / allUsers.size * 100)
                    } else {
                        0.0
                    }
                )
                
                ResponseEntity.ok(ApiResponse(
                    success = true,
                    data = statistics,
                    message = "전체 사용자 암호화 상태 통계 조회 성공"
                ))
            }
        } catch (e: Exception) {
            logger.error("사용자 암호화 상태 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "사용자 암호화 상태 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 암호화 서비스 메트릭 조회
     * 
     * @return 암호화 서비스 메트릭
     */
    @Operation(
        summary = "암호화 서비스 메트릭 조회",
        description = "암호화 서비스의 성능 및 상태 메트릭을 조회합니다"
    )
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getEncryptionMetrics(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info("암호화 서비스 메트릭 조회")
            
            val now = LocalDateTime.now()
            val last24Hours = now.minusDays(1)
            
            // 최근 24시간 내 감사 로그 통계
            val recentLogs = securityAuditLogRepository.findByCreatedAtBetween(last24Hours, now)
            
            val encryptionLogs = recentLogs.filter { 
                it.eventType.name.contains("ENCRYPTION", ignoreCase = true) ||
                it.eventType.name.contains("DECRYPTION", ignoreCase = true)
            }
            
            val successCount = encryptionLogs.count { it.success }
            val totalCount = encryptionLogs.size
            val successRate = if (totalCount > 0) {
                (successCount.toDouble() / totalCount * 100)
            } else {
                0.0
            }
            
            val avgLatency = encryptionLogs
                .filter { it.executionTimeMs != null && it.success }
                .mapNotNull { it.executionTimeMs }
                .average()
                .takeIf { !it.isNaN() }
            
            val suspiciousActivities = recentLogs.count {
                it.eventType.name.contains("SUSPICIOUS", ignoreCase = true)
            }
            
            val metrics = mapOf(
                "period" to "Last 24 hours",
                "totalOperations" to totalCount,
                "successfulOperations" to successCount,
                "failedOperations" to (totalCount - successCount),
                "successRate" to successRate,
                "averageLatencyMs" to (avgLatency?.toLong() ?: 0),
                "suspiciousActivities" to suspiciousActivities,
                "timestamp" to now.toString()
            )
            
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = metrics,
                message = "암호화 서비스 메트릭 조회 성공"
            ))
        } catch (e: Exception) {
            logger.error("암호화 서비스 메트릭 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "메트릭 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }
}

