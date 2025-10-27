package com.august.cupid.repository

import com.august.cupid.model.entity.Report
import com.august.cupid.model.entity.ReportStatus
import com.august.cupid.model.entity.ReportType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 신고 Repository (PostgreSQL)
 * E2E 암호화 절충안을 통한 신고 시스템
 */
@Repository
interface ReportRepository : JpaRepository<Report, UUID> {

    /**
     * 신고자 ID로 신고 목록 조회
     */
    fun findBySubmitterIdOrderByCreatedAtDesc(submitterId: UUID, pageable: Pageable): Page<Report>

    /**
     * 신고 대상 사용자 ID로 신고 목록 조회
     */
    fun findByTargetUserIdOrderByCreatedAtDesc(targetUserId: UUID, pageable: Pageable): Page<Report>

    /**
     * 신고 상태별 조회
     */
    fun findByStatusOrderByCreatedAtDesc(status: ReportStatus, pageable: Pageable): Page<Report>

    /**
     * 신고 유형별 조회
     */
    fun findByReportTypeOrderByCreatedAtDesc(reportType: ReportType, pageable: Pageable): Page<Report>

    /**
     * 신고 대상 메시지 ID로 조회
     */
    fun findByTargetMessageIdOrderByCreatedAtDesc(targetMessageId: UUID, pageable: Pageable): Page<Report>

    /**
     * 처리자 ID로 신고 목록 조회
     */
    fun findByResolverIdOrderByResolvedAtDesc(resolverId: UUID, pageable: Pageable): Page<Report>

    /**
     * 특정 시간 이후의 신고들 조회
     */
    fun findByCreatedAtAfterOrderByCreatedAtDesc(createdAt: LocalDateTime, pageable: Pageable): Page<Report>

    /**
     * 특정 시간 범위의 신고들 조회
     */
    fun findByCreatedAtBetweenOrderByCreatedAtDesc(
        startTime: LocalDateTime, 
        endTime: LocalDateTime, 
        pageable: Pageable
    ): Page<Report>

    /**
     * 신고 상태별 개수 조회
     */
    fun countByStatus(status: ReportStatus): Long

    /**
     * 신고 유형별 개수 조회
     */
    fun countByReportType(reportType: ReportType): Long

    /**
     * 사용자가 제출한 신고 개수 조회
     */
    fun countBySubmitterId(submitterId: UUID): Long

    /**
     * 사용자에 대한 신고 개수 조회
     */
    fun countByTargetUserId(targetUserId: UUID): Long

    /**
     * 처리자가 처리한 신고 개수 조회
     */
    fun countByResolverId(resolverId: UUID): Long

    /**
     * 신고 상태 업데이트
     */
    @Query("UPDATE Report r SET r.status = :status, r.updatedAt = CURRENT_TIMESTAMP WHERE r.id = :reportId")
    fun updateReportStatus(@Param("reportId") reportId: UUID, @Param("status") status: ReportStatus): Int

    /**
     * 신고 처리 (상태 변경 및 처리자 지정)
     */
    @Query("""
        UPDATE Report r 
        SET r.status = :status, 
            r.resolvedAt = :resolvedAt, 
            r.resolver.id = :resolverId,
            r.updatedAt = CURRENT_TIMESTAMP 
        WHERE r.id = :reportId
    """)
    fun resolveReport(
        @Param("reportId") reportId: UUID, 
        @Param("status") status: ReportStatus, 
        @Param("resolvedAt") resolvedAt: LocalDateTime, 
        @Param("resolverId") resolverId: UUID
    ): Int

    /**
     * 대기 중인 신고들 조회
     */
    @Query("""
        SELECT r FROM Report r 
        WHERE r.status = 'PENDING' 
        ORDER BY r.createdAt ASC
    """)
    fun findPendingReports(pageable: Pageable): Page<Report>

    /**
     * 신고 대상 사용자별 신고 통계 조회
     */
    @Query("""
        SELECT r.targetUser.id, COUNT(r), r.reportType 
        FROM Report r 
        WHERE r.targetUser.id IS NOT NULL 
        GROUP BY r.targetUser.id, r.reportType 
        ORDER BY COUNT(r) DESC
    """)
    fun findReportStatisticsByTargetUser(): List<Array<Any>>

    /**
     * 신고 유형별 통계 조회
     */
    @Query("""
        SELECT r.reportType, COUNT(r), r.status 
        FROM Report r 
        GROUP BY r.reportType, r.status 
        ORDER BY COUNT(r) DESC
    """)
    fun findReportStatisticsByType(): List<Array<Any>>

    /**
     * 반복 신고자 조회 (특정 사용자에 대한 신고를 여러 번 한 사용자)
     */
    @Query("""
        SELECT r.submitter.id, COUNT(r) 
        FROM Report r 
        WHERE r.targetUser.id = :targetUserId 
        GROUP BY r.submitter.id 
        HAVING COUNT(r) > 1 
        ORDER BY COUNT(r) DESC
    """)
    fun findRepeatedReporters(@Param("targetUserId") targetUserId: UUID): List<Array<Any>>

    /**
     * 반복 신고 대상 조회 (여러 번 신고당한 사용자)
     */
    @Query("""
        SELECT r.targetUser.id, COUNT(r) 
        FROM Report r 
        WHERE r.targetUser.id IS NOT NULL 
        GROUP BY r.targetUser.id 
        HAVING COUNT(r) >= :minCount 
        ORDER BY COUNT(r) DESC
    """)
    fun findFrequentlyReportedUsers(@Param("minCount") minCount: Long): List<Array<Any>>

    /**
     * 신고 해시값으로 중복 신고 확인
     */
    fun existsByReportedContentHash(reportedContentHash: String): Boolean

    /**
     * 특정 메시지에 대한 신고들 조회
     */
    fun findByTargetMessageIdOrderByCreatedAtDesc(targetMessageId: UUID): List<Report>

    /**
     * 컨텍스트 메시지 ID가 포함된 신고들 조회
     * TODO: PostgreSQL 배열 쿼리 문제로 인해 임시로 비활성화
     */
    // @Query("SELECT r FROM Report r WHERE :messageId MEMBER OF r.contextMessageIds")
    // fun findByContextMessageIdsContaining(@Param("messageId") messageId: UUID): List<Report>

    /**
     * 신고 처리 시간 통계 조회
     */
    @Query("""
        SELECT AVG(TIMESTAMPDIFF(MINUTE, r.createdAt, r.resolvedAt)) 
        FROM Report r 
        WHERE r.resolvedAt IS NOT NULL
    """)
    fun findAverageResolutionTime(): Double?

    /**
     * 월별 신고 통계 조회
     */
    @Query("""
        SELECT YEAR(r.createdAt), MONTH(r.createdAt), COUNT(r) 
        FROM Report r 
        GROUP BY YEAR(r.createdAt), MONTH(r.createdAt) 
        ORDER BY YEAR(r.createdAt) DESC, MONTH(r.createdAt) DESC
    """)
    fun findMonthlyReportStatistics(): List<Array<Any>>
}
