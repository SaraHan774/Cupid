package com.august.cupid.repository

import com.august.cupid.model.entity.KeyBackup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 키 백업 Repository (PostgreSQL)
 * Signal Protocol 키 백업 관리
 */
@Repository
interface KeyBackupRepository : JpaRepository<KeyBackup, UUID> {

    /**
     * 사용자 ID로 모든 백업 조회 (최신순)
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<KeyBackup>

    /**
     * 사용자 ID로 활성 백업 조회 (만료되지 않고 사용되지 않은 백업)
     */
    @Query("""
        SELECT kb FROM KeyBackup kb 
        WHERE kb.user.id = :userId 
          AND kb.isUsed = false 
          AND (kb.expiresAt IS NULL OR kb.expiresAt > :now)
        ORDER BY kb.createdAt DESC
    """)
    fun findActiveBackupsByUserId(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime
    ): List<KeyBackup>

    /**
     * 백업 ID와 사용자 ID로 조회
     */
    @Query("SELECT kb FROM KeyBackup kb WHERE kb.id = :backupId AND kb.user.id = :userId")
    fun findByBackupIdAndUserId(
        @Param("backupId") backupId: UUID,
        @Param("userId") userId: UUID
    ): KeyBackup?

    /**
     * 만료된 백업 조회
     */
    @Query("SELECT kb FROM KeyBackup kb WHERE kb.expiresAt IS NOT NULL AND kb.expiresAt <= :now")
    fun findExpiredBackups(@Param("now") now: LocalDateTime): List<KeyBackup>

    /**
     * 만료된 백업 삭제
     */
    @Query("DELETE FROM KeyBackup kb WHERE kb.expiresAt IS NOT NULL AND kb.expiresAt <= :now")
    fun deleteExpiredBackups(@Param("now") now: LocalDateTime): Int

    /**
     * 사용자의 백업 개수 조회
     */
    @Query("SELECT COUNT(kb) FROM KeyBackup kb WHERE kb.user.id = :userId")
    fun countByUserId(@Param("userId") userId: UUID): Long

    /**
     * 사용자의 활성 백업 개수 조회
     */
    @Query("""
        SELECT COUNT(kb) FROM KeyBackup kb 
        WHERE kb.user.id = :userId 
          AND kb.isUsed = false 
          AND (kb.expiresAt IS NULL OR kb.expiresAt > :now)
    """)
    fun countActiveBackupsByUserId(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime
    ): Long

    /**
     * 백업 사용 표시 (복구 시 호출)
     * data class의 불변성을 유지하기 위해 네이티브 UPDATE 쿼리 사용
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE key_backups 
            SET is_used = true, used_at = :usedAt
            WHERE id = :backupId AND user_id = :userId
        """,
        nativeQuery = true
    )
    fun markBackupAsUsed(
        @Param("backupId") backupId: UUID,
        @Param("userId") userId: UUID,
        @Param("usedAt") usedAt: LocalDateTime
    ): Int
}

