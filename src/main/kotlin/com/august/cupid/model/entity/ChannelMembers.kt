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
            name = "uq_channel_members_channel_user",
            columnNames = ["channel_id", "user_id"]
        )
    ]
)
class ChannelMembers(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    val id: UUID? = null,  // null이면 persist, 값이 있으면 merge

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    val channel: Channel,

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: ChannelRole = ChannelRole.MEMBER,

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "left_at")
    val leftAt: LocalDateTime? = null,

    @Column(name = "last_read_at")
    var lastReadAt: LocalDateTime? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Version
    @Column(name = "version")
    val version: Long? = null  // 낙관적 락을 위한 버전 필드
)

/**
 * 채널 역할 열거형
 */
enum class ChannelRole {
    ADMIN,   // 관리자
    MEMBER   // 일반 멤버
}
