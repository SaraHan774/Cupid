package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 차단 엔티티 (PostgreSQL)
 * 사용자 간 차단 관계 관리
 */
@Entity
@Table(
    name = "user_blocks",
    indexes = [
        Index(name = "idx_user_blocks_blocker", columnList = "blocker_id"),
        Index(name = "idx_user_blocks_blocked", columnList = "blocked_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "unique_block",
            columnNames = ["blocker_id", "blocked_id"]
        )
    ]
)
data class UserBlocks(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    val blocker: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    val blocked: User,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 서로 다른 사용자인지 검증
     */
    init {
        require(blocker.id != blocked.id) { "자기 자신을 차단할 수 없습니다" }
    }
}
