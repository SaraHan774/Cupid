package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.FcmToken
import com.august.cupid.model.entity.DeviceType
import com.august.cupid.model.entity.UserNotificationSettings
import com.august.cupid.model.entity.ChannelNotificationSettings
import com.august.cupid.repository.FcmTokenRepository
import com.august.cupid.repository.UserNotificationSettingsRepository
import com.august.cupid.repository.ChannelNotificationSettingsRepository
import com.august.cupid.repository.UserRepository
import com.august.cupid.repository.ChannelRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 알림 서비스
 * FCM을 통한 푸시 알림 및 알림 설정 관리
 */
@Service
@Transactional
class NotificationService(
    private val fcmTokenRepository: FcmTokenRepository,
    private val userNotificationSettingsRepository: UserNotificationSettingsRepository,
    private val channelNotificationSettingsRepository: ChannelNotificationSettingsRepository,
    private val userRepository: UserRepository,
    private val channelRepository: ChannelRepository,
    private val firebaseMessaging: FirebaseMessaging
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * FCM 토큰 등록
     */
    fun registerFcmToken(userId: UUID, request: RegisterFcmTokenRequest): ApiResponse<String> {
        return try {
            // 사용자 존재 확인
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            val deviceType = DeviceType.valueOf(request.deviceType.uppercase())

            // 기존 토큰 확인
            val existingToken = fcmTokenRepository.findByToken(request.token)
            if (existingToken != null) {
                // 기존 토큰 삭제 후 새로 생성 (data class이므로 업데이트 불가)
                fcmTokenRepository.delete(existingToken)
            }
            
            // 새 토큰 생성
            val fcmToken = FcmToken(
                user = user,
                token = request.token,
                deviceType = deviceType,
                deviceName = request.deviceName,
                appVersion = request.appVersion,
                isActive = true,
                lastUsedAt = LocalDateTime.now()
            )
            fcmTokenRepository.save(fcmToken)

            logger.info("FCM 토큰 등록 완료: 사용자 ${user.username} (${userId})")

            ApiResponse(true, message = "FCM 토큰이 성공적으로 등록되었습니다")
        } catch (e: Exception) {
            logger.error("FCM 토큰 등록 실패: ${e.message}", e)
            ApiResponse(false, error = "FCM 토큰 등록 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자 전역 알림 설정 업데이트
     */
    fun updateUserNotificationSettings(userId: UUID, request: NotificationSettingsRequest): ApiResponse<String> {
        return try {
            // 사용자 존재 확인
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 기존 설정 조회 또는 생성
            var settings = userNotificationSettingsRepository.findByUserId(userId)
            if (settings == null) {
                settings = UserNotificationSettings(
                    user = user,
                    enabled = true,
                    soundEnabled = true,
                    vibrationEnabled = true,
                    showPreview = true,
                    dndEnabled = false,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            }

            // 설정 업데이트 (data class이므로 새 인스턴스 생성)
            val updatedSettings = settings.copy(
                enabled = request.enabled,
                soundEnabled = request.soundEnabled,
                vibrationEnabled = request.vibrationEnabled,
                showPreview = request.showPreview,
                dndEnabled = request.dndEnabled,
                dndStartTime = if (request.dndStartTime != null) LocalTime.parse(request.dndStartTime, DateTimeFormatter.ofPattern("HH:mm")) else settings.dndStartTime,
                dndEndTime = if (request.dndEndTime != null) LocalTime.parse(request.dndEndTime, DateTimeFormatter.ofPattern("HH:mm")) else settings.dndEndTime,
                dndDays = request.dndDays ?: settings.dndDays,
                updatedAt = LocalDateTime.now()
            )
            userNotificationSettingsRepository.save(updatedSettings)

            logger.info("사용자 알림 설정 업데이트 완료: ${user.username} (${userId})")

            ApiResponse(true, message = "알림 설정이 성공적으로 업데이트되었습니다")
        } catch (e: Exception) {
            logger.error("사용자 알림 설정 업데이트 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 알림 설정 업데이트 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널별 알림 설정 업데이트
     */
    fun updateChannelNotificationSettings(
        channelId: UUID, 
        userId: UUID, 
        enabled: Boolean,
        soundEnabled: Boolean = true,
        soundName: String = "default",
        vibrationEnabled: Boolean = true,
        vibrationPattern: List<Int> = listOf(0, 200, 100, 200),
        mutedUntil: LocalDateTime? = null
    ): ApiResponse<String> {
        return try {
            // 채널과 사용자 존재 확인
            val channel = channelRepository.findById(channelId).orElse(null)
            val user = userRepository.findById(userId).orElse(null)
            
            if (channel == null || user == null) {
                return ApiResponse(false, message = "채널 또는 사용자를 찾을 수 없습니다")
            }

            // 기존 설정 조회 또는 생성
            var settings = channelNotificationSettingsRepository.findByChannelIdAndUserId(channelId, userId)
            if (settings == null) {
                settings = ChannelNotificationSettings(
                    channel = channel,
                    user = user,
                    enabled = true,
                    soundEnabled = true,
                    soundName = "default",
                    vibrationEnabled = true,
                    vibrationPattern = listOf(0, 200, 100, 200),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            }

            // 설정 업데이트 (data class이므로 새 인스턴스 생성)
            val updatedSettings = settings.copy(
                enabled = enabled,
                soundEnabled = soundEnabled,
                soundName = soundName,
                vibrationEnabled = vibrationEnabled,
                vibrationPattern = vibrationPattern,
                mutedUntil = mutedUntil,
                updatedAt = LocalDateTime.now()
            )
            channelNotificationSettingsRepository.save(updatedSettings)

            logger.info("채널 알림 설정 업데이트 완료: 채널 ${channelId} -> 사용자 ${userId}")

            ApiResponse(true, message = "채널 알림 설정이 성공적으로 업데이트되었습니다")
        } catch (e: Exception) {
            logger.error("채널 알림 설정 업데이트 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 알림 설정 업데이트 중 오류가 발생했습니다")
        }
    }

    /**
     * 메시지 알림 전송
     */
    fun sendMessageNotification(
        channelId: UUID,
        senderId: UUID,
        messageContent: String,
        messageType: String
    ): ApiResponse<Int> {
        return try {
            // 채널 멤버들 조회
            val members = channelNotificationSettingsRepository.findEnabledSettingsByChannelId(channelId)
            val sender = userRepository.findById(senderId).orElse(null) ?: return ApiResponse(false, message = "발신자를 찾을 수 없습니다")

            var successCount = 0
            val currentTime = LocalDateTime.now()

            for (member in members) {
                // 발신자에게는 알림 전송하지 않음
                if (member.user.id == senderId) continue

                // 방해금지 모드 확인
                if (isInDndMode(member.user.id, currentTime)) continue

                // 음소거 확인
                if (member.mutedUntil != null && member.mutedUntil.isAfter(currentTime)) continue

                // 알림 전송
                val sent = sendNotificationToUser(
                    userId = member.user.id,
                    title = "새 메시지",
                    body = "${sender.username}: $messageContent",
                    data = mapOf(
                        "type" to "MESSAGE",
                        "channelId" to channelId.toString(),
                        "senderId" to senderId.toString(),
                        "messageType" to messageType
                    ),
                    soundEnabled = member.soundEnabled,
                    vibrationEnabled = member.vibrationEnabled
                )

                if (sent) successCount++
            }

            logger.info("메시지 알림 전송 완료: 채널 ${channelId}, 성공 ${successCount}개")

            ApiResponse(true, data = successCount, message = "$successCount 개의 알림이 전송되었습니다")
        } catch (e: Exception) {
            logger.error("메시지 알림 전송 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 알림 전송 중 오류가 발생했습니다")
        }
    }

    /**
     * 매칭 알림 전송
     */
    fun sendMatchNotification(userId: UUID, matchUserId: UUID): ApiResponse<Boolean> {
        return try {
            val user = userRepository.findById(matchUserId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            val sent = sendNotificationToUser(
                userId = userId,
                title = "새로운 매칭!",
                body = "${user.username}님과 매칭되었습니다!",
                data = mapOf(
                    "type" to "MATCH",
                    "matchUserId" to matchUserId.toString()
                )
            )

            ApiResponse(true, data = sent, message = if (sent) "매칭 알림이 전송되었습니다" else "알림 전송에 실패했습니다")
        } catch (e: Exception) {
            logger.error("매칭 알림 전송 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 알림 전송 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자에게 알림 전송 (내부 메서드)
     */
    private fun sendNotificationToUser(
        userId: UUID,
        title: String,
        body: String,
        data: Map<String, String>,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true
    ): Boolean {
        return try {
            // 사용자의 활성 FCM 토큰들 조회
            val tokens = fcmTokenRepository.findByUserIdAndIsActiveTrue(userId)
            if (tokens.isEmpty()) {
                logger.warn("사용자 ${userId}의 활성 FCM 토큰이 없습니다")
                return false
            }

            var successCount = 0
            for (token in tokens) {
                try {
                    val notification = Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()

                    val message = Message.builder()
                        .setToken(token.token)
                        .setNotification(notification)
                        .putAllData(data)
                        .build()

                    val response = firebaseMessaging.send(message)
                    logger.debug("FCM 알림 전송 성공: ${response}")
                    successCount++

                    // 토큰 마지막 사용 시간 업데이트
                    fcmTokenRepository.updateLastUsedAt(token.token, LocalDateTime.now())
                } catch (e: Exception) {
                    logger.error("FCM 토큰 ${token.token}으로 알림 전송 실패: ${e.message}")
                    // 실패한 토큰 비활성화
                    fcmTokenRepository.deactivateToken(token.token)
                }
            }

            successCount > 0
        } catch (e: Exception) {
            logger.error("사용자 ${userId}에게 알림 전송 실패: ${e.message}", e)
            false
        }
    }

    /**
     * 방해금지 모드 확인
     */
    private fun isInDndMode(userId: UUID, currentTime: LocalDateTime): Boolean {
        return try {
            val settings = userNotificationSettingsRepository.findByUserId(userId) ?: return false
            if (!settings.dndEnabled || !settings.enabled) return false

            val currentTimeOfDay = currentTime.toLocalTime()
            val dayOfWeek = currentTime.dayOfWeek.value

            // 방해금지 요일 확인
            if (!settings.dndDays.contains(dayOfWeek)) return false

            // 방해금지 시간 확인
            val startTime = settings.dndStartTime ?: return false
            val endTime = settings.dndEndTime ?: return false

            when {
                startTime <= endTime -> currentTimeOfDay >= startTime && currentTimeOfDay <= endTime
                else -> currentTimeOfDay >= startTime || currentTimeOfDay <= endTime
            }
        } catch (e: Exception) {
            logger.error("방해금지 모드 확인 실패: ${e.message}", e)
            false
        }
    }

    /**
     * 오래된 FCM 토큰 정리
     */
    fun cleanupOldTokens(): ApiResponse<Int> {
        return try {
            val threshold = LocalDateTime.now().minusDays(30)
            val deletedCount = fcmTokenRepository.deleteOldTokens(threshold)
            logger.info("오래된 FCM 토큰 정리 완료: $deletedCount 개")
            ApiResponse(true, data = deletedCount, message = "$deletedCount 개의 오래된 토큰이 정리되었습니다")
        } catch (e: Exception) {
            logger.error("오래된 FCM 토큰 정리 실패: ${e.message}", e)
            ApiResponse(false, error = "오래된 FCM 토큰 정리 중 오류가 발생했습니다")
        }
    }

    /**
     * 만료된 음소거 설정 해제
     */
    fun unmuteExpiredChannels(): ApiResponse<Int> {
        return try {
            val unmutedCount = channelNotificationSettingsRepository.unmuteExpiredChannels(LocalDateTime.now())
            logger.info("만료된 음소거 설정 해제 완료: $unmutedCount 개")
            ApiResponse(true, data = unmutedCount, message = "$unmutedCount 개의 음소거가 해제되었습니다")
        } catch (e: Exception) {
            logger.error("만료된 음소거 설정 해제 실패: ${e.message}", e)
            ApiResponse(false, error = "만료된 음소거 설정 해제 중 오류가 발생했습니다")
        }
    }

    /**
     * 알림 통계 조회
     */
    @Transactional(readOnly = true)
    fun getNotificationStatistics(): ApiResponse<Map<String, Any>> {
        return try {
            val enabledUsers = userNotificationSettingsRepository.countUsersWithEnabledNotifications()
            val dndUsers = userNotificationSettingsRepository.countUsersWithDndEnabled()
            val soundUsers = userNotificationSettingsRepository.countUsersWithSoundEnabled()
            val vibrationUsers = userNotificationSettingsRepository.countUsersWithVibrationEnabled()

            val activeTokens = fcmTokenRepository.countActiveTokensByUserId(UUID.randomUUID()) // 전체 개수는 별도 쿼리 필요
            val androidTokens = fcmTokenRepository.countActiveTokensByDeviceType(DeviceType.ANDROID)
            val iosTokens = fcmTokenRepository.countActiveTokensByDeviceType(DeviceType.IOS)
            val webTokens = fcmTokenRepository.countActiveTokensByDeviceType(DeviceType.WEB)

            val statistics = mapOf(
                "enabledUsers" to enabledUsers,
                "dndUsers" to dndUsers,
                "soundUsers" to soundUsers,
                "vibrationUsers" to vibrationUsers,
                "androidTokens" to androidTokens,
                "iosTokens" to iosTokens,
                "webTokens" to webTokens
            )

            ApiResponse(true, data = statistics)
        } catch (e: Exception) {
            logger.error("알림 통계 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "알림 통계 조회 중 오류가 발생했습니다")
        }
    }
}
