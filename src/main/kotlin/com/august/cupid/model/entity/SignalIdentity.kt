package com.august.cupid.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Identity Entity (PostgreSQL)
 * Stores identity key information for trust verification
 *
 * SECURITY: Identity keys are public and used for MITM attack prevention
 */
@Entity
@Table(
    name = "signal_identities",
    indexes = [
        Index(name = "idx_signal_identities_user_id", columnList = "user_id"),
        Index(name = "idx_signal_identities_address", columnList = "address_name,address_device_id", unique = true)
    ]
)
data class SignalIdentity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    /**
     * User who verified this identity
     */
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /**
     * Remote party's address name
     */
    @Column(name = "address_name", nullable = false, length = 500)
    val addressName: String,

    /**
     * Remote party's device ID
     */
    @Column(name = "address_device_id", nullable = false)
    val addressDeviceId: Int,

    /**
     * Remote party's identity key (Base64 encoded public key)
     */
    @Column(name = "identity_key", nullable = false, columnDefinition = "TEXT")
    val identityKey: String,

    /**
     * Trust level for this identity
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trust_level", nullable = false)
    val trustLevel: TrustLevel = TrustLevel.UNTRUSTED,

    /**
     * When this identity was first seen
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * When this identity was verified (if trusted)
     */
    @Column(name = "verified_at")
    val verifiedAt: LocalDateTime? = null
)

/**
 * Trust levels for identity keys
 */
enum class TrustLevel {
    /**
     * Identity key has not been verified
     */
    UNTRUSTED,

    /**
     * Identity key has been explicitly verified by the user
     */
    TRUSTED,

    /**
     * Identity key has changed - potential MITM attack
     * User must verify the new key
     */
    CHANGED
}
