package com.august.cupid.controller

import com.august.cupid.model.dto.ApiResponse
import com.august.cupid.model.dto.RegisterFcmTokenRequest
import com.august.cupid.model.dto.notification.*
import com.august.cupid.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 알림 관리 컨트롤러
 * FCM 토큰 관리 및 알림 설정 API
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // TODO: JWT 인증 추가 필요
    // 현재는 userId를 파라미터로 받는 형태 (추후 SecurityContext에서 추출)

    /**
     * FCM 토큰 등록
     * POST /api/v1/notifications/fcm-token
     */
    @PostMapping("/fcm-token")
    fun registerFcmToken(
        @RequestParam userId: String, // TODO: JWT에서 추출
        @RequestBody request: RegisterFcmTokenRequest
    ): ResponseEntity<Map<String, Any>> {
        logger.info("FCM 토큰 등록 요청: userId={}", userId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val result = notificationService.registerFcmToken(userIdUuid, request)
            
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
    @GetMapping("/fcm-token")
    fun getMyFcmTokens(
        @RequestParam userId: String // TODO: JWT에서 추출
    ): ResponseEntity<Map<String, Any>> {
        logger.info("FCM 토큰 목록 조회: userId={}", userId)
        
        // TODO: NotificationService에 getFcmTokens 메서드 추가 필요
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "아직 구현되지 않았습니다"
        ))
    }

    /**
     * FCM 토큰 삭제
     * DELETE /api/v1/notifications/fcm-token/{tokenId}
     */
    @DeleteMapping("/fcm-token/{tokenId}")
    fun deleteFcmToken(
        @RequestParam userId: String, // TODO: JWT에서 추출
        @PathVariable tokenId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("FCM 토큰 삭제 요청: userId={}, tokenId={}", userId, tokenId)
        
        // TODO: NotificationService에 deleteFcmToken 메서드 추가 필요
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "아직 구현되지 않았습니다"
        ))
    }

    /**
     * 알림 설정 조회
     * GET /api/v1/notifications/settings
     */
    @GetMapping("/settings")
    fun getNotificationSettings(
        @RequestParam userId: String // TODO: JWT에서 추출
    ): ResponseEntity<Map<String, Any>> {
        logger.info("알림 설정 조회: userId={}", userId)
        
        // TODO: NotificationService에 getNotificationSettings 메서드 추가 필요
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "아직 구현되지 않았습니다"
        ))
    }

    /**
     * 알림 설정 업데이트
     * PUT /api/v1/notifications/settings
     */
    @PutMapping("/settings")
    fun updateNotificationSettings(
        @RequestParam userId: String, // TODO: JWT에서 추출
        @RequestBody request: UpdateUserNotificationSettingsRequest
    ): ResponseEntity<Map<String, Any>> {
        logger.info("알림 설정 업데이트 요청: userId={}", userId)
        
        // TODO: NotificationService에 updateUserNotificationSettings 메서드가 
        // NotificationSettingsRequest를 받도록 수정 필요
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "DTO 매핑 필요 - 나중에 구현"
        ))
    }
    
    /**
     * 채널별 알림 설정 업데이트
     * PUT /api/v1/channels/{channelId}/notifications/settings
     */
    @PutMapping("/channels/{channelId}/settings")
    fun updateChannelNotificationSettings(
        @RequestParam userId: String,
        @PathVariable channelId: String,
        @RequestBody request: UpdateChannelNotificationSettingsRequest
    ): ResponseEntity<Map<String, Any>> {
        logger.info("채널별 알림 설정 업데이트: userId={}, channelId={}", userId, channelId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = notificationService.updateChannelNotificationSettings(
                channelId = channelIdUuid,
                userId = userIdUuid,
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
    @PostMapping("/channels/{channelId}/mute")
    fun muteChannel(
        @RequestParam userId: String,
        @PathVariable channelId: String,
        @RequestBody request: MuteChannelRequest
    ): ResponseEntity<Map<String, Any>> {
        logger.info("채널 음소거 요청: userId={}, channelId={}", userId, channelId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = notificationService.updateChannelNotificationSettings(
                channelId = channelIdUuid,
                userId = userIdUuid,
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
    @PostMapping("/channels/{channelId}/unmute")
    fun unmuteChannel(
        @RequestParam userId: String,
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("채널 음소거 해제 요청: userId={}, channelId={}", userId, channelId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = notificationService.updateChannelNotificationSettings(
                channelId = channelIdUuid,
                userId = userIdUuid,
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

