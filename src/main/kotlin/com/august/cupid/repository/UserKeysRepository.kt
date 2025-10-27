package com.august.cupid.repository

import com.august.cupid.model.entity.UserKeys
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 키 Repository (PostgreSQL)
 * Signal Protocol 키 관리
 */
@Repository
interface UserKeysRepository : JpaRepository<UserKeys, UUID> {

    /**
     * 사용자 ID로 키 조회
     */
    fun findByUserId(userId: UUID): UserKeys?

    /**
     * 사용자 ID로 활성 키 조회 (만료되지 않은 키)
     */
    @Query("SELECT uk FROM UserKeys uk WHERE uk.user.id = :userId AND (uk.expiresAt IS NULL OR uk.expiresAt > :now)")
    fun findActiveKeysByUserId(@Param("userId") userId: UUID, @Param("now") now: LocalDateTime): UserKeys?

    /**
     * 만료된 키들 조회
     */
    @Query("SELECT uk FROM UserKeys uk WHERE uk.expiresAt IS NOT NULL AND uk.expiresAt <= :now")
    fun findExpiredKeys(@Param("now") now: LocalDateTime): List<UserKeys>

    /**
     * 사용자 키 존재 여부 확인
     */
    fun existsByUserId(userId: UUID): Boolean

    /**
     * 사용자 키 삭제
     */
    fun deleteByUserId(userId: UUID)

    /**
     * 만료된 키들 삭제
     */
    @Query("DELETE FROM UserKeys uk WHERE uk.expiresAt IS NOT NULL AND uk.expiresAt <= :now")
    fun deleteExpiredKeys(@Param("now") now: LocalDateTime): Int

    /**
     * 특정 사용자의 모든 키 조회
     */
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<UserKeys>

    /**
     * One-Time Pre Key ID로 키 조회
     */
    fun findByOneTimePreKeyId(oneTimePreKeyId: Int): UserKeys?

    /**
     * 사용자별 키 개수 조회
     */
    @Query("SELECT COUNT(uk) FROM UserKeys uk WHERE uk.user.id = :userId")
    fun countKeysByUserId(@Param("userId") userId: UUID): Long
}
