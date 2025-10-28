package com.august.cupid.controller

import com.august.cupid.model.dto.ApiResponse
import com.august.cupid.model.dto.RegisterFcmTokenRequest
import com.august.cupid.model.dto.NotificationSettingsRequest
import com.august.cupid.model.dto.notification.*
import com.august.cupid.service.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 알림 관리 컨트롤러
 * FCM 토큰 관리 및 알림 설정 API
 */
@Tag(name = "Notification", description = "알림 관리 API - FCM 토큰 등록/관리 및 알림 설정")
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * JWT에서 사용자 ID 추출
     */
    private fun getUserIdFromAuthentication(authentication: Authentication): UUID? {
        return try {
            val userIdString = authentication.name
            UUID.fromString(userIdString)
        } catch (e: Exception) {
            logger.error("사용자 ID 추출 실패", e)
            null
        }
    }

    /**
     * FCM 토큰 등록
     * POST /api/v1/notifications/fcm-token
     */
    @Operation(
        summary = "FCM 토큰 등록",
        description = "사용자의 FCM 토큰을 등록하여 푸시 알림 수신을 활성화합니다"
    )
    @ApiResponses(
        value = [
            io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "토큰 등록 성공",
                content = [Content(schema = Schema(implementation = Map::class))]
            ),
            io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content = [Content(schema = Schema(implementation = Map::class))]
            )
        ]
    )
    @PostMapping("/fcm-token")
    fun registerFcmToken(
        authentication: Authentication,
        @RequestBody request: RegisterFcmTokenRequest
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("FCM 토큰 등록 요청: userId={}", userId)
        
        return try {
            val result = notificationService.registerFcmToken(userId, request)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "FCM 토큰이 성공적으로 등록되었습니다")
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.error ?: "FCM 토큰 등록 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("FCM 토큰 등록 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 내 FCM 토큰 목록 조회
     * GET /api/v1/notifications/fcm-token
     */
    @Operation(
        summary = "FCM 토큰 목록 조회",
        description = "현재 사용자의 등록된 FCM 토큰 목록을 조회합니다"
    )
    @GetMapping("/fcm-token")
    fun getMyFcmTokens(
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("FCM 토큰 목록 조회: userId={}", userId)
        
        return try {
            val result = notificationService.getFcmTokens(userId)
            
            if (result.success && result.data != null) {
                val tokenList = result.data.map { token ->
                    mapOf(
                        "id" to token.id.toString(),
                        "token" to token.token.take(20) + "...", // 보안상 일부만 표시
                        "deviceType" to token.deviceType.name,
                        "deviceName" to token.deviceName,
                        "appVersion" to token.appVersion,
                        "createdAt" to token.createdAt.toString(),
                        "lastUsedAt" to token.lastUsedAt.toString()
                    )
                }
                
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "tokens" to tokenList,
                    "count" to tokenList.size
                ))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                    "success" to false,
                    "error" to (result.error ?: "FCM 토큰 조회 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("FCM 토큰 목록 조회 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * FCM 토큰 삭제
     * DELETE /api/v1/notifications/fcm-token/{tokenId}
     */
    @Operation(
        summary = "FCM 토큰 삭제",
        description = "등록된 FCM 토큰을 삭제합니다"
    )
    @DeleteMapping("/fcm-token/{tokenId}")
    fun deleteFcmToken(
        authentication: Authentication,
        @PathVariable tokenId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("FCM 토큰 삭제 요청: userId={}, tokenId={}", userId, tokenId)
        
        return try {
            val tokenIdUuid = UUID.fromString(tokenId)
            val result = notificationService.deleteFcmToken(userId, tokenIdUuid)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "FCM 토큰이 성공적으로 삭제되었습니다")
                ))
            } else {
                val errorMap = mutableMapOf<String, Any>(
                    "success" to false,
                    "error" to (result.error ?: "FCM 토큰 삭제 실패")
                )
                result.message?.let { errorMap["message"] = it }
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMap)
            }
        } catch (e: Exception) {
            logger.error("FCM 토큰 삭제 중 오류 발생: userId={}, tokenId={}", userId, tokenId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 알림 설정 조회
     * GET /api/v1/notifications/settings
     */
    @Operation(
        summary = "알림 설정 조회",
        description = "현재 사용자의 알림 설정(방해금지, 소리, 진동 등)을 조회합니다"
    )
    @GetMapping("/settings")
    fun getNotificationSettings(
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("알림 설정 조회: userId={}", userId)
        
        return try {
            val result = notificationService.getUserNotificationSettings(userId)
            
            if (result.success) {
                val settings = result.data
                val responseMap = mutableMapOf<String, Any>(
                    "success" to true
                )
                
                if (settings != null) {
                    responseMap["settings"] = mapOf(
                        "enabled" to settings.enabled,
                        "dndEnabled" to settings.dndEnabled,
                        "dndStartTime" to settings.dndStartTime.toString(),
                        "dndEndTime" to settings.dndEndTime.toString(),
                        "soundEnabled" to settings.soundEnabled,
                        "vibrationEnabled" to settings.vibrationEnabled,
                        "showPreview" to settings.showPreview,
                        "dndDays" to settings.dndDays
                    )
                } else {
                    // 기본값 반환
                    responseMap["settings"] = mapOf(
                        "enabled" to true,
                        "dndEnabled" to false,
                        "soundEnabled" to true,
                        "vibrationEnabled" to true,
                        "showPreview" to true
                    )
                    responseMap["isDefault"] = true
                }
                
                ResponseEntity.ok(responseMap)
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                    "success" to false,
                    "error" to (result.error ?: "알림 설정 조회 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("알림 설정 조회 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 알림 설정 업데이트
     * PUT /api/v1/notifications/settings
     */
    @Operation(
        summary = "알림 설정 업데이트",
        description = "알림 설정(방해금지 모드, 소리, 진동 등)을 업데이트합니다"
    )
    @PutMapping("/settings")
    fun updateNotificationSettings(
        authentication: Authentication,
        @RequestBody request: UpdateUserNotificationSettingsRequest
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("알림 설정 업데이트 요청: userId={}", userId)
        
        return try {
            // NotificationSettingsRequest로 변환
            val notificationSettingsRequest = NotificationSettingsRequest(
                enabled = request.enabled ?: true,
                soundEnabled = request.soundEnabled ?: true,
                vibrationEnabled = request.vibrationEnabled ?: true,
                showPreview = request.showPreview ?: true,
                dndEnabled = request.dndEnabled ?: false,
                dndStartTime = request.dndStartTime?.toString(),
                dndEndTime = request.dndEndTime?.toString(),
                dndDays = request.dndDays
            )
            
            val result = notificationService.updateUserNotificationSettings(userId, notificationSettingsRequest)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "알림 설정이 성공적으로 업데이트되었습니다")
                ))
            } else {
                val errorBody = mutableMapOf<String, Any>(
                    "success" to false,
                    "error" to (result.error ?: "알림 설정 업데이트 실패")
                )
                result.message?.let { errorBody["message"] = it }
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody)
            }
        } catch (e: Exception) {
            logger.error("알림 설정 업데이트 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }
    
    /**
     * 채널별 알림 설정 업데이트
     * PUT /api/v1/channels/{channelId}/notifications/settings
     */
    @Operation(
        summary = "채널별 알림 설정 업데이트",
        description = "특정 채널에 대한 알림 설정을 업데이트합니다"
    )
    @PutMapping("/channels/{channelId}/settings")
    fun updateChannelNotificationSettings(
        authentication: Authentication,
        @PathVariable channelId: String,
        @RequestBody request: UpdateChannelNotificationSettingsRequest
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("채널별 알림 설정 업데이트: userId={}, channelId={}", userId, channelId)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = notificationService.updateChannelNotificationSettings(
                channelId = channelIdUuid,
                userId = userId,
                enabled = request.enabled ?: true,
                soundEnabled = request.soundEnabled ?: true,
                soundName = request.soundName ?: "default",
                vibrationEnabled = request.vibrationEnabled ?: true,
                vibrationPattern = request.vibrationPattern ?: listOf(0, 200, 100, 200),
                mutedUntil = request.mutedUntil
            )
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "채널 알림 설정이 업데이트되었습니다")
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.error ?: "채널 알림 설정 업데이트 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("채널별 알림 설정 업데이트 중 오류: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }
    
    /**
     * 채널 음소거
     * POST /api/v1/channels/{channelId}/notifications/mute
     */
    @Operation(
        summary = "채널 음소거",
        description = "특정 채널의 알림을 음소거합니다 (일시적 또는 영구적으로)"
    )
    @PostMapping("/channels/{channelId}/mute")
    fun muteChannel(
        authentication: Authentication,
        @PathVariable channelId: String,
        @RequestBody request: MuteChannelRequest
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("채널 음소거 요청: userId={}, channelId={}", userId, channelId)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = notificationService.updateChannelNotificationSettings(
                channelId = channelIdUuid,
                userId = userId,
                enabled = true, // 음소거는 enabled를 false로 하지 않음
                soundEnabled = true,
                soundName = "default",
                vibrationEnabled = true,
                vibrationPattern = listOf(0, 200, 100, 200),
                mutedUntil = request.mutedUntil ?: null
            )
            
            if (result.success) {
                val responseMap = mutableMapOf<String, Any>(
                    "success" to true,
                    "message" to "채널이 음소거되었습니다"
                )
                request.mutedUntil?.let { responseMap["mutedUntil"] = it.toString() }
                ResponseEntity.ok(responseMap)
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to "채널 음소거 실패"
                ))
            }
        } catch (e: Exception) {
            logger.error("채널 음소거 중 오류: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }
    
    /**
     * 채널 음소거 해제
     * POST /api/v1/channels/{channelId}/notifications/unmute
     */
    @Operation(
        summary = "채널 음소거 해제",
        description = "음소거된 채널의 알림을 다시 활성화합니다"
    )
    @PostMapping("/channels/{channelId}/unmute")
    fun unmuteChannel(
        authentication: Authentication,
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("채널 음소거 해제 요청: userId={}, channelId={}", userId, channelId)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = notificationService.updateChannelNotificationSettings(
                channelId = channelIdUuid,
                userId = userId,
                enabled = true,
                soundEnabled = true,
                soundName = "default",
                vibrationEnabled = true,
                vibrationPattern = listOf(0, 200, 100, 200),
                mutedUntil = null // 음소거 해제
            )
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to "채널 음소거가 해제되었습니다"
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to "채널 음소거 해제 실패"
                ))
            }
        } catch (e: Exception) {
            logger.error("채널 음소거 해제 중 오류: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }
}

