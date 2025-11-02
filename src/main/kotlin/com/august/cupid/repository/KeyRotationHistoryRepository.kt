package com.august.cupid.repository

import com.august.cupid.model.entity.KeyRotationHistory
import com.august.cupid.model.entity.RotationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 키 회전 이력 Repository (PostgreSQL)
 * 키 회전 작업 이력 조회 및 관리
 */
@Repository
interface KeyRotationHistoryRepository : JpaRepository<KeyRotationHistory, UUID> {

    /**
     * 사용자 ID로 키 회전 이력 조회 (최신순)
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<KeyRotationHistory>

    /**
     * 사용자 ID와 회전 타입으로 이력 조회
     */
    fun findByUserIdAndRotationTypeOrderByCreatedAtDesc(
        userId: UUID,
        rotationType: RotationType
    ): List<KeyRotationHistory>

    /**
     * 특정 기간 동안의 회전 이력 조회
     */
    @Query("SELECT h FROM KeyRotationHistory h WHERE h.createdAt BETWEEN :startTime AND :endTime ORDER BY h.createdAt DESC")
    fun findByCreatedAtBetween(
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<KeyRotationHistory>

    /**
     * 성공한 회전 작업만 조회
     */
    @Query("SELECT h FROM KeyRotationHistory h WHERE h.success = true AND h.createdAt >= :since ORDER BY h.createdAt DESC")
    fun findSuccessfulRotationsSince(@Param("since") since: LocalDateTime): List<KeyRotationHistory>

    /**
     * 실패한 회전 작업만 조회
     */
    @Query("SELECT h FROM KeyRotationHistory h WHERE h.success = false AND h.createdAt >= :since ORDER BY h.createdAt DESC")
    fun findFailedRotationsSince(@Param("since") since: LocalDateTime): List<KeyRotationHistory>

    /**
     * 특정 사용자의 최근 회전 이력 조회 (제한)
     */
    @Query("SELECT h FROM KeyRotationHistory h WHERE h.userId = :userId ORDER BY h.createdAt DESC")
    fun findRecentRotationsByUserId(@Param("userId") userId: UUID, limit: Int): List<KeyRotationHistory>

    /**
     * 회전 타입별 통계 조회
     */
    @Query("SELECT h.rotationType, COUNT(h) FROM KeyRotationHistory h WHERE h.createdAt >= :since GROUP BY h.rotationType")
    fun countByRotationTypeSince(@Param("since") since: LocalDateTime): List<Array<Any>>
}

