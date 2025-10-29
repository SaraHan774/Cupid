package com.august.cupid.controller

import com.august.cupid.model.dto.SendMessageRequest
import com.august.cupid.model.entity.ChannelType
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.UserRepository
import com.august.cupid.service.MessageService
import com.august.cupid.service.OnlineStatusService
import com.august.cupid.service.NotificationService
import com.august.cupid.websocket.ConnectionInterceptor
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.util.*

/**
 * WebSocket Chat Controller
 * 실시간 메시지 전송 및 브로드캐스트
 */
@Controller
class ChatController(
    private val messageService: MessageService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val onlineStatusService: OnlineStatusService,
    private val notificationService: NotificationService,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Echo 메시지 핸들러 (테스트용)
     * 클라이언트가 /app/echo로 메시지를 보내면 /topic/echo로 에코
     */
    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    fun echo(message: String): String {
        logger.info("Received echo message: $message")
        return "Echo: $message"
    }

    /**
     * 메시지 전송 핸들러
     * 클라이언트가 /app/send로 메시지를 보내면 채널 멤버들에게 브로드캐스트
     * 
     * 플로우:
     * 1. 메시지 저장 (MessageService)
     * 2. 채널 멤버 조회
     * 3. 온라인 사용자에게 WebSocket으로 브로드캐스트
     * 4. 오프라인 사용자는 FCM으로 전송 (다음 Priority)
     */
    @MessageMapping("/send")
    fun handleMessage(
        @Payload request: SendMessageRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("메시지 전송 실패: 사용자 ID를 추출할 수 없습니다")
                return
            }

            // channelId 검증
            if (request.channelId == null) {
                logger.warn("메시지 전송 실패: channelId가 null입니다")
                return
            }

            logger.info("메시지 전송 요청: userId={}, channelId={}", userId, request.channelId)

            // 메시지 저장
            val result = messageService.sendMessage(request, userId)
            
            if (!result.success || result.data == null) {
                logger.error("메시지 저장 실패: ${result.message}")
                return
            }

            val savedMessage = result.data
            logger.info("메시지 저장 완료: messageId={}", savedMessage.id)

            // 채널 멤버 조회
            val members = channelMembersRepository.findByChannelIdAndIsActiveTrue(request.channelId)
            
            if (members.isEmpty()) {
                logger.warn("채널 멤버를 찾을 수 없음: channelId={}", request.channelId)
                return
            }

            // 채널 topic으로 브로드캐스트 (모든 구독자에게 전달)
            try {
                messagingTemplate.convertAndSend(
                    "/topic/channel/${request.channelId}",
                    savedMessage
                )
                logger.debug("채널 topic 브로드캐스트 완료: channelId={}", request.channelId)
            } catch (e: Exception) {
                logger.error("채널 topic 브로드캐스트 실패: channelId={}", request.channelId, e)
            }

            // 모든 멤버에게 브로드캐스트 (발신자 제외)
            // SimpleBroker가 연결된 세션에만 전달함
            var broadcastCount = 0

            members.forEach { member ->
                // 발신자는 제외
                if (member.user.id == userId) return@forEach

                // 두 가지 방식으로 브로드캐스트
                // 1. User destination (원래 방식)
                try {
                    messagingTemplate.convertAndSendToUser(
                        member.user.id.toString(),
                        "/queue/messages",
                        savedMessage
                    )
                    logger.debug("User destination 브로드캐스트: userId={}", member.user.id)
                } catch (e: Exception) {
                    logger.error("User destination 브로드캐스트 실패: userId={}", member.user.id, e)
                }

                broadcastCount++

                // 오프라인 사용자인 경우 FCM 알림 추가 전송
                val isOnline = onlineStatusService.isUserOnline(member.user.id.toString())
                if (!isOnline) {
                    logger.debug("오프라인 사용자 FCM 전송: userId={}", member.user.id)
                    
                    try {
                        val notificationResult = notificationService.sendMessageNotification(
                            channelId = request.channelId,
                            senderId = userId,
                            messageContent = request.encryptedContent.substring(0, minOf(100, request.encryptedContent.length)),
                            messageType = request.messageType
                        )
                        
                        if (notificationResult.success) {
                            logger.info("FCM 알림 전송 성공: userId={}", member.user.id)
                        } else {
                            logger.warn("FCM 알림 전송 실패: userId={}, reason={}", 
                                member.user.id, notificationResult.message)
                        }
                    } catch (e: Exception) {
                        logger.error("FCM 알림 전송 중 오류: userId={}", member.user.id, e)
                    }
                }
            }

            logger.info("메시지 전송 완료: messageId={}, 브로드캐스트={}",
                savedMessage.id, broadcastCount)

        } catch (e: Exception) {
            logger.error("메시지 전송 중 오류 발생", e)
        }
    }

    /**
     * 사용자 ID 추출
     * WebSocket 세션에서 사용자 ID를 추출
     */
    private fun extractUserId(headerAccessor: SimpMessageHeaderAccessor): UUID? {
        return try {
            // 1. Principal에서 추출 시도
            val principal = headerAccessor.user
            if (principal != null && principal.name != null) {
                UUID.fromString(principal.name)
            } else {
                // 2. 세션 속성에서 추출 시도
                val sessionAttributes = headerAccessor.sessionAttributes
                val userIdString = sessionAttributes?.get("userId") as? String
                if (userIdString != null) {
                    UUID.fromString(userIdString)
                } else {
                    logger.warn("사용자 ID를 추출할 수 없습니다. principal={}, sessionAttributes={}", 
                        principal, sessionAttributes)
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("사용자 ID 추출 실패", e)
            null
        }
    }
}
