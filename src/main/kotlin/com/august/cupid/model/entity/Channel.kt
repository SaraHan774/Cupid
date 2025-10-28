package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * 채널 엔티티 (PostgreSQL)
 * 1:1 채팅방과 그룹 채팅방을 모두 지원
 */
@Entity
@Table(
    name = "channels",
    indexes = [
        Index(name = "idx_channels_type", columnList = "type"),
        Index(name = "idx_channels_creator", columnList = "creator_id"),
        Index(name = "idx_channels_match", columnList = "match_id"),
        Index(name = "idx_channels_created_at", columnList = "created_at")
    ]
)
data class Channel(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    val id: UUID? = null,  // null이면 persist, 값이 있으면 merge

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: ChannelType,

    @Column(name = "name", length = 255)
    val name: String?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    val creator: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    val match: Match?,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    @Column(name = "version")
    val version: Long? = null,  // 낙관적 락을 위한 버전 필드

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null
)

/**
 * 채널 타입 열거형
 */
enum class ChannelType {
    DIRECT,  // 1:1 채팅
    GROUP    // 그룹 채팅
}
