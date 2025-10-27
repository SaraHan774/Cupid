package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * Signal Protocol 키 엔티티 (PostgreSQL)
 * E2E 암호화를 위한 사용자 키 정보 저장
 */
@Entity
@Table(
    name = "user_keys",
    indexes = [
        Index(name = "idx_user_keys_user_id", columnList = "user_id"),
        Index(name = "idx_user_keys_expires_at", columnList = "expires_at")
    ]
)
data class UserKeys(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "identity_key", nullable = false, columnDefinition = "TEXT")
    val identityKey: String,

    @Column(name = "signed_pre_key", nullable = false, columnDefinition = "TEXT")
    val signedPreKey: String,

    @Column(name = "pre_key_signature", nullable = false, columnDefinition = "TEXT")
    val preKeySignature: String,

    @Column(name = "one_time_pre_key_id")
    val oneTimePreKeyId: Int? = null,

    @Column(name = "one_time_pre_key", columnDefinition = "TEXT")
    val oneTimePreKey: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null
)
