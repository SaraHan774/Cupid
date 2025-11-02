package com.august.cupid.service

import com.august.cupid.model.entity.AuditEventType
import com.august.cupid.model.entity.SecurityAuditLog
import com.august.cupid.repository.SecurityAuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 보안 감사 로깅 서비스
 * 
 * 기능:
 * - 모든 암호화 작업 이벤트 로깅
 * - 성공/실패 추적
 * - 성능 메트릭 수집
 * - 의심스러운 활동 감지
 * 
 * NOTE: MongoDB에 저장되므로 비동기로 처리하여 성능 영향 최소화
 */
@Service
class SecurityAuditLogger(
    private val securityAuditLogRepository: SecurityAuditLogRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // 의심스러운 활동 임계값
        private const val SUSPICIOUS_FAILURE_THRESHOLD = 5 // 10분 내 5회 이상 실패
        private const val SUSPICIOUS_TIME_WINDOW_MINUTES = 10L
    }

    /**
     * 키 생성 이벤트 로깅
     */
    @Async
    fun logKeyGeneration(
        userId: UUID,
        success: Boolean,
        executionTimeMs: Long? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val log = SecurityAuditLog(
                userId = userId,
                eventType = AuditEventType.KEY_GENERATION,
                success = success,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs,
                metadata = metadata
            )
            securityAuditLogRepository.save(log)
            logger.debug("키 생성 이벤트 로깅: userId={}, success={}", userId, success)
        } catch (e: Exception) {
            logger.error("키 생성 이벤트 로깅 실패", e)
            // 로깅 실패는 치명적이지 않음
        }
    }

    /**
     * 키 등록 이벤트 로깅
     */
    @Async
    fun logKeyRegistration(
        userId: UUID,
        success: Boolean,
        executionTimeMs: Long? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val log = SecurityAuditLog(
                userId = userId,
                eventType = AuditEventType.KEY_REGISTRATION,
                success = success,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs,
                metadata = metadata
            )
            securityAuditLogRepository.save(log)
            logger.debug("키 등록 이벤트 로깅: userId={}, success={}", userId, success)
        } catch (e: Exception) {
            logger.error("키 등록 이벤트 로깅 실패", e)
        }
    }

    /**
     * 세션 초기화 이벤트 로깅
     */
    @Async
    fun logSessionInitialization(
        userId: UUID,
        recipientId: UUID? = null,
        success: Boolean,
        executionTimeMs: Long? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val logMetadata = mutableMapOf<String, Any>()
            recipientId?.let { logMetadata["recipient_id"] = it.toString() }
            metadata?.let { logMetadata.putAll(it) }

            val log = SecurityAuditLog(
                userId = userId,
                eventType = if (success) AuditEventType.SESSION_INITIALIZATION else AuditEventType.SESSION_FAILURE,
                success = success,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs,
                metadata = if (logMetadata.isNotEmpty()) logMetadata else null
            )
            securityAuditLogRepository.save(log)
            
            // 실패 시 의심스러운 활동 체크
            if (!success) {
                checkSuspiciousActivity(userId)
            }
            
            logger.debug("세션 초기화 이벤트 로깅: userId={}, recipientId={}, success={}", userId, recipientId, success)
        } catch (e: Exception) {
            logger.error("세션 초기화 이벤트 로깅 실패", e)
        }
    }

    /**
     * 메시지 암호화 이벤트 로깅
     */
    @Async
    fun logMessageEncryption(
        senderId: UUID,
        recipientId: UUID,
        success: Boolean,
        executionTimeMs: Long? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val logMetadata = mutableMapOf<String, Any>(
                "recipient_id" to recipientId.toString()
            )
            metadata?.let { logMetadata.putAll(it) }

            val log = SecurityAuditLog(
                userId = senderId,
                eventType = if (success) AuditEventType.MESSAGE_ENCRYPTION else AuditEventType.ENCRYPTION_FAILURE,
                success = success,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs,
                metadata = logMetadata
            )
            securityAuditLogRepository.save(log)
            
            // 실패 시 의심스러운 활동 체크
            if (!success) {
                checkSuspiciousActivity(senderId)
            }
            
            logger.debug("메시지 암호화 이벤트 로깅: senderId={}, recipientId={}, success={}", senderId, recipientId, success)
        } catch (e: Exception) {
            logger.error("메시지 암호화 이벤트 로깅 실패", e)
        }
    }

    /**
     * 메시지 복호화 이벤트 로깅
     */
    @Async
    fun logMessageDecryption(
        recipientId: UUID,
        senderId: UUID,
        success: Boolean,
        executionTimeMs: Long? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val logMetadata = mutableMapOf<String, Any>(
                "sender_id" to senderId.toString()
            )
            metadata?.let { logMetadata.putAll(it) }

            val log = SecurityAuditLog(
                userId = recipientId,
                eventType = if (success) AuditEventType.MESSAGE_DECRYPTION else AuditEventType.DECRYPTION_FAILURE,
                success = success,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs,
                metadata = logMetadata
            )
            securityAuditLogRepository.save(log)
            
            // 실패 시 의심스러운 활동 체크
            if (!success) {
                checkSuspiciousActivity(recipientId)
            }
            
            logger.debug("메시지 복호화 이벤트 로깅: recipientId={}, senderId={}, success={}", recipientId, senderId, success)
        } catch (e: Exception) {
            logger.error("메시지 복호화 이벤트 로깅 실패", e)
        }
    }

    /**
     * 키 번들 조회 이벤트 로깅
     */
    @Async
    fun logKeyBundleRetrieval(
        requesterId: UUID,
        targetUserId: UUID,
        success: Boolean,
        executionTimeMs: Long? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val logMetadata = mutableMapOf<String, Any>(
                "target_user_id" to targetUserId.toString()
            )
            metadata?.let { logMetadata.putAll(it) }

            val log = SecurityAuditLog(
                userId = requesterId,
                eventType = AuditEventType.KEY_BUNDLE_RETRIEVAL,
                success = success,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs,
                metadata = logMetadata
            )
            securityAuditLogRepository.save(log)
            logger.debug("키 번들 조회 이벤트 로깅: requesterId={}, targetUserId={}, success={}", requesterId, targetUserId, success)
        } catch (e: Exception) {
            logger.error("키 번들 조회 이벤트 로깅 실패", e)
        }
    }

    /**
     * 지문 검증 이벤트 로깅
     */
    @Async
    fun logFingerprintVerification(
        userId: UUID,
        remoteUserId: UUID,
        verified: Boolean,
        executionTimeMs: Long? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val logMetadata = mutableMapOf<String, Any>(
                "remote_user_id" to remoteUserId.toString(),
                "verified" to verified
            )
            metadata?.let { logMetadata.putAll(it) }

            val log = SecurityAuditLog(
                userId = userId,
                eventType = AuditEventType.FINGERPRINT_VERIFICATION,
                success = verified,
                executionTimeMs = executionTimeMs,
                metadata = logMetadata
            )
            securityAuditLogRepository.save(log)
            logger.debug("지문 검증 이벤트 로깅: userId={}, remoteUserId={}, verified={}", userId, remoteUserId, verified)
        } catch (e: Exception) {
            logger.error("지문 검증 이벤트 로깅 실패", e)
        }
    }

    /**
     * 의심스러운 활동 감지 및 로깅
     */
    @Async
    fun checkSuspiciousActivity(userId: UUID) {
        try {
            val since = LocalDateTime.now().minusMinutes(SUSPICIOUS_TIME_WINDOW_MINUTES)
            val failureCount = securityAuditLogRepository.countByUserIdAndSuccessFalseAndCreatedAtAfter(userId, since)

            if (failureCount >= SUSPICIOUS_FAILURE_THRESHOLD) {
                logger.warn("의심스러운 활동 감지: userId={}, 최근 {}분 내 실패 {}회", userId, SUSPICIOUS_TIME_WINDOW_MINUTES, failureCount)
                
                val log = SecurityAuditLog(
                    userId = userId,
                    eventType = AuditEventType.SUSPICIOUS_ACTIVITY,
                    eventSubtype = "MULTIPLE_FAILURES",
                    success = false,
                    errorMessage = "최근 ${SUSPICIOUS_TIME_WINDOW_MINUTES}분 내 ${failureCount}회 실패",
                    metadata = mapOf(
                        "failure_count" to failureCount,
                        "time_window_minutes" to SUSPICIOUS_TIME_WINDOW_MINUTES
                    )
                )
                securityAuditLogRepository.save(log)
            }
        } catch (e: Exception) {
            logger.error("의심스러운 활동 체크 실패: userId={}", userId, e)
        }
    }

    /**
     * 로그 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    fun getAuditLogs(
        userId: UUID? = null,
        eventType: AuditEventType? = null,
        success: Boolean? = null,
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null,
        page: Int = 0,
        size: Int = 50
    ): Page<SecurityAuditLog> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        return when {
            userId != null && eventType != null -> {
                securityAuditLogRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(userId, eventType, pageable)
            }
            userId != null && success != null && !success -> {
                securityAuditLogRepository.findByUserIdAndSuccessFalseOrderByCreatedAtDesc(userId, pageable)
            }
            userId != null -> {
                securityAuditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            }
            eventType != null -> {
                securityAuditLogRepository.findByEventTypeOrderByCreatedAtDesc(eventType, pageable)
            }
            success != null && !success -> {
                securityAuditLogRepository.findBySuccessFalseOrderByCreatedAtDesc(pageable)
            }
            startTime != null && endTime != null -> {
                securityAuditLogRepository.findByCreatedAtBetween(startTime, endTime, pageable)
            }
            else -> {
                // 기본: 최근 실패 이벤트
                securityAuditLogRepository.findBySuccessFalseOrderByCreatedAtDesc(pageable)
            }
        }
    }

    /**
     * 암호화 작업 통계 조회
     */
    @Transactional(readOnly = true)
    fun getEncryptionMetrics(
        startTime: LocalDateTime = LocalDateTime.now().minusDays(7),
        endTime: LocalDateTime = LocalDateTime.now()
    ): EncryptionMetrics {
        val allLogs = securityAuditLogRepository.findByCreatedAtBetween(startTime, endTime)

        val encryptionLogs = allLogs.filter { 
            it.eventType == AuditEventType.MESSAGE_ENCRYPTION || 
            it.eventType == AuditEventType.ENCRYPTION_FAILURE
        }
        val decryptionLogs = allLogs.filter { 
            it.eventType == AuditEventType.MESSAGE_DECRYPTION || 
            it.eventType == AuditEventType.DECRYPTION_FAILURE
        }
        val sessionLogs = allLogs.filter { 
            it.eventType == AuditEventType.SESSION_INITIALIZATION || 
            it.eventType == AuditEventType.SESSION_FAILURE
        }

        val encryptionSuccess = encryptionLogs.count { it.success }
        val encryptionTotal = encryptionLogs.size
        val encryptionSuccessRate = if (encryptionTotal > 0) {
            (encryptionSuccess.toDouble() / encryptionTotal * 100)
        } else 0.0

        val decryptionSuccess = decryptionLogs.count { it.success }
        val decryptionTotal = decryptionLogs.size
        val decryptionSuccessRate = if (decryptionTotal > 0) {
            (decryptionSuccess.toDouble() / decryptionTotal * 100)
        } else 0.0

        val sessionSuccess = sessionLogs.count { it.success }
        val sessionTotal = sessionLogs.size
        val sessionSuccessRate = if (sessionTotal > 0) {
            (sessionSuccess.toDouble() / sessionTotal * 100)
        } else 0.0

        // 평균 실행 시간 계산
        val encryptionAvgLatency = encryptionLogs
            .filter { it.executionTimeMs != null && it.success }
            .mapNotNull { it.executionTimeMs }
            .average()
            .takeIf { !it.isNaN() }?.toLong()

        val decryptionAvgLatency = decryptionLogs
            .filter { it.executionTimeMs != null && it.success }
            .mapNotNull { it.executionTimeMs }
            .average()
            .takeIf { !it.isNaN() }?.toLong()

        val suspiciousActivities = allLogs.count { it.eventType == AuditEventType.SUSPICIOUS_ACTIVITY }

        return EncryptionMetrics(
            period = "${startTime} ~ ${endTime}",
            encryptionTotal = encryptionTotal,
            encryptionSuccess = encryptionSuccess,
            encryptionSuccessRate = encryptionSuccessRate,
            encryptionAvgLatencyMs = encryptionAvgLatency,
            decryptionTotal = decryptionTotal,
            decryptionSuccess = decryptionSuccess,
            decryptionSuccessRate = decryptionSuccessRate,
            decryptionAvgLatencyMs = decryptionAvgLatency,
            sessionTotal = sessionTotal,
            sessionSuccess = sessionSuccess,
            sessionSuccessRate = sessionSuccessRate,
            suspiciousActivities = suspiciousActivities
        )
    }
}

/**
 * 암호화 작업 메트릭 데이터 클래스
 */
data class EncryptionMetrics(
    val period: String,
    val encryptionTotal: Int,
    val encryptionSuccess: Int,
    val encryptionSuccessRate: Double,
    val encryptionAvgLatencyMs: Long?,
    val decryptionTotal: Int,
    val decryptionSuccess: Int,
    val decryptionSuccessRate: Double,
    val decryptionAvgLatencyMs: Long?,
    val sessionTotal: Int,
    val sessionSuccess: Int,
    val sessionSuccessRate: Double,
    val suspiciousActivities: Int
)

