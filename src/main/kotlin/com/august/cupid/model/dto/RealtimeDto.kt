package com.august.cupid.model.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.*

/**
 * 실시간 기능을 위한 WebSocket DTO 모음
 *
 * 포함 기능:
 * - 타이핑 인디케이터
 * - 읽음 표시 (Read Receipts)
 * - 온라인 상태 (Presence)
 */

// ============================================
// 타이핑 인디케이터 DTOs
// ============================================

/**
 * 타이핑 시작/중지 요청 DTO
 *
 * WebSocket endpoint: /app/typing/start, /app/typing/stop
 */
data class TypingRequest(
    @field:NotNull(message = "채널 ID는 필수입니다")
    val channelId: UUID
)

/**
 * 타이핑 이벤트 브로드캐스트 DTO
 *
 * Subscription topic: /topic/channel.{channelId}.typing
 *
 * Message Format:
 * {
 *   "userId": "uuid-string",
 *   "channelId": "uuid-string",
 *   "isTyping": true,
 *   "timestamp": "2025-10-30T10:30:00"
 * }
 */
data class TypingEvent(
    val userId: UUID,
    val channelId: UUID,
    val isTyping: Boolean,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 타이핑 중인 사용자 목록 응답 DTO
 *
 * Use Cases:
 * - 초기 연결 시 현재 상태 동기화
 * - 재연결 후 상태 복구
 */
data class TypingUsersResponse(
    val channelId: UUID,
    val typingUserIds: List<String>,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// ============================================
// 읽음 표시 (Read Receipts) DTOs
// ============================================

/**
 * 메시지 읽음 표시 요청 DTO
 *
 * REST API: POST /api/v1/channels/{channelId}/messages/{messageId}/read
 */
data class MarkAsReadRequest(
    val messageId: UUID,
    val channelId: UUID
)

/**
 * 배치 읽음 표시 요청 DTO
 *
 * Performance Optimization:
 * - 한 번의 요청으로 여러 메시지 읽음 표시
 * - 채널 전체 메시지 읽음 처리에 유용
 *
 * REST API: POST /api/v1/channels/{channelId}/messages/read-batch
 */
data class BatchMarkAsReadRequest(
    @field:NotNull(message = "채널 ID는 필수입니다")
    val channelId: UUID,

    val messageIds: List<UUID>
)

/**
 * 읽음 표시 이벤트 DTO
 *
 * WebSocket notification: /queue/read-receipts (메시지 발신자에게 전송)
 *
 * Message Format:
 * {
 *   "messageId": "uuid-string",
 *   "channelId": "uuid-string",
 *   "userId": "uuid-string",
 *   "readAt": "2025-10-30T10:30:00"
 * }
 *
 * Privacy Considerations:
 * - 설정에 따라 전송 여부 결정
 * - 발신자만 수신
 */
data class ReadReceiptEvent(
    val messageId: UUID,
    val channelId: UUID,
    val userId: UUID,
    val readAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 읽음 표시 응답 DTO
 */
data class ReadReceiptResponse(
    val messageId: UUID,
    val channelId: UUID,
    val userId: UUID,
    val readAt: LocalDateTime,
    val success: Boolean = true
)

/**
 * 배치 읽음 표시 응답 DTO
 */
data class BatchReadReceiptResponse(
    val channelId: UUID,
    val successCount: Int,
    val failedMessageIds: List<UUID> = emptyList(),
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 메시지별 읽음 수 응답 DTO
 *
 * Use Cases:
 * - 메시지 UI에 읽은 사람 수 표시
 * - 그룹 채팅에서 읽음 통계
 */
data class MessageReadCountResponse(
    val messageId: UUID,
    val readCount: Long,
    val totalMembers: Int,
    val readUserIds: List<UUID> = emptyList()
)

/**
 * 채널 읽지 않은 메시지 수 응답 DTO
 */
data class UnreadCountResponse(
    val channelId: UUID,
    val unreadCount: Long,
    val lastReadAt: LocalDateTime?
)

// ============================================
// 온라인 상태 (Presence) DTOs
// ============================================

/**
 * 온라인 상태 변경 이벤트 DTO
 *
 * Subscription topic: /topic/presence
 *
 * Message Format:
 * {
 *   "userId": "uuid-string",
 *   "status": "ONLINE",
 *   "timestamp": "2025-10-30T10:30:00"
 * }
 */
data class PresenceEvent(
    val userId: UUID,
    val status: PresenceStatus,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 온라인 상태 열거형
 */
enum class PresenceStatus {
    ONLINE,
    OFFLINE,
    AWAY
}

/**
 * 채널 멤버 온라인 상태 응답 DTO
 */
data class ChannelPresenceResponse(
    val channelId: UUID,
    val onlineUserIds: List<String>,
    val totalMembers: Int,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 하트비트 요청 DTO
 *
 * WebSocket endpoint: /app/heartbeat
 *
 * Purpose:
 * - 연결 상태 유지
 * - 온라인 상태 TTL 갱신
 */
data class HeartbeatRequest(
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 하트비트 응답 DTO
 */
data class HeartbeatResponse(
    val received: Boolean = true,
    val serverTimestamp: LocalDateTime = LocalDateTime.now()
)

// ============================================
// 에러 응답 DTOs
// ============================================

/**
 * WebSocket 에러 응답 DTO
 *
 * Use Cases:
 * - 권한 없음
 * - 잘못된 요청
 * - 서버 오류
 */
data class WebSocketErrorResponse(
    val errorCode: String,
    val message: String,
    val details: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * WebSocket 에러 코드
 */
object WebSocketErrorCodes {
    const val UNAUTHORIZED = "WS_UNAUTHORIZED"
    const val INVALID_REQUEST = "WS_INVALID_REQUEST"
    const val CHANNEL_NOT_FOUND = "WS_CHANNEL_NOT_FOUND"
    const val MESSAGE_NOT_FOUND = "WS_MESSAGE_NOT_FOUND"
    const val INTERNAL_ERROR = "WS_INTERNAL_ERROR"
    const val RATE_LIMIT_EXCEEDED = "WS_RATE_LIMIT_EXCEEDED"
}

// ============================================
// 연결 관리 DTOs
// ============================================

/**
 * WebSocket 연결 정보 DTO
 */
data class ConnectionInfo(
    val sessionId: String,
    val userId: UUID,
    val connectedAt: LocalDateTime,
    val lastActivity: LocalDateTime
)

/**
 * 채널 구독 요청 DTO
 *
 * WebSocket endpoint: /app/subscribe
 */
data class ChannelSubscribeRequest(
    @field:NotNull(message = "채널 ID는 필수입니다")
    val channelId: UUID
)

/**
 * 채널 구독 해제 요청 DTO
 *
 * WebSocket endpoint: /app/unsubscribe
 */
data class ChannelUnsubscribeRequest(
    @field:NotNull(message = "채널 ID는 필수입니다")
    val channelId: UUID
)

/**
 * 구독 확인 응답 DTO
 */
data class SubscriptionResponse(
    val channelId: UUID,
    val subscribed: Boolean,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
