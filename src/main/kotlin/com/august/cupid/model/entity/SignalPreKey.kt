package com.august.cupid.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Pre-Key Entity (PostgreSQL)
 * Stores one-time pre-keys for X3DH key agreement
 *
 * SECURITY CRITICAL:
 * - Pre-keys must be single-use only
 * - Pre-keys must be rotated regularly
 * - Expired pre-keys must be deleted
 */
@Entity
@Table(
    name = "signal_pre_keys",
    indexes = [
        Index(name = "idx_signal_pre_keys_user_id", columnList = "user_id"),
        Index(name = "idx_signal_pre_keys_key_id", columnList = "user_id,pre_key_id", unique = true),
        Index(name = "idx_signal_pre_keys_used", columnList = "is_used"),
        Index(name = "idx_signal_pre_keys_created_at", columnList = "created_at")
    ]
)
data class SignalPreKey(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    /**
     * User who owns this pre-key
     */
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /**
     * Pre-key ID (must be unique per user)
     */
    @Column(name = "pre_key_id", nullable = false)
    val preKeyId: Int,

    /**
     * Pre-key public key (Base64 encoded)
     */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    val publicKey: String,

    /**
     * Pre-key private key (Base64 encoded, ENCRYPTED)
     * SECURITY: This is encrypted with a master key before storage
     */
    @Column(name = "private_key_encrypted", nullable = false, columnDefinition = "TEXT")
    val privateKeyEncrypted: String,

    /**
     * Whether this pre-key has been used
     * SECURITY: One-time pre-keys must only be used once
     */
    @Column(name = "is_used", nullable = false)
    var isUsed: Boolean = false,

    /**
     * When this pre-key was used (if used)
     */
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,

    /**
     * When this pre-key was created
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * When this pre-key expires
     * SECURITY: Pre-keys should expire after 30-90 days
     */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusDays(90)
)
