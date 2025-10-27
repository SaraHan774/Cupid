package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * 매칭 엔티티 (PostgreSQL)
 * 소개팅 앱의 매칭 정보 저장
 */
@Entity
@Table(
    name = "matches",
    indexes = [
        Index(name = "idx_matches_user1", columnList = "user1_id"),
        Index(name = "idx_matches_user2", columnList = "user2_id"),
        Index(name = "idx_matches_status", columnList = "status"),
        Index(name = "idx_matches_expires_at", columnList = "expires_at")
    ]
)
data class Match(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    val user1: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    val user2: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: MatchStatus = MatchStatus.ACTIVE,

    @CreationTimestamp
    @Column(name = "matched_at", nullable = false)
    val matchedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null
) {
    /**
     * 서로 다른 사용자인지 검증
     */
    init {
        require(user1.id != user2.id) { "매칭은 서로 다른 사용자 간에만 가능합니다" }
    }
}

/**
 * 매칭 상태 열거형
 */
enum class MatchStatus {
    ACTIVE,    // 활성
    EXPIRED,   // 만료
    CANCELLED  // 취소
}
