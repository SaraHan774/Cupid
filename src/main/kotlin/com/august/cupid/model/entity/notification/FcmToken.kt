package com.august.cupid.model.entity.notification

import com.august.cupid.model.entity.User
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
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_fcm_token_token",
            columnNames = ["token"]
        )
    ]
)
class FcmToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    val id: UUID? = null,  // null이면 persist, 값이 있으면 merge

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "token", nullable = false, unique = true, length = 500)
    var token: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    var deviceType: DeviceType,

    @Column(name = "device_name", length = 100)
    var deviceName: String? = null,

    @Column(name = "app_version", length = 50)
    var appVersion: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Version
    @Column(name = "version")
    val version: Long? = null  // 낙관적 락을 위한 버전 필드
)

/**
 * 디바이스 타입 열거형
 */
enum class DeviceType {
    IOS,     // iOS
    ANDROID, // Android
    WEB      // Web
}
