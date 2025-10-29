package com.august.cupid.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol Session Entity (PostgreSQL)
 * Stores encrypted Signal Protocol session state
 *
 * SECURITY: Session records are encrypted before storage
 */
@Entity
@Table(
    name = "signal_sessions",
    indexes = [
        Index(name = "idx_signal_sessions_user_id", columnList = "user_id"),
        Index(name = "idx_signal_sessions_address", columnList = "address_name,address_device_id", unique = true),
        Index(name = "idx_signal_sessions_created_at", columnList = "created_at"),
        Index(name = "idx_signal_sessions_last_used", columnList = "last_used_at")
    ]
)
data class SignalSession(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    /**
     * User who owns this session
     */
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /**
     * Remote party's address name (usually their user ID)
     */
    @Column(name = "address_name", nullable = false, length = 500)
    val addressName: String,

    /**
     * Remote party's device ID
     */
    @Column(name = "address_device_id", nullable = false)
    val addressDeviceId: Int,

    /**
     * Encrypted session record (Base64 encoded)
     * SECURITY: This contains the Double Ratchet state and must be protected
     */
    @Column(name = "session_record", nullable = false, columnDefinition = "TEXT")
    val sessionRecord: String,

    /**
     * Session creation timestamp
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Last time this session was used for encryption/decryption
     */
    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Session version for tracking protocol upgrades
     */
    @Column(name = "version", nullable = false)
    val version: Int = 1
)
