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

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_seen_at")
    val lastSeenAt: LocalDateTime? = null,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null
) {
    /**
     * JPA를 위한 기본 생성자 (공식 문서 권장사항)
     */
    constructor() : this(
        id = UUID.randomUUID(),
        username = "", // 공식 문서 권장: 빈 문자열 사용
        passwordHash = "",
        email = null,
        profileImageUrl = null,
        profileThumbnailUrl = null,
        profileImageBlurhash = null,
        profileImageMetadata = null,
        createdAt = LocalDateTime.now(), // @CreationTimestamp가 자동 처리하지만 기본값 필요
        updatedAt = LocalDateTime.now(), // @UpdateTimestamp가 자동 처리하지만 기본값 필요
        lastSeenAt = null,
        isActive = true,
        metadata = null
    )

    /**
     * 사용자 검증 (공식 문서 권장사항 - 별도 메서드)
     */
    fun validate(): User {
        require(username.length >= 3) { "사용자명은 3자 이상이어야 합니다" }
        return this
    }

    /**
     * UserResponse DTO로 변환
     */
    fun toResponse(): com.august.cupid.model.dto.UserResponse {
        return com.august.cupid.model.dto.UserResponse(
            id = id,
            username = username,
            email = email ?: "",
            profileImageUrl = profileImageUrl,
            bio = null, // User 엔티티에 bio 필드가 없으므로 null
            isActive = isActive,
            createdAt = createdAt,
            lastSeenAt = lastSeenAt
        )
    }
}
