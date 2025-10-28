package com.august.cupid.model.dto.notification

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

/**
 * 사용자 알림 설정 관련 DTO
 * 전역 알림 설정 및 방해금지 모드 관리
 */

/**
 * 사용자 알림 설정 업데이트 요청 DTO
 */
data class UpdateUserNotificationSettingsRequest(
    val enabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val vibrationEnabled: Boolean? = null,
    val showPreview: Boolean? = null,
    val dndEnabled: Boolean? = null,
    @JsonFormat(pattern = "HH:mm")
    val dndStartTime: LocalTime? = null,
    @JsonFormat(pattern = "HH:mm")
    val dndEndTime: LocalTime? = null,
    val dndDays: List<Int>? = null // 1=월요일, 7=일요일
)

/**
 * 사용자 알림 설정 응답 DTO
 */
data class UserNotificationSettingsResponse(
    val userId: String,
    val enabled: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val showPreview: Boolean,
    val dndEnabled: Boolean,
    @JsonFormat(pattern = "HH:mm")
    val dndStartTime: LocalTime,
    @JsonFormat(pattern = "HH:mm")
    val dndEndTime: LocalTime,
    val dndDays: List<Int>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val updatedAt: LocalDateTime
)

/**
 * 채널별 알림 설정 업데이트 요청 DTO
 */
data class UpdateChannelNotificationSettingsRequest(
    val enabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val soundName: String? = null,
    val vibrationEnabled: Boolean? = null,
    val vibrationPattern: List<Int>? = null, // 밀리초 배열
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val mutedUntil: LocalDateTime? = null
)

/**
 * 채널별 알림 설정 응답 DTO
 */
data class ChannelNotificationSettingsResponse(
    val id: String,
    val channelId: String,
    val userId: String,
    val enabled: Boolean,
    val soundEnabled: Boolean,
    val soundName: String,
    val vibrationEnabled: Boolean,
    val vibrationPattern: List<Int>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val mutedUntil: LocalDateTime?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val updatedAt: LocalDateTime
)

/**
 * 채널 음소거 요청 DTO
 */
data class MuteChannelRequest(
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val mutedUntil: LocalDateTime? = null // null이면 영구 음소거
)

/**
 * 채널 음소거 해제 요청 DTO
 */
data class UnmuteChannelRequest(
    val channelId: String
)

/**
 * 알림 설정 요약 응답 DTO
 */
data class NotificationSettingsSummaryResponse(
    val userId: String,
    val globalSettings: UserNotificationSettingsResponse,
    val channelSettings: List<ChannelNotificationSettingsResponse>
)
