package com.august.cupid.repository

import com.august.cupid.model.entity.SignalIdentity
import com.august.cupid.model.entity.TrustLevel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Identity Repository
 * Manages identity key trust verification
 */
@Repository
interface SignalIdentityRepository : JpaRepository<SignalIdentity, UUID> {

    /**
     * Find identity by user ID and remote address
     */
    fun findByUserIdAndAddressNameAndAddressDeviceId(
        userId: UUID,
        addressName: String,
        addressDeviceId: Int
    ): SignalIdentity?

    /**
     * Find all identities for a user
     */
    fun findByUserId(userId: UUID): List<SignalIdentity>

    /**
     * Find identities with specific trust level
     */
    fun findByUserIdAndTrustLevel(userId: UUID, trustLevel: TrustLevel): List<SignalIdentity>

    /**
     * Update trust level
     */
    @Modifying
    @Query("UPDATE SignalIdentity i SET i.trustLevel = :trustLevel, i.verifiedAt = :verifiedAt WHERE i.id = :id")
    fun updateTrustLevel(
        @Param("id") id: UUID,
        @Param("trustLevel") trustLevel: TrustLevel,
        @Param("verifiedAt") verifiedAt: LocalDateTime?
    )

    /**
     * Delete identity by address
     */
    @Modifying
    @Query("DELETE FROM SignalIdentity i WHERE i.userId = :userId AND i.addressName = :addressName AND i.addressDeviceId = :addressDeviceId")
    fun deleteByAddress(
        @Param("userId") userId: UUID,
        @Param("addressName") addressName: String,
        @Param("addressDeviceId") addressDeviceId: Int
    )

    /**
     * Check if identity exists and matches
     */
    fun existsByUserIdAndAddressNameAndAddressDeviceIdAndIdentityKey(
        userId: UUID,
        addressName: String,
        addressDeviceId: Int,
        identityKey: String
    ): Boolean

    /**
     * Find identities that have changed (for security alerts)
     */
    fun findByUserIdAndTrustLevelAndCreatedAtAfter(
        userId: UUID,
        trustLevel: TrustLevel,
        after: LocalDateTime
    ): List<SignalIdentity>

    /**
     * Delete all identities for a user
     */
    @Modifying
    @Query("DELETE FROM SignalIdentity i WHERE i.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)
}
