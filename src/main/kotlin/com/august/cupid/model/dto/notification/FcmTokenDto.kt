package com.august.cupid.model.dto.notification

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.util.*

/**
 * FCM 토큰 관련 DTO
 * 디바이스별 FCM 토큰 관리
 */

/**
 * FCM 토큰 등록 요청 DTO
 */
data class RegisterFcmTokenRequest(
    val token: String,
    val deviceType: DeviceTypeDto,
    val deviceName: String? = null,
    val appVersion: String? = null
)

/**
 * FCM 토큰 업데이트 요청 DTO
 */
data class UpdateFcmTokenRequest(
    val token: String,
    val deviceName: String? = null,
    val appVersion: String? = null
)

/**
 * FCM 토큰 응답 DTO
 */
data class FcmTokenResponse(
    val id: String,
    val userId: String,
    val token: String,
    val deviceType: DeviceTypeDto,
    val deviceName: String?,
    val appVersion: String?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val lastUsedAt: LocalDateTime,
    val isActive: Boolean
)

/**
 * 디바이스 타입 DTO
 */
enum class DeviceTypeDto {
    IOS,     // iOS
    ANDROID, // Android
    WEB      // Web
}

/**
 * FCM 토큰 목록 응답 DTO
 */
data class FcmTokenListResponse(
    val userId: String,
    val tokens: List<FcmTokenResponse>
)

/**
 * FCM 토큰 삭제 요청 DTO
 */
data class DeleteFcmTokenRequest(
    val tokenId: String
)

/**
 * FCM 토큰 비활성화 요청 DTO
 */
data class DeactivateFcmTokenRequest(
    val tokenId: String
)

/**
 * FCM 토큰 활성화 요청 DTO
 */
data class ActivateFcmTokenRequest(
    val tokenId: String
)
