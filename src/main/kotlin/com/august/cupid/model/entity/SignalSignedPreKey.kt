package com.august.cupid.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Signed Pre-Key Entity (PostgreSQL)
 * Stores the current signed pre-key for X3DH
 *
 * SECURITY CRITICAL:
 * - Signed pre-keys provide forward secrecy
 * - Must be rotated regularly (weekly/monthly)
 * - Signature must be verified before use
 */
@Entity
@Table(
    name = "signal_signed_pre_keys",
    indexes = [
        Index(name = "idx_signal_signed_pre_keys_user_id", columnList = "user_id"),
        Index(name = "idx_signal_signed_pre_keys_key_id", columnList = "user_id,signed_pre_key_id", unique = true),
        Index(name = "idx_signal_signed_pre_keys_is_active", columnList = "is_active"),
        Index(name = "idx_signal_signed_pre_keys_created_at", columnList = "created_at")
    ]
)
data class SignalSignedPreKey(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    /**
     * User who owns this signed pre-key
     */
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /**
     * Signed pre-key ID
     */
    @Column(name = "signed_pre_key_id", nullable = false)
    val signedPreKeyId: Int,

    /**
     * Signed pre-key public key (Base64 encoded)
     */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    val publicKey: String,

    /**
     * Signed pre-key private key (Base64 encoded, ENCRYPTED)
     * SECURITY: This is encrypted with a master key before storage
     */
    @Column(name = "private_key_encrypted", nullable = false, columnDefinition = "TEXT")
    val privateKeyEncrypted: String,

    /**
     * Signature of the public key (Base64 encoded)
     * SECURITY: Signed with the identity key to prevent MITM attacks
     */
    @Column(name = "signature", nullable = false, columnDefinition = "TEXT")
    val signature: String,

    /**
     * Timestamp when the signed pre-key was generated (milliseconds since epoch)
     * Used in the Signal Protocol
     */
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Whether this signed pre-key is currently active
     * Only one signed pre-key should be active at a time
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    /**
     * When this signed pre-key was created
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * When this signed pre-key expires
     * SECURITY: Should be rotated weekly or monthly
     */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusDays(30)
)
