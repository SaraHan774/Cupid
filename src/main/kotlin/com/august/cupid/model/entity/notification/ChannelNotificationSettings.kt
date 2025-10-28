package com.august.cupid.model.entity.notification

import com.august.cupid.model.entity.Channel
import com.august.cupid.model.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * 채널별 알림 설정 엔티티 (PostgreSQL)
 * 채널별 세부 알림 설정 및 음소거 관리
 */
@Entity
@Table(
    name = "channel_notification_settings",
    indexes = [
        Index(name = "idx_channel_notification_settings_channel", columnList = "channel_id"),
        Index(name = "idx_channel_notification_settings_user", columnList = "user_id"),
        Index(name = "idx_channel_notification_settings_muted", columnList = "muted_until")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "unique_channel_user_notification",
            columnNames = ["channel_id", "user_id"]
        )
    ]
)
data class ChannelNotificationSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    val channel: Channel,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,

    @Column(name = "sound_enabled", nullable = false)
    val soundEnabled: Boolean = true,

    @Column(name = "sound_name", nullable = false, length = 100)
    val soundName: String = "message.mp3",

    @Column(name = "vibration_enabled", nullable = false)
    val vibrationEnabled: Boolean = true,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "vibration_pattern", nullable = false, columnDefinition = "integer[]")
    val vibrationPattern: List<Int> = listOf(0, 250, 250, 250), // 밀리초 배열

    // 일시적 음소거
    @Column(name = "muted_until")
    val mutedUntil: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
