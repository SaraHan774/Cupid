package com.august.cupid.controller

import com.august.cupid.model.dto.ApiResponse
import com.august.cupid.model.entity.AuditEventType
import com.august.cupid.model.entity.SecurityAuditLog
import com.august.cupid.service.EncryptionMetrics
import com.august.cupid.service.SecurityAuditLogger
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

/**
 * 보안 감사 로그 컨트롤러
 * 
 * 기능:
 * - 보안 감사 로그 조회
 * - 암호화 작업 메트릭 조회
 * - 의심스러운 활동 감지 알림
 * 
 * SECURITY: 관리자 권한 필요 (@PreAuthorize("hasRole('ADMIN')"))
 */
@Tag(name = "Admin - Security Audit", description = "보안 감사 로그 관리 API")
@RestController
@RequestMapping("/api/v1/admin/security")
class SecurityAuditController(
    private val securityAuditLogger: SecurityAuditLogger
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 보안 감사 로그 조회
     * 
     * @param userId 사용자 ID (선택적)
     * @param eventType 이벤트 타입 (선택적)
     * @param success 성공 여부 (선택적)
     * @param startTime 시작 시간 (선택적)
     * @param endTime 종료 시간 (선택적)
     * @param page 페이지 번호 (기본값: 0)
     * @param size 페이지 크기 (기본값: 50)
     * @return 감사 로그 페이지
     */
    @Operation(
        summary = "보안 감사 로그 조회",
        description = "보안 감사 로그를 조회합니다. 필터링 옵션을 제공합니다."
    )
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getAuditLogs(
        @RequestParam(required = false)
        @Parameter(description = "사용자 ID (선택적)")
        userId: UUID?,
        
        @RequestParam(required = false)
        @Parameter(description = "이벤트 타입")
        eventType: AuditEventType?,
        
        @RequestParam(required = false)
        @Parameter(description = "성공 여부 (true/false)")
        success: Boolean?,
        
        @RequestParam(required = false)
        @Parameter(description = "시작 시간 (ISO 8601 형식)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startTime: LocalDateTime?,
        
        @RequestParam(required = false)
        @Parameter(description = "종료 시간 (ISO 8601 형식)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endTime: LocalDateTime?,
        
        @RequestParam(defaultValue = "0")
        @Parameter(description = "페이지 번호")
        page: Int,
        
        @RequestParam(defaultValue = "50")
        @Parameter(description = "페이지 크기")
        size: Int
    ): ResponseEntity<ApiResponse<Page<SecurityAuditLog>>> {
        return try {
            logger.info("보안 감사 로그 조회: userId={}, eventType={}, success={}, startTime={}, endTime={}, page={}, size={}",
                userId, eventType, success, startTime, endTime, page, size)
            
            val logs = securityAuditLogger.getAuditLogs(
                userId = userId,
                eventType = eventType,
                success = success,
                startTime = startTime,
                endTime = endTime,
                page = page,
                size = size
            )
            
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = logs,
                message = "로그 조회 성공: 총 ${logs.totalElements}개, 페이지 ${logs.number + 1}/${logs.totalPages}"
            ))
        } catch (e: Exception) {
            logger.error("보안 감사 로그 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "로그 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 암호화 작업 메트릭 조회
     * 
     * @param days 통계 기간 (일, 기본값: 7)
     * @return 암호화 메트릭
     */
    @Operation(
        summary = "암호화 작업 메트릭 조회",
        description = "지정된 기간 동안의 암호화 작업 통계 및 메트릭을 조회합니다."
    )
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getEncryptionMetrics(
        @RequestParam(defaultValue = "7")
        @Parameter(description = "통계 기간 (일)")
        days: Int
    ): ResponseEntity<ApiResponse<EncryptionMetrics>> {
        return try {
            logger.info("암호화 작업 메트릭 조회: days={}", days)
            
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusDays(days.toLong())
            
            val metrics = securityAuditLogger.getEncryptionMetrics(startTime, endTime)
            
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = metrics,
                message = "메트릭 조회 성공"
            ))
        } catch (e: Exception) {
            logger.error("암호화 작업 메트릭 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "메트릭 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 의심스러운 활동 조회
     * 
     * @param days 조회 기간 (일, 기본값: 7)
     * @param page 페이지 번호 (기본값: 0)
     * @param size 페이지 크기 (기본값: 50)
     * @return 의심스러운 활동 로그
     */
    @Operation(
        summary = "의심스러운 활동 조회",
        description = "의심스러운 활동으로 감지된 이벤트를 조회합니다."
    )
    @GetMapping("/suspicious")
    @PreAuthorize("hasRole('ADMIN')") // TODO: 실제 관리자 권한 체크 구현 필요
    fun getSuspiciousActivities(
        @RequestParam(defaultValue = "7")
        @Parameter(description = "조회 기간 (일)")
        days: Int,
        
        @RequestParam(defaultValue = "0")
        @Parameter(description = "페이지 번호")
        page: Int,
        
        @RequestParam(defaultValue = "50")
        @Parameter(description = "페이지 크기")
        size: Int
    ): ResponseEntity<ApiResponse<Page<SecurityAuditLog>>> {
        return try {
            logger.info("의심스러운 활동 조회: days={}, page={}, size={}", days, page, size)
            
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusDays(days.toLong())
            
            val logs = securityAuditLogger.getAuditLogs(
                eventType = AuditEventType.SUSPICIOUS_ACTIVITY,
                startTime = startTime,
                endTime = endTime,
                page = page,
                size = size
            )
            
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = logs,
                message = "의심스러운 활동 조회 성공: 총 ${logs.totalElements}개"
            ))
        } catch (e: Exception) {
            logger.error("의심스러운 활동 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                error = "의심스러운 활동 조회 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }
}

