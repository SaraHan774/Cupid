package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * 키 회전 이력 엔티티 (PostgreSQL)
 * 키 회전 작업의 감사(Audit)를 위한 이력 저장
 *
 * 목적:
 * - 키 회전 작업 추적
 * - 문제 발생 시 디버깅
 * - 보안 감사(Compliance)
 * - 통계 및 모니터링
 */
@Entity
@Table(
    name = "key_rotation_history",
    indexes = [
        Index(name = "idx_key_rotation_history_user_id", columnList = "user_id"),
        Index(name = "idx_key_rotation_history_created_at", columnList = "created_at"),
        Index(name = "idx_key_rotation_history_rotation_type", columnList = "rotation_type")
    ]
)
data class KeyRotationHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    /**
     * 키를 회전한 사용자 ID
     */
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /**
     * 회전 타입
     * - SIGNED_PRE_KEY: Signed Pre-Key 회전
     * - ONE_TIME_PRE_KEYS: One-Time Pre-Keys 보충
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rotation_type", nullable = false, length = 50)
    val rotationType: RotationType,

    /**
     * 이전 키 ID (Signed Pre-Key의 경우)
     */
    @Column(name = "previous_key_id")
    val previousKeyId: Int? = null,

    /**
     * 새 키 ID (Signed Pre-Key의 경우)
     */
    @Column(name = "new_key_id")
    val newKeyId: Int? = null,

    /**
     * 추가된 One-Time Pre-Keys 개수 (One-Time Pre-Keys 보충의 경우)
     */
    @Column(name = "keys_added")
    val keysAdded: Int? = null,

    /**
     * 회전 작업 성공 여부
     */
    @Column(name = "success", nullable = false)
    val success: Boolean = true,

    /**
     * 오류 메시지 (실패한 경우)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    /**
     * 회전 작업 실행 시간 (밀리초)
     */
    @Column(name = "execution_time_ms")
    val executionTimeMs: Long? = null,

    /**
     * 생성 시각
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 키 회전 타입 열거형
 */
enum class RotationType {
    SIGNED_PRE_KEY,      // Signed Pre-Key 회전
    ONE_TIME_PRE_KEYS,  // One-Time Pre-Keys 보충
    BOTH                // 둘 다 수행
}

