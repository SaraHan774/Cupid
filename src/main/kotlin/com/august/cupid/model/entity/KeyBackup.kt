package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * 키 백업 엔티티 (PostgreSQL)
 * 
 * 사용자의 Signal Protocol 키 백업을 저장합니다.
 * 백업 데이터는 별도의 백업 비밀번호로 암호화되어 저장됩니다.
 * 
 * SECURITY:
 * - 백업 데이터는 사용자 비밀번호와 다른 별도 비밀번호로 암호화
 * - 만료 기간 설정 가능 (기본 90일)
 * - 삭제 시 안전하게 제거
 */
@Entity
@Table(
    name = "key_backups",
    indexes = [
        Index(name = "idx_key_backups_user_id", columnList = "user_id"),
        Index(name = "idx_key_backups_expires_at", columnList = "expires_at"),
        Index(name = "idx_key_backups_created_at", columnList = "created_at")
    ]
)
data class KeyBackup(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /**
     * 암호화된 백업 데이터 (AES-256-GCM)
     * 백업 비밀번호로 암호화된 Signal Protocol 키 데이터
     * 포맷: JSON {identityKey, signedPreKey, preKeys, ...}
     */
    @Column(name = "encrypted_backup_data", nullable = false, columnDefinition = "TEXT")
    val encryptedBackupData: String,

    /**
     * 백업 데이터 해시 (SHA-256)
     * 백업 데이터 무결성 검증용
     */
    @Column(name = "backup_hash", nullable = false, length = 64)
    val backupHash: String,

    /**
     * 백업 생성 시점
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 백업 만료 시간 (기본 90일)
     * NULL이면 만료되지 않음
     */
    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = LocalDateTime.now().plusDays(90),

    /**
     * 백업 메타데이터 (JSON)
     * 예: {"device_name": "iPhone 13", "app_version": "1.0.0"}
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    val metadata: String? = null,

    /**
     * 백업이 사용되었는지 여부
     * 복구 시 true로 설정
     */
    @Column(name = "is_used", nullable = false)
    val isUsed: Boolean = false,

    /**
     * 백업 사용 시점
     */
    @Column(name = "used_at")
    val usedAt: LocalDateTime? = null
)

