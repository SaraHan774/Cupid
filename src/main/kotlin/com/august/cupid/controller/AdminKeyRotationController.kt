package com.august.cupid.controller

import com.august.cupid.model.dto.ApiResponse
import com.august.cupid.model.entity.KeyRotationHistory
import com.august.cupid.model.entity.RotationType
import com.august.cupid.repository.KeyRotationHistoryRepository
import com.august.cupid.scheduler.KeyRotationResult
import com.august.cupid.scheduler.KeyRotationScheduler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

/**
 * 관리자 키 회전 컨트롤러
 * 
 * 기능:
 * - 수동 키 회전 트리거
 * - 키 회전 이력 조회
 * - 회전 통계 조회
 * 
 * SECURITY: 관리자 권한 필요 (@PreAuthorize("hasRole('ADMIN')"))
 * 
 * TODO: 실제 프로덕션에서는 관리자 인증 시스템과 통합 필요
 */
@Tag(name = "Admin - Key Rotation", description = "관리자 키 회전 관리 API")
@RestController
@RequestMapping("/api/v1/admin/keys")
class AdminKeyRotationController(
    private val keyRotationScheduler: KeyRotationScheduler,
    private val keyRotationHistoryRepository: KeyRotationHistoryRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Signed Pre-Key 수동 회전 트리거
     * 
     * @param userId 회전할 사용자 ID (선택적, 없으면 모든 사용자)
     * @return 회전 결과
     */
    @Operation(
        summary = "Signed Pre-Key 수동 회전",
        description = "관리자가 특정 사용자 또는 모든 사용자의 Signed Pre-Key를 수동으로 회전합니다."
    )
    @PostMapping("/rotate/signed-pre-key")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun rotateSignedPreKey(
        @RequestParam(required = false)
        @Parameter(description = "회전할 사용자 ID (선택적, 없으면 모든 사용자)")
        userId: UUID?
    ): ResponseEntity<ApiResponse<KeyRotationResult>> {
        return try {
            logger.info("Signed Pre-Key 수동 회전 요청: userId={}", userId)
            
            val result = keyRotationScheduler.manualRotateSignedPreKey(userId)
            
            ResponseEntity.ok(ApiResponse(
                success = result.success,
                data = result,
                message = result.message
            ))
        } catch (e: Exception) {
            logger.error("Signed Pre-Key 수동 회전 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "회전 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * One-Time Pre-Keys 수동 보충 트리거
     * 
     * @param userId 보충할 사용자 ID (선택적, 없으면 모든 사용자)
     * @return 보충 결과
     */
    @Operation(
        summary = "One-Time Pre-Keys 수동 보충",
        description = "관리자가 특정 사용자 또는 모든 사용자의 One-Time Pre-Keys를 수동으로 보충합니다."
    )
    @PostMapping("/replenish/one-time-pre-keys")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun replenishOneTimePreKeys(
        @RequestParam(required = false)
        @Parameter(description = "보충할 사용자 ID (선택적, 없으면 모든 사용자)")
        userId: UUID?
    ): ResponseEntity<ApiResponse<KeyRotationResult>> {
        return try {
            logger.info("One-Time Pre-Keys 수동 보충 요청: userId={}", userId)
            
            val result = keyRotationScheduler.manualReplenishPreKeys(userId)
            
            ResponseEntity.ok(ApiResponse(
                success = result.success,
                data = result,
                message = result.message
            ))
        } catch (e: Exception) {
            logger.error("One-Time Pre-Keys 수동 보충 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "보충 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 키 회전 이력 조회
     * 
     * @param userId 사용자 ID (선택적, 없으면 모든 사용자)
     * @param rotationType 회전 타입 (선택적)
     * @param startTime 시작 시간 (선택적)
     * @param endTime 종료 시간 (선택적)
     * @param limit 결과 개수 제한 (기본값: 100)
     * @return 회전 이력 목록
     */
    @Operation(
        summary = "키 회전 이력 조회",
        description = "키 회전 이력을 조회합니다. 필터링 옵션을 제공합니다."
    )
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getRotationHistory(
        @RequestParam(required = false)
        @Parameter(description = "사용자 ID (선택적)")
        userId: UUID?,
        
        @RequestParam(required = false)
        @Parameter(description = "회전 타입 (SIGNED_PRE_KEY, ONE_TIME_PRE_KEYS)")
        rotationType: RotationType?,
        
        @RequestParam(required = false)
        @Parameter(description = "시작 시간 (ISO 8601 형식)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startTime: LocalDateTime?,
        
        @RequestParam(required = false)
        @Parameter(description = "종료 시간 (ISO 8601 형식)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endTime: LocalDateTime?,
        
        @RequestParam(defaultValue = "100")
        @Parameter(description = "결과 개수 제한")
        limit: Int
    ): ResponseEntity<ApiResponse<List<KeyRotationHistory>>> {
        return try {
            logger.info("키 회전 이력 조회: userId={}, rotationType={}, startTime={}, endTime={}",
                userId, rotationType, startTime, endTime)
            
            val history = when {
                userId != null && rotationType != null -> {
                    keyRotationHistoryRepository
                        .findByUserIdAndRotationTypeOrderByCreatedAtDesc(userId, rotationType)
                        .take(limit)
                }
                userId != null -> {
                    keyRotationHistoryRepository
                        .findByUserIdOrderByCreatedAtDesc(userId)
                        .take(limit)
                }
                startTime != null && endTime != null -> {
                    keyRotationHistoryRepository
                        .findByCreatedAtBetween(startTime, endTime)
                        .take(limit)
                }
                else -> {
                    // 기본적으로 최근 이력만 조회
                    val since = LocalDateTime.now().minusDays(30)
                    keyRotationHistoryRepository
                        .findSuccessfulRotationsSince(since)
                        .take(limit)
                }
            }
            
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = history,
                message = "이력 조회 성공: ${history.size}개"
            ))
        } catch (e: Exception) {
            logger.error("키 회전 이력 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "이력 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 키 회전 통계 조회
     * 
     * @param days 통계 기간 (일, 기본값: 30)
     * @return 회전 통계
     */
    @Operation(
        summary = "키 회전 통계 조회",
        description = "지정된 기간 동안의 키 회전 통계를 조회합니다."
    )
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getRotationStatistics(
        @RequestParam(defaultValue = "30")
        @Parameter(description = "통계 기간 (일)")
        days: Int
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info("키 회전 통계 조회: days={}", days)
            
            val since = LocalDateTime.now().minusDays(days.toLong())
            
            val successfulRotations = keyRotationHistoryRepository.findSuccessfulRotationsSince(since)
            val failedRotations = keyRotationHistoryRepository.findFailedRotationsSince(since)
            
            val statistics = mapOf(
                "periodDays" to days,
                "startTime" to since.toString(),
                "endTime" to LocalDateTime.now().toString(),
                "totalSuccessful" to successfulRotations.size,
                "totalFailed" to failedRotations.size,
                "successRate" to if (successfulRotations.size + failedRotations.size > 0) {
                    (successfulRotations.size.toDouble() / (successfulRotations.size + failedRotations.size) * 100)
                } else {
                    0.0
                },
                "byRotationType" to keyRotationHistoryRepository.countByRotationTypeSince(since)
                    .associate { it[0] as RotationType to it[1] as Long }
            )
            
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = statistics,
                message = "통계 조회 성공"
            ))
        } catch (e: Exception) {
            logger.error("키 회전 통계 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "통계 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }
}

