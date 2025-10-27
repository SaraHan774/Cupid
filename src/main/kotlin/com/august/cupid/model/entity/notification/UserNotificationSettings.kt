package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

/**
 * 사용자 알림 설정 엔티티 (PostgreSQL)
 * 전역 알림 설정 및 방해금지 모드 관리
 */
@Entity
@Table(
    name = "user_notification_settings",
    indexes = [
        Index(name = "idx_user_notification_settings_user", columnList = "user_id")
    ]
)
data class UserNotificationSettings(
    @Id
    @JoinColumn(name = "user_id", nullable = false)
    @OneToOne(fetch = FetchType.LAZY)
    val user: User,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,

    @Column(name = "sound_enabled", nullable = false)
    val soundEnabled: Boolean = true,

    @Column(name = "vibration_enabled", nullable = false)
    val vibrationEnabled: Boolean = true,

    @Column(name = "show_preview", nullable = false)
    val showPreview: Boolean = true,

    // 방해금지 모드
    @Column(name = "dnd_enabled", nullable = false)
    val dndEnabled: Boolean = false,

    @Column(name = "dnd_start_time", nullable = false)
    val dndStartTime: LocalTime = LocalTime.of(22, 0),

    @Column(name = "dnd_end_time", nullable = false)
    val dndEndTime: LocalTime = LocalTime.of(8, 0),

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dnd_days", nullable = false, columnDefinition = "integer[]")
    val dndDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=월요일, 7=일요일

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
