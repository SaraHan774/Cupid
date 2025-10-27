package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * 채널 멤버 엔티티 (PostgreSQL)
 * 채널 참여자 정보와 읽음 상태 관리
 */
@Entity
@Table(
    name = "channel_members",
    indexes = [
        Index(name = "idx_channel_members_channel", columnList = "channel_id"),
        Index(name = "idx_channel_members_user", columnList = "user_id"),
        Index(name = "idx_channel_members_active", columnList = "is_active"),
        Index(name = "idx_channel_members_last_read", columnList = "last_read_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "unique_active_membership",
            columnNames = ["channel_id", "user_id", "is_active"]
        )
    ]
)
data class ChannelMembers(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    val channel: Channel,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: ChannelRole = ChannelRole.MEMBER,

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "left_at")
    val leftAt: LocalDateTime? = null,

    @Column(name = "last_read_at")
    var lastReadAt: LocalDateTime? = null,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true
)

/**
 * 채널 역할 열거형
 */
enum class ChannelRole {
    ADMIN,   // 관리자
    MEMBER   // 일반 멤버
}
