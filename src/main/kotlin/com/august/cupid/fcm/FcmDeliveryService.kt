package com.august.cupid.fcm

import com.august.cupid.model.entity.notification.FcmToken
import com.august.cupid.model.entity.notification.DeviceType
import com.august.cupid.model.entity.User
import com.august.cupid.repository.FcmTokenRepository
import com.august.cupid.repository.UserRepository
import com.google.firebase.messaging.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * FCM 전송 서비스
 * Silent Push 메시지 전송 및 토큰 관리
 */
@Service
@Transactional
class FcmDeliveryService(
    private val fcmTokenRepository: FcmTokenRepository,
    private val userRepository: UserRepository,
    private val firebaseMessaging: FirebaseMessaging,
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Redis 키 패턴
        private const val FCM_TOKEN_CACHE_PREFIX = "fcm_token:"
        private const val FCM_TOKEN_CACHE_TTL_HOURS = 1L
        
        // iOS 설정
        private const val APNS_PRIORITY_HIGH = "10"
        private const val APNS_PRIORITY_LOW = "5"
    }

    /**
     * Silent Push 메시지 전송
     * 
     * @param userId 수신자 ID
     * @param type 메시지 타입 ("new_message", "match")
     * @param channelId 채널 ID (new_message인 경우)
     * @param senderId 발신자 ID (new_message인 경우)
     * @param encryptedContent 암호화된 메시지 내용
     * @return 성공 여부
     */
    fun sendSilentPush(
        userId: UUID,
        type: String,
        channelId: UUID?,
        senderId: UUID?,
        encryptedContent: String
    ): Boolean {
        return try {
            logger.info("FCM Silent Push 전송 시작: userId={}, type={}", userId, type)
            
            // 사용자의 활성 FCM 토큰들 조회
            val tokens = getActiveFcmTokens(userId)
            
            if (tokens.isEmpty()) {
                logger.warn("사용자 {}의 활성 FCM 토큰이 없습니다", userId)
                return false
            }

            var successCount = 0

            // 각 토큰에 대해 전송
            tokens.forEach { token ->
                try {
                    val message = buildSilentPushMessage(token, type, channelId, senderId, encryptedContent)
                    val response = firebaseMessaging.send(message)
                    
                    logger.debug("FCM 전송 성공: tokenId={}, messageId={}", token.id, response)
                    successCount++
                    
                    // 토큰 마지막 사용 시간 업데이트
                    updateTokenLastUsed(token)
                    
                } catch (e: FirebaseMessagingException) {
                    logger.error("FCM 전송 실패: tokenId={}, error={}", token.id, e.errorCode)
                    
                    // 전송 실패 처리
                    handleSendFailure(token, e)
                }
            }

            val success = successCount > 0
            logger.info("FCM Silent Push 전송 완료: userId={}, 성공={}/{}", userId, successCount, tokens.size)
            
            success

        } catch (e: Exception) {
            logger.error("FCM Silent Push 전송 중 오류 발생: userId={}", userId, e)
            false
        }
    }

    /**
     * Silent Push 메시지 빌드
     */
    private fun buildSilentPushMessage(
        token: FcmToken,
        type: String,
        channelId: UUID?,
        senderId: UUID?,
        encryptedContent: String
    ): Message {
        // 공통 데이터 페이로드
        val dataMap = mutableMapOf<String, String>(
            "type" to type
        )
        
        // 타입별 데이터 추가
        when (type) {
            "new_message" -> {
                channelId?.let { dataMap["channel_id"] = it.toString() }
                senderId?.let { dataMap["sender_id"] = it.toString() }
                dataMap["encrypted_content"] = encryptedContent
            }
            "match" -> {
                senderId?.let { dataMap["match_user_id"] = it.toString() }
            }
        }

        // 메시지 빌더 생성
        val messageBuilder = Message.builder()
            .setToken(token.token)
            .putAllData(dataMap)

        // 디바이스 타입별 설정
        when (token.deviceType) {
            DeviceType.IOS -> {
                messageBuilder.setApnsConfig(
                    ApnsConfig.builder()
                        .putHeader("apns-priority", APNS_PRIORITY_HIGH)
                        .putHeader("apns-push-type", "background")
                        .setAps(
                            Aps.builder()
                                .setContentAvailable(true)
                                .setMutableContent(true)
                                .build()
                        )
                        .build()
                )
            }
            DeviceType.ANDROID -> {
                messageBuilder.setAndroidConfig(
                    AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build()
                )
            }
            DeviceType.WEB -> {
                // Web은 기본 설정 사용
            }
        }

        return messageBuilder.build()
    }

    /**
     * 활성 FCM 토큰 조회 (Redis 캐싱)
     */
    private fun getActiveFcmTokens(userId: UUID): List<FcmToken> {
        return try {
            // Redis 캐시 확인
            val cacheKey = "$FCM_TOKEN_CACHE_PREFIX$userId"
            val cachedTokens = redisTemplate.opsForValue().get(cacheKey)
            
            if (cachedTokens != null) {
                logger.debug("FCM 토큰 캐시 히트: userId={}", userId)
                // TODO: 캐시된 토큰을 파싱하여 반환
                // 현재는 캐시 구조가 단순하므로 DB 조회
            }
            
            // DB에서 조회
            val user = userRepository.findById(userId).orElse(null) ?: return emptyList()
            val tokens = fcmTokenRepository.findByUserAndIsActiveTrue(user)
            
            tokens

        } catch (e: Exception) {
            logger.error("FCM 토큰 조회 실패: userId={}", userId, e)
            emptyList()
        }
    }

    /**
     * 토큰 마지막 사용 시간 업데이트
     */
    private fun updateTokenLastUsed(token: FcmToken) {
        try {
            fcmTokenRepository.updateLastUsedAt(token.token, LocalDateTime.now())
            logger.debug("FCM 토큰 마지막 사용 시간 업데이트: tokenId={}", token.id)
        } catch (e: Exception) {
            logger.error("FCM 토큰 업데이트 실패: tokenId={}", token.id, e)
        }
    }

    /**
     * 전송 실패 처리
     * @param token 실패한 토큰
     * @param exception Firebase 예외
     */
    private fun handleSendFailure(token: FcmToken, exception: FirebaseMessagingException) {
        try {
            val errorCode = exception.errorCode.toString()
            when (errorCode) {
                "invalid-registration-token",
                "unregistered",
                "invalid-argument" -> {
                    // 토큰 비활성화
                    fcmTokenRepository.deactivateToken(token.token)
                    logger.info("무효한 FCM 토큰 비활성화: tokenId={}", token.id)
                }
                "quota-exceeded" -> {
                    logger.warn("FCM 할당량 초과")
                }
                "unavailable",
                "internal" -> {
                    logger.warn("FCM 서버 일시적 오류: {}", exception.message)
                }
                else -> {
                    logger.warn("FCM 전송 실패 (알 수 없는 오류): {}", errorCode)
                }
            }
        } catch (e: Exception) {
            logger.error("전송 실패 처리 중 오류 발생: tokenId={}", token.id, e)
        }
    }


    /**
     * 배치 전송
     * 여러 사용자에게 동일한 메시지 전송
     */
    fun sendBatchSilentPush(
        userIds: List<UUID>,
        type: String,
        channelId: UUID?,
        senderId: UUID?,
        encryptedContent: String
    ): Map<UUID, Boolean> {
        val results = mutableMapOf<UUID, Boolean>()
        
        userIds.forEach { userId ->
            val success = sendSilentPush(userId, type, channelId, senderId, encryptedContent)
            results[userId] = success
        }
        
        logger.info("FCM 배치 전송 완료: {}명 중 {}명 성공", userIds.size, results.count { it.value })
        
        return results
    }
}

