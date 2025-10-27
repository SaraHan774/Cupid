package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 엔티티 (PostgreSQL)
 * 기본 사용자 정보와 프로필 이미지 최적화 정보를 포함
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_users_username", columnList = "username"),
        Index(name = "idx_users_email", columnList = "email"),
        Index(name = "idx_users_is_active", columnList = "is_active"),
        Index(name = "idx_users_last_seen", columnList = "last_seen_at")
    ]
)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "username", nullable = false, unique = true, length = 50)
    val username: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    val passwordHash: String,

    @Column(name = "email", unique = true, length = 255)
    val email: String? = null,

    // 프로필 이미지 최적화
    @Column(name = "profile_image_url", length = 500)
    val profileImageUrl: String? = null,

    @Column(name = "profile_thumbnail_url", length = 500)
    val profileThumbnailUrl: String? = null,

    @Column(name = "profile_image_blurhash", length = 50)
    val profileImageBlurhash: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_image_metadata", columnDefinition = "jsonb")
    val profileImageMetadata: Map<String, Any>? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_seen_at")
    var lastSeenAt: LocalDateTime? = null,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null
) {
    /**
     * 사용자명 길이 검증
     */
    init {
        require(username.length >= 3) { "사용자명은 3자 이상이어야 합니다" }
    }
}
