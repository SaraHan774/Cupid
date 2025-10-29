package com.august.cupid.repository

import com.august.cupid.model.entity.SignalSignedPreKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Signed Pre-Key Repository
 * Manages signed pre-key lifecycle and rotation
 */
@Repository
interface SignalSignedPreKeyRepository : JpaRepository<SignalSignedPreKey, UUID> {

    /**
     * Find signed pre-key by user ID and key ID
     */
    fun findByUserIdAndSignedPreKeyId(userId: UUID, signedPreKeyId: Int): SignalSignedPreKey?

    /**
     * Find active signed pre-key for a user
     */
    fun findByUserIdAndIsActiveTrue(userId: UUID): SignalSignedPreKey?

    /**
     * Find all signed pre-keys for a user
     */
    fun findByUserId(userId: UUID): List<SignalSignedPreKey>

    /**
     * Deactivate all signed pre-keys for a user (before creating new one)
     */
    @Modifying
    @Query("UPDATE SignalSignedPreKey s SET s.isActive = false WHERE s.userId = :userId AND s.isActive = true")
    fun deactivateAll(@Param("userId") userId: UUID)

    /**
     * Delete expired signed pre-keys (keep some history for overlap)
     */
    @Modifying
    @Query("DELETE FROM SignalSignedPreKey s WHERE s.expiresAt < :threshold AND s.isActive = false")
    fun deleteExpiredInactive(@Param("threshold") threshold: LocalDateTime): Int

    /**
     * Find signed pre-keys expiring soon (for rotation)
     */
    @Query("SELECT s FROM SignalSignedPreKey s WHERE s.isActive = true AND s.expiresAt < :threshold")
    fun findExpiringSoon(@Param("threshold") threshold: LocalDateTime): List<SignalSignedPreKey>

    /**
     * Find highest signed pre-key ID for a user (for generating next ID)
     */
    @Query("SELECT COALESCE(MAX(s.signedPreKeyId), 0) FROM SignalSignedPreKey s WHERE s.userId = :userId")
    fun findMaxSignedPreKeyId(@Param("userId") userId: UUID): Int

    /**
     * Check if user has active signed pre-key
     */
    fun existsByUserIdAndIsActiveTrue(userId: UUID): Boolean

    /**
     * Delete all signed pre-keys for a user
     */
    @Modifying
    @Query("DELETE FROM SignalSignedPreKey s WHERE s.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)
}
