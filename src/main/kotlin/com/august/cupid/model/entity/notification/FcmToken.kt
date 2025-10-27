package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * FCM 토큰 엔티티 (PostgreSQL)
 * 디바이스별 FCM 토큰 관리
 */
@Entity
@Table(
    name = "fcm_tokens",
    indexes = [
        Index(name = "idx_fcm_tokens_user", columnList = "user_id"),
        Index(name = "idx_fcm_tokens_token", columnList = "token"),
        Index(name = "idx_fcm_tokens_active", columnList = "is_active")
    ]
)
data class FcmToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "token", nullable = false, unique = true, length = 500)
    val token: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    val deviceType: DeviceType,

    @Column(name = "device_name", length = 100)
    val deviceName: String? = null,

    @Column(name = "app_version", length = 50)
    val appVersion: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true
)

/**
 * 디바이스 타입 열거형
 */
enum class DeviceType {
    IOS,     // iOS
    ANDROID  // Android
}
