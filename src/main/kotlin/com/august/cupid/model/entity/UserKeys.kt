package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Identity Key Pair Entity (PostgreSQL)
 * Stores the user's identity key pair for E2E encryption
 *
 * SECURITY CRITICAL:
 * - Identity key pair is the root of trust for Signal Protocol
 * - Private key MUST be encrypted before storage
 * - Private key MUST NEVER be exposed in logs or API responses
 * - Identity key should remain stable (not rotated frequently)
 */
@Entity
@Table(
    name = "user_keys",
    indexes = [
        Index(name = "idx_user_keys_user_id", columnList = "user_id", unique = true),
        Index(name = "idx_user_keys_expires_at", columnList = "expires_at")
    ]
)
data class UserKeys(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /**
     * Identity public key (Base64 encoded Curve25519 public key)
     * This is shared with other users for key exchange
     */
    @Column(name = "identity_public_key", nullable = false, columnDefinition = "TEXT")
    val identityPublicKey: String,

    /**
     * Identity private key (Base64 encoded, ENCRYPTED)
     * SECURITY: This is encrypted with a master key derived from user credentials
     * NEVER return this in API responses
     */
    @Column(name = "identity_private_key_encrypted", nullable = false, columnDefinition = "TEXT")
    val identityPrivateKeyEncrypted: String,

    /**
     * Device ID for multi-device support
     * Default is 1 for single device
     */
    @Column(name = "device_id", nullable = false)
    val deviceId: Int = 1,

    /**
     * Registration ID (random 14-bit value)
     * Used in X3DH protocol
     */
    @Column(name = "registration_id", nullable = false)
    val registrationId: Int,

    /**
     * Signed Pre-Key (Base64 encoded, serialized SignedPreKeyRecord)
     * Used for X3DH key exchange
     */
    @Column(name = "signed_pre_key", nullable = false, columnDefinition = "TEXT")
    val signedPreKey: String,

    /**
     * Signature over the signed pre-key (Base64 encoded)
     * Verifies that the signed pre-key was created by the identity key
     */
    @Column(name = "pre_key_signature", nullable = false, columnDefinition = "TEXT")
    val preKeySignature: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Expiration time for the identity key (optional)
     * NULL means no expiration (recommended for identity keys)
     */
    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null
)
