package com.august.cupid.model.dto.notification

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.util.*

/**
 * 푸시 알림 관련 DTO
 * FCM을 통한 푸시 알림 전송 및 관리
 */

/**
 * 푸시 알림 전송 요청 DTO
 */
data class SendPushNotificationRequest(
    val userId: String,
    val title: String,
    val body: String,
    val data: Map<String, String>? = null,
    val channelId: String? = null,
    val messageId: String? = null,
    val priority: NotificationPriority = NotificationPriority.HIGH
)

/**
 * 푸시 알림 전송 응답 DTO
 */
data class SendPushNotificationResponse(
    val success: Boolean,
    val messageId: String?,
    val errorMessage: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val sentAt: LocalDateTime
)

/**
 * 배치 푸시 알림 전송 요청 DTO
 */
data class SendBatchPushNotificationRequest(
    val userIds: List<String>,
    val title: String,
    val body: String,
    val data: Map<String, String>? = null,
    val channelId: String? = null,
    val priority: NotificationPriority = NotificationPriority.HIGH
)

/**
 * 배치 푸시 알림 전송 응답 DTO
 */
data class SendBatchPushNotificationResponse(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<SendPushNotificationResponse>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val sentAt: LocalDateTime
)

/**
 * Silent Push 알림 전송 요청 DTO
 * E2E 암호화된 메시지의 경우 내용 없이 전송
 */
data class SendSilentPushRequest(
    val userId: String,
    val channelId: String,
    val messageId: String,
    val senderId: String,
    val data: Map<String, String>? = null
)

/**
 * 알림 전송 결과 DTO
 */
data class NotificationDeliveryResult(
    val userId: String,
    val tokenId: String?,
    val success: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val deliveredAt: LocalDateTime
)

/**
 * 알림 우선순위 열거형
 */
enum class NotificationPriority {
    LOW,    // 낮은 우선순위
    NORMAL, // 일반 우선순위
    HIGH    // 높은 우선순위
}

/**
 * 알림 통계 응답 DTO
 */
data class NotificationStatsResponse(
    val userId: String,
    val totalSent: Int,
    val totalDelivered: Int,
    val totalFailed: Int,
    val deliveryRate: Double, // 배송률 (0.0 ~ 1.0)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val lastSentAt: LocalDateTime?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val periodStart: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val periodEnd: LocalDateTime
)
