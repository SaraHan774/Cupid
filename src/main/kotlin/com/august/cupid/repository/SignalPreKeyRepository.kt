package com.august.cupid.repository

import com.august.cupid.model.entity.SignalPreKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Pre-Key Repository
 * Manages one-time pre-key lifecycle
 */
@Repository
interface SignalPreKeyRepository : JpaRepository<SignalPreKey, UUID> {

    /**
     * Find pre-key by user ID and pre-key ID
     */
    fun findByUserIdAndPreKeyId(userId: UUID, preKeyId: Int): SignalPreKey?

    /**
     * Find all pre-keys for a user
     */
    fun findByUserId(userId: UUID): List<SignalPreKey>

    /**
     * Find unused pre-keys for a user
     */
    fun findByUserIdAndIsUsedFalse(userId: UUID): List<SignalPreKey>

    /**
     * Find unused and not expired pre-keys
     */
    @Query("SELECT p FROM SignalPreKey p WHERE p.userId = :userId AND p.isUsed = false AND p.expiresAt > :now ORDER BY p.createdAt ASC")
    fun findAvailablePreKeys(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime
    ): List<SignalPreKey>

    /**
     * Get one available pre-key (for key bundle)
     * Alias for findAvailablePreKey (single result)
     */
    @Query("SELECT p FROM SignalPreKey p WHERE p.userId = :userId AND p.isUsed = false AND p.expiresAt > :now ORDER BY p.createdAt ASC LIMIT 1")
    fun findOneAvailablePreKey(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime
    ): SignalPreKey?

    /**
     * Alias method for compatibility
     */
    fun findAvailablePreKey(userId: UUID, now: LocalDateTime): SignalPreKey? {
        return findOneAvailablePreKey(userId, now)
    }

    /**
     * Mark pre-key as used
     */
    @Modifying
    @Query("UPDATE SignalPreKey p SET p.isUsed = true, p.usedAt = :usedAt WHERE p.id = :id AND p.isUsed = false")
    fun markAsUsed(@Param("id") id: UUID, @Param("usedAt") usedAt: LocalDateTime): Int

    /**
     * Delete expired pre-keys
     */
    @Modifying
    @Query("DELETE FROM SignalPreKey p WHERE p.expiresAt < :now")
    fun deleteExpiredPreKeys(@Param("now") now: LocalDateTime): Int

    /**
     * Delete used pre-keys older than threshold
     */
    @Modifying
    @Query("DELETE FROM SignalPreKey p WHERE p.isUsed = true AND p.usedAt < :threshold")
    fun deleteOldUsedPreKeys(@Param("threshold") threshold: LocalDateTime): Int

    /**
     * Count available pre-keys for a user
     */
    @Query("SELECT COUNT(p) FROM SignalPreKey p WHERE p.userId = :userId AND p.isUsed = false AND p.expiresAt > :now")
    fun countAvailablePreKeys(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime
    ): Long

    /**
     * Find highest pre-key ID for a user (for generating next ID)
     */
    @Query("SELECT COALESCE(MAX(p.preKeyId), 0) FROM SignalPreKey p WHERE p.userId = :userId")
    fun findMaxPreKeyId(@Param("userId") userId: UUID): Int

    /**
     * Delete all pre-keys for a user
     */
    @Modifying
    @Query("DELETE FROM SignalPreKey p WHERE p.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)
}
