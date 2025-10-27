package com.august.cupid.model.dto

import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 생성 요청 DTO
 */
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val profileImageUrl: String? = null,
    val bio: String? = null
)

/**
 * 사용자 응답 DTO
 */
data class UserResponse(
    val id: UUID,
    val username: String,
    val email: String,
    val profileImageUrl: String?,
    val bio: String?,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val lastSeenAt: LocalDateTime?
)

/**
 * 사용자 업데이트 요청 DTO
 */
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val bio: String? = null
)

/**
 * 채널 생성 요청 DTO
 */
data class CreateChannelRequest(
    val name: String? = null,
    val type: String, // DIRECT, GROUP
    val matchId: UUID? = null
)

/**
 * 채널 응답 DTO
 */
data class ChannelResponse(
    val id: UUID,
    val name: String?,
    val type: String,
    val creatorId: UUID,
    val matchId: UUID?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * 메시지 전송 요청 DTO
 */
data class SendMessageRequest(
    val channelId: UUID,
    val encryptedContent: String,
    val messageType: String = "TEXT", // TEXT, IMAGE, FILE, SYSTEM
    val fileMetadata: FileMetadataDto? = null,
    val replyToMessageId: UUID? = null
)

/**
 * 파일 메타데이터 DTO
 */
data class FileMetadataDto(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val fileUrl: String,
    val thumbnailUrl: String? = null
)

/**
 * 메시지 응답 DTO
 */
data class MessageResponse(
    val id: UUID,
    val channelId: UUID,
    val senderId: UUID,
    val encryptedContent: String,
    val messageType: String,
    val fileMetadata: FileMetadataDto?,
    val replyToMessageId: UUID?,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * 매칭 생성 요청 DTO
 */
data class CreateMatchRequest(
    val user1Id: UUID,
    val user2Id: UUID,
    val expiresAt: LocalDateTime? = null
)

/**
 * 매칭 응답 DTO
 */
data class MatchResponse(
    val id: UUID,
    val user1Id: UUID,
    val user2Id: UUID,
    val status: String,
    val matchedAt: LocalDateTime,
    val expiresAt: LocalDateTime?
)

/**
 * 알림 설정 요청 DTO
 */
data class NotificationSettingsRequest(
    val enabled: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val showPreview: Boolean,
    val dndEnabled: Boolean,
    val dndStartTime: String? = null, // HH:mm 형식
    val dndEndTime: String? = null,   // HH:mm 형식
    val dndDays: List<Int>? = null    // 1-7 (월-일)
)

/**
 * FCM 토큰 등록 요청 DTO
 */
data class RegisterFcmTokenRequest(
    val token: String,
    val deviceType: String, // ANDROID, IOS, WEB
    val deviceName: String? = null,
    val appVersion: String? = null
)

/**
 * 신고 요청 DTO
 */
data class CreateReportRequest(
    val targetUserId: UUID? = null,
    val targetMessageId: UUID? = null,
    val reportType: String, // SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, FAKE_PROFILE, OTHER
    val description: String? = null,
    val contextMessageIds: List<UUID>? = null
)

/**
 * 신고 응답 DTO
 */
data class ReportResponse(
    val id: UUID,
    val submitterId: UUID,
    val targetUserId: UUID?,
    val targetMessageId: UUID?,
    val reportType: String,
    val description: String?,
    val status: String,
    val createdAt: LocalDateTime,
    val resolvedAt: LocalDateTime?
)

/**
 * API 응답 래퍼 DTO
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * 페이징 응답 DTO
 */
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)
