package com.august.cupid.repository

import com.august.cupid.model.entity.SignalSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Session Repository
 * Manages session state persistence
 */
@Repository
interface SignalSessionRepository : JpaRepository<SignalSession, UUID> {

    /**
     * Find session by user ID and remote address
     */
    fun findByUserIdAndAddressNameAndAddressDeviceId(
        userId: UUID,
        addressName: String,
        addressDeviceId: Int
    ): SignalSession?

    /**
     * Find all sessions for a user
     */
    fun findByUserId(userId: UUID): List<SignalSession>

    /**
     * Find all sessions with a specific remote address
     */
    fun findByUserIdAndAddressName(userId: UUID, addressName: String): List<SignalSession>

    /**
     * Delete session by address
     */
    @Modifying
    @Query("DELETE FROM SignalSession s WHERE s.userId = :userId AND s.addressName = :addressName AND s.addressDeviceId = :addressDeviceId")
    fun deleteByAddress(
        @Param("userId") userId: UUID,
        @Param("addressName") addressName: String,
        @Param("addressDeviceId") addressDeviceId: Int
    )

    /**
     * Delete all sessions for a user
     */
    @Modifying
    @Query("DELETE FROM SignalSession s WHERE s.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)

    /**
     * Update last used timestamp
     */
    @Modifying
    @Query("UPDATE SignalSession s SET s.lastUsedAt = :lastUsedAt WHERE s.id = :id")
    fun updateLastUsedAt(@Param("id") id: UUID, @Param("lastUsedAt") lastUsedAt: LocalDateTime)

    /**
     * Find sessions that haven't been used recently (for cleanup)
     */
    @Query("SELECT s FROM SignalSession s WHERE s.lastUsedAt < :threshold")
    fun findInactiveSessions(@Param("threshold") threshold: LocalDateTime): List<SignalSession>

    /**
     * Count sessions for a user
     */
    fun countByUserId(userId: UUID): Long

    /**
     * Check if session exists for a user and address
     */
    fun existsByUserIdAndAddressName(userId: UUID, addressName: String): Boolean

    /**
     * Delete sessions by user ID and address name (all devices)
     */
    @Modifying
    @Query("DELETE FROM SignalSession s WHERE s.userId = :userId AND s.addressName = :addressName")
    fun deleteByUserIdAndAddressName(@Param("userId") userId: UUID, @Param("addressName") addressName: String)
}
