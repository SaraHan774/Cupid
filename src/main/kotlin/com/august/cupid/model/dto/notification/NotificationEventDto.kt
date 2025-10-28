package com.august.cupid.model.dto.notification

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.util.*

/**
 * 알림 이벤트 관련 DTO
 * 실시간 알림 이벤트 및 WebSocket 메시지
 */

/**
 * 실시간 알림 이벤트 DTO
 * WebSocket을 통해 클라이언트에게 전송되는 알림 이벤트
 */
data class NotificationEventDto(
    val eventType: NotificationEventType,
    val userId: String,
    val channelId: String? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val title: String? = null,
    val body: String? = null,
    val data: Map<String, String>? = null,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 알림 이벤트 타입 열거형
 */
enum class NotificationEventType {
    MESSAGE_RECEIVED,      // 새 메시지 수신
    MESSAGE_READ,          // 메시지 읽음
    USER_ONLINE,           // 사용자 온라인
    USER_OFFLINE,           // 사용자 오프라인
    CHANNEL_CREATED,        // 채널 생성
    CHANNEL_UPDATED,        // 채널 업데이트
    CHANNEL_DELETED,        // 채널 삭제
    USER_JOINED_CHANNEL,    // 사용자 채널 참여
    USER_LEFT_CHANNEL,      // 사용자 채널 탈퇴
    TYPING_STARTED,         // 타이핑 시작
    TYPING_STOPPED,         // 타이핑 중단
    CALL_INCOMING,          // 통화 수신
    CALL_ENDED,             // 통화 종료
    SYSTEM_NOTIFICATION     // 시스템 알림
}

/**
 * WebSocket 메시지 DTO
 * WebSocket을 통해 주고받는 메시지 구조
 */
data class WebSocketMessageDto(
    val type: WebSocketMessageType,
    val payload: Any,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: String = UUID.randomUUID().toString()
)

/**
 * WebSocket 메시지 타입 열거형
 */
enum class WebSocketMessageType {
    NOTIFICATION,           // 알림 메시지
    MESSAGE,               // 채팅 메시지
    TYPING,                // 타이핑 상태
    PRESENCE,              // 온라인 상태
    ERROR,                 // 에러 메시지
    PING,                  // 연결 확인
    PONG                   // 연결 응답
}

/**
 * 사용자 온라인 상태 DTO
 */
data class UserPresenceDto(
    val userId: String,
    val status: UserPresenceStatus,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val lastSeenAt: LocalDateTime? = null,
    val deviceType: DeviceTypeDto? = null
)

/**
 * 사용자 온라인 상태 열거형
 */
enum class UserPresenceStatus {
    ONLINE,     // 온라인
    OFFLINE,    // 오프라인
    AWAY,       // 자리비움
    BUSY,       // 바쁨
    INVISIBLE   // 보이지 않음
}

/**
 * 타이핑 상태 DTO
 */
data class TypingStatusDto(
    val userId: String,
    val channelId: String,
    val isTyping: Boolean,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 알림 설정 변경 이벤트 DTO
 */
data class NotificationSettingsChangedEventDto(
    val userId: String,
    val settingsType: NotificationSettingsType,
    val channelId: String? = null,
    val oldSettings: Any? = null,
    val newSettings: Any,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val changedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 알림 설정 타입 열거형
 */
enum class NotificationSettingsType {
    USER_GLOBAL,        // 사용자 전역 설정
    CHANNEL_SPECIFIC   // 채널별 설정
}
