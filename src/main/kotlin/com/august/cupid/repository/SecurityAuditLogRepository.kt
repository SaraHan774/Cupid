package com.august.cupid.repository

import com.august.cupid.model.entity.AuditEventType
import com.august.cupid.model.entity.SecurityAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 보안 감사 로그 Repository (MongoDB)
 */
@Repository
interface SecurityAuditLogRepository : MongoRepository<SecurityAuditLog, UUID> {

    /**
     * 사용자 ID로 로그 조회 (최신순)
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<SecurityAuditLog>

    /**
     * 이벤트 타입으로 로그 조회
     */
    fun findByEventTypeOrderByCreatedAtDesc(eventType: AuditEventType, pageable: Pageable): Page<SecurityAuditLog>

    /**
     * 사용자 ID와 이벤트 타입으로 로그 조회
     */
    fun findByUserIdAndEventTypeOrderByCreatedAtDesc(
        userId: UUID,
        eventType: AuditEventType,
        pageable: Pageable
    ): Page<SecurityAuditLog>

    /**
     * 실패한 이벤트만 조회
     */
    fun findBySuccessFalseOrderByCreatedAtDesc(pageable: Pageable): Page<SecurityAuditLog>

    /**
     * 특정 사용자의 실패한 이벤트 조회
     */
    fun findByUserIdAndSuccessFalseOrderByCreatedAtDesc(
        userId: UUID,
        pageable: Pageable
    ): Page<SecurityAuditLog>

    /**
     * 특정 기간 동안의 로그 조회
     */
    @Query("{'created_at': {'\$gte': ?0, '\$lte': ?1}}")
    fun findByCreatedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageable: Pageable
    ): Page<SecurityAuditLog>

    /**
     * 특정 사용자의 기간별 로그 조회
     */
    @Query("{'user_id': ?0, 'created_at': {'\$gte': ?1, '\$lte': ?2}}")
    fun findByUserIdAndCreatedAtBetween(
        userId: UUID,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageable: Pageable
    ): Page<SecurityAuditLog>

    /**
     * 최근 N일간의 실패 이벤트 개수 조회 (의심스러운 활동 감지용)
     */
    @Query("{'user_id': ?0, 'success': false, 'created_at': {'\$gte': ?1}}")
    fun countByUserIdAndSuccessFalseAndCreatedAtAfter(userId: UUID, since: LocalDateTime): Long

    /**
     * 특정 기간 동안의 이벤트 타입별 통계
     */
    @Query("{'event_type': ?0, 'created_at': {'\$gte': ?1, '\$lte': ?2}}")
    fun findByEventTypeAndCreatedAtBetween(
        eventType: AuditEventType,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<SecurityAuditLog>

    /**
     * 성공률 계산을 위한 통계 조회
     */
    @Query("{'created_at': {'\$gte': ?0, '\$lte': ?1}}")
    fun findByCreatedAtBetween(startTime: LocalDateTime, endTime: LocalDateTime): List<SecurityAuditLog>

    /**
     * 특정 이벤트 타입의 평균 실행 시간 조회
     */
    @Query("{'event_type': ?0, 'success': true, 'execution_time_ms': {'\$exists': true}, 'created_at': {'\$gte': ?1}}")
    fun findByEventTypeAndSuccessTrueAndCreatedAtAfter(
        eventType: AuditEventType,
        since: LocalDateTime
    ): List<SecurityAuditLog>
}

