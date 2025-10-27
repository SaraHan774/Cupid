package com.august.cupid.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * 신고 엔티티 (PostgreSQL)
 * E2E 암호화 절충안을 통한 신고 시스템
 */
@Entity
@Table(
    name = "reports",
    indexes = [
        Index(name = "idx_reports_submitter", columnList = "submitter_id"),
        Index(name = "idx_reports_target_user", columnList = "target_user_id"),
        Index(name = "idx_reports_target_message", columnList = "target_message_id"),
        Index(name = "idx_reports_status", columnList = "status"),
        Index(name = "idx_reports_created_at", columnList = "created_at")
    ]
)
data class Report(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitter_id", nullable = false)
    val submitter: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    val targetUser: User? = null,

    @Column(name = "target_message_id")
    val targetMessageId: UUID? = null,

    // 신고자가 복호화하여 제공한 내용 (E2E 암호화 절충안)
    @Column(name = "reported_content", nullable = false, columnDefinition = "TEXT")
    val reportedContent: String,

    @Column(name = "reported_content_hash", length = 64)
    val reportedContentHash: String? = null,  // SHA-256 해시 (검증용)

    // 추가 증거
    @Column(name = "screenshot_url", columnDefinition = "TEXT")
    val screenshotUrl: String? = null,

    // 컨텍스트 정보
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "context_message_ids", columnDefinition = "uuid[]")
    val contextMessageIds: List<UUID>? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    val reportType: ReportType,

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    val reason: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: ReportStatus = ReportStatus.PENDING,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "resolved_at")
    val resolvedAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolver_id")
    val resolver: User? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 신고 유형 열거형
 */
enum class ReportType {
    SPAM,           // 스팸
    HARASSMENT,     // 괴롭힘
    INAPPROPRIATE,  // 부적절한 내용
    OTHER           // 기타
}

/**
 * 신고 상태 열거형
 */
enum class ReportStatus {
    PENDING,   // 대기
    REVIEWED,  // 검토됨
    RESOLVED,  // 해결됨
    DISMISSED  // 기각됨
}
