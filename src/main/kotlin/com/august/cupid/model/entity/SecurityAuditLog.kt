package com.august.cupid.model.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime
import java.util.*

/**
 * 보안 감사 로그 엔티티 (MongoDB)
 * 
 * 목적:
 * - 암호화 작업 이벤트 추적
 * - 보안 사고 감지 및 조사
 * - 컴플라이언스 감사 추적
 * - 성능 모니터링
 * 
 * TTL: 90일 후 자동 삭제 (MongoDB TTL 인덱스 사용)
 */
@Document(collection = "security_audit_logs")
@CompoundIndexes(
    CompoundIndex(
        name = "user_event_time_idx",
        def = "{'user_id': 1, 'event_type': 1, 'created_at': -1}"
    ),
    CompoundIndex(
        name = "user_success_idx",
        def = "{'user_id': 1, 'success': 1, 'created_at': -1}"
    ),
    CompoundIndex(
        name = "event_type_time_idx",
        def = "{'event_type': 1, 'created_at': -1}"
    ),
    CompoundIndex(
        name = "ttl_idx",
        def = "{'expires_at': 1}",
        expireAfterSeconds = 0
    )
)
data class SecurityAuditLog(
    @Id
    val id: UUID = UUID.randomUUID(),

    /**
     * 이벤트를 발생시킨 사용자 ID
     */
    @Indexed
    @Field("user_id")
    val userId: UUID?,

    /**
     * 이벤트 타입
     */
    @Indexed
    @Field("event_type")
    val eventType: AuditEventType,

    /**
     * 이벤트 세부 타입 (선택적)
     */
    @Field("event_subtype")
    val eventSubtype: String? = null,

    /**
     * 작업 성공 여부
     */
    @Indexed
    @Field("success")
    val success: Boolean,

    /**
     * 오류 메시지 (실패한 경우)
     */
    @Field("error_message")
    val errorMessage: String? = null,

    /**
     * 작업 실행 시간 (밀리초)
     */
    @Field("execution_time_ms")
    val executionTimeMs: Long? = null,

    /**
     * 추가 메타데이터
     * - recipient_id: 수신자 ID (암호화 작업의 경우)
     * - session_id: 세션 ID
     * - device_id: 디바이스 ID
     * - ip_address: IP 주소 (선택적)
     * - user_agent: User Agent (선택적)
     */
    @Field("metadata")
    val metadata: Map<String, Any>? = null,

    /**
     * 로그 생성 시각
     */
    @Indexed
    @Field("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * TTL용 만료 시각 (90일 후)
     */
    @Indexed
    @Field("expires_at")
    val expiresAt: LocalDateTime = LocalDateTime.now().plusDays(90)
)

/**
 * 감사 이벤트 타입 열거형
 */
enum class AuditEventType {
    /**
     * 키 생성 이벤트
     */
    KEY_GENERATION,
    
    /**
     * 키 등록 이벤트
     */
    KEY_REGISTRATION,
    
    /**
     * 키 회전 이벤트
     */
    KEY_ROTATION,
    
    /**
     * 세션 초기화 이벤트
     */
    SESSION_INITIALIZATION,
    
    /**
     * 메시지 암호화 이벤트
     */
    MESSAGE_ENCRYPTION,
    
    /**
     * 메시지 복호화 이벤트
     */
    MESSAGE_DECRYPTION,
    
    /**
     * 암호화 실패 이벤트
     */
    ENCRYPTION_FAILURE,
    
    /**
     * 복호화 실패 이벤트
     */
    DECRYPTION_FAILURE,
    
    /**
     * 세션 생성 실패 이벤트
     */
    SESSION_FAILURE,
    
    /**
     * 의심스러운 활동 감지
     */
    SUSPICIOUS_ACTIVITY,
    
    /**
     * 키 번들 조회 이벤트
     */
    KEY_BUNDLE_RETRIEVAL,
    
    /**
     * 지문 검증 이벤트
     */
    FINGERPRINT_VERIFICATION
}

