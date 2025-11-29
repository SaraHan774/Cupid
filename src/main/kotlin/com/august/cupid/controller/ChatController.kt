package com.august.cupid.controller

import com.august.cupid.model.dto.SendMessageRequest
import com.august.cupid.model.dto.MessageResponse
import com.august.cupid.model.dto.FileMetadataDto
import com.august.cupid.model.entity.ChannelType
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.UserRepository
import com.august.cupid.service.MessageService
import com.august.cupid.service.OnlineStatusService
import com.august.cupid.service.NotificationService
import com.august.cupid.service.EncryptionService
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
    private val userRepository: UserRepository,
    private val encryptionService: EncryptionService
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
     * 메시지 전송 핸들러 (E2E 암호화 통합)
     * 클라이언트가 /app/send로 메시지를 보내면 채널 멤버들에게 암호화하여 브로드캐스트
     * 
     * 플로우:
     * 1. 사용자 ID 추출 및 검증
     * 2. 채널 멤버 조회
     * 3. 각 수신자별로 세션 확인 및 자동 초기화
     * 4. 메시지 암호화 (평문인 경우)
     * 5. 메시지 저장 (암호화된 내용)
     * 6. 온라인 사용자에게 WebSocket으로 브로드캐스트
     * 7. 오프라인 사용자는 FCM으로 전송
     * 
     * 암호화:
     * - Signal Protocol E2E 암호화 사용
     * - 세션이 없으면 자동으로 초기화
     * - 암호화 오류 시 사용자에게 피드백 제공
     */
    @MessageMapping("/send")
    fun handleMessage(
        @Payload request: SendMessageRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("메시지 전송 실패: 사용자 ID를 추출할 수 없습니다")
                sendErrorToUser(headerAccessor, "인증 오류: 사용자 ID를 확인할 수 없습니다")
                return
            }

            // 2. channelId 검증
            if (request.channelId == null) {
                logger.warn("메시지 전송 실패: channelId가 null입니다")
                sendErrorToUser(headerAccessor, "채널 ID가 필요합니다")
                return
            }

            logger.info("메시지 전송 요청: userId={}, channelId={}", userId, request.channelId)

            // 3. 채널 멤버 조회
            val members = channelMembersRepository.findByChannelIdAndIsActiveTrue(request.channelId)
            if (members.isEmpty()) {
                logger.warn("채널 멤버를 찾을 수 없음: channelId={}", request.channelId)
                sendErrorToUser(headerAccessor, "채널 멤버를 찾을 수 없습니다")
                return
            }

            // 4. 암호화 처리 (각 수신자별로)
            val encryptedContents = mutableMapOf<UUID, String>()
            val encryptionErrors = mutableListOf<String>()

            members.forEach { member ->
                // 발신자는 제외
                if (member.user.id == userId) return@forEach

                val recipientId = member.user.id

                try {
                    // 4-1. 세션 확인 및 자동 초기화
                    ensureSession(userId, recipientId)

                    // 4-2. 메시지 암호화
                    // encryptedContent가 이미 암호화된 경우와 평문인 경우를 구분
                    val plaintext = if (request.encryptedContent.startsWith("ENCRYPTED:")) {
                        // 이미 암호화된 내용인 경우 (클라이언트 측 암호화)
                        request.encryptedContent.removePrefix("ENCRYPTED:")
                    } else {
                        // 평문인 경우 서버에서 암호화
                        request.encryptedContent
                    }

                    val encryptedMessage = encryptionService.encryptMessage(
                        senderId = userId,
                        recipientId = recipientId,
                        plaintext = plaintext
                    )

                    encryptedContents[recipientId] = encryptedMessage.encryptedContent
                    logger.debug("메시지 암호화 완료: sender={}, recipient={}", userId, recipientId)

                } catch (e: IllegalStateException) {
                    // 세션 초기화 실패
                    logger.error("세션 초기화 실패: sender={}, recipient={}", userId, recipientId, e)
                    encryptionErrors.add("수신자 ${recipientId}와의 세션을 초기화할 수 없습니다")
                } catch (e: Exception) {
                    // 암호화 실패
                    logger.error("메시지 암호화 실패: sender={}, recipient={}", userId, recipientId, e)
                    encryptionErrors.add("수신자 ${recipientId}에게 메시지를 암호화할 수 없습니다")
                }
            }

            // 5. 암호화 오류가 있으면 사용자에게 알림
            if (encryptionErrors.isNotEmpty()) {
                logger.warn("일부 수신자에게 암호화 실패: errors={}", encryptionErrors)
                sendErrorToUser(headerAccessor, "일부 수신자에게 메시지를 암호화할 수 없습니다: ${encryptionErrors.joinToString(", ")}")
                // 부분 실패해도 성공한 수신자에게는 전송 계속
            }

            // 6. 메시지 저장 (첫 번째 수신자의 암호화된 내용 사용, 또는 원본 사용)
            val contentToStore = encryptedContents.values.firstOrNull() ?: request.encryptedContent
            val messageRequest = SendMessageRequest(
                channelId = request.channelId,
                encryptedContent = contentToStore,
                messageType = request.messageType,
                fileMetadata = request.fileMetadata,
                replyToMessageId = request.replyToMessageId
            )

            val savedMessage = messageService.sendMessage(messageRequest, userId)
            logger.info("메시지 저장 완료: messageId={}", savedMessage.id)

            // 7. 암호화된 메시지를 각 수신자에게 브로드캐스트
            var broadcastCount = 0
            var encryptedBroadcastCount = 0

            members.forEach { member ->
                // 발신자는 제외
                if (member.user.id == userId) return@forEach

                val recipientId = member.user.id

                // 각 수신자별로 암호화된 내용 전송
                val recipientEncryptedContent = encryptedContents[recipientId]
                if (recipientEncryptedContent != null) {
                    // 암호화된 메시지 Response 객체 생성 (copy 사용)
                    val encryptedMessageResponse = savedMessage.copy(
                        encryptedContent = recipientEncryptedContent
                    )

                    // User destination으로 브로드캐스트
                    try {
                        messagingTemplate.convertAndSendToUser(
                            recipientId.toString(),
                            "/queue/messages",
                            encryptedMessageResponse
                        )
                        encryptedBroadcastCount++
                        logger.debug("암호화된 메시지 브로드캐스트: recipientId={}", recipientId)
                    } catch (e: Exception) {
                        logger.error("암호화된 메시지 브로드캐스트 실패: recipientId={}", recipientId, e)
                    }
                } else {
                    // 암호화 실패한 경우 일반 메시지 전송 (폴백)
                    logger.warn("암호화 실패로 일반 메시지 전송: recipientId={}", recipientId)
                    try {
                        messagingTemplate.convertAndSendToUser(
                            recipientId.toString(),
                            "/queue/messages",
                            savedMessage
                        )
                        broadcastCount++
                    } catch (e: Exception) {
                        logger.error("일반 메시지 브로드캐스트 실패: recipientId={}", recipientId, e)
                    }
                }

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

            // 8. 채널 topic으로 브로드캐스트 (선택적 - 암호화된 내용은 제외)
            // 주의: 채널 topic은 모든 구독자에게 동일한 메시지를 전송하므로
            // 개별 암호화된 메시지는 User destination을 통해 전송
            try {
                // 메타데이터만 브로드캐스트 (암호화된 내용은 제외)
                val metadataMessage = mapOf(
                    "id" to savedMessage.id.toString(),
                    "channelId" to savedMessage.channelId.toString(),
                    "senderId" to savedMessage.senderId.toString(),
                    "messageType" to savedMessage.messageType,
                    "status" to savedMessage.status,
                    "createdAt" to savedMessage.createdAt.toString(),
                    "isEncrypted" to true
                )
                messagingTemplate.convertAndSend(
                    "/topic/channel/${request.channelId}",
                    metadataMessage
                )
                logger.debug("채널 메타데이터 브로드캐스트 완료: channelId={}", request.channelId)
            } catch (e: Exception) {
                logger.error("채널 메타데이터 브로드캐스트 실패: channelId={}", request.channelId, e)
            }

            logger.info("메시지 전송 완료: messageId={}, 암호화된 브로드캐스트={}, 일반 브로드캐스트={}",
                savedMessage.id, encryptedBroadcastCount, broadcastCount)

            // 9. 성공 응답 전송
            sendSuccessToUser(headerAccessor, savedMessage.id.toString())

        } catch (e: Exception) {
            logger.error("메시지 전송 중 오류 발생", e)
            sendErrorToUser(headerAccessor, "메시지 전송 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * 세션 확인 및 자동 초기화
     * 수신자와의 세션이 없으면 자동으로 초기화
     */
    private fun ensureSession(senderId: UUID, recipientId: UUID) {
        // 세션 존재 확인
        if (!encryptionService.hasSession(senderId, recipientId)) {
            logger.info("세션이 없어 자동 초기화: sender={}, recipient={}", senderId, recipientId)
            
            try {
                // 수신자의 PreKeyBundle 조회
                val preKeyBundle = encryptionService.getPreKeyBundle(recipientId, deviceId = 1)
                
                // 세션 초기화
                val sessionEstablished = encryptionService.initializeSession(
                    senderId,
                    recipientId,
                    preKeyBundle
                )
                
                if (!sessionEstablished) {
                    throw IllegalStateException("세션 초기화에 실패했습니다")
                }
                
                logger.info("세션 자동 초기화 완료: sender={}, recipient={}", senderId, recipientId)
            } catch (e: NoSuchElementException) {
                logger.error("수신자의 키를 찾을 수 없음: recipientId={}", recipientId, e)
                throw IllegalStateException("수신자의 암호화 키가 등록되지 않았습니다")
            } catch (e: Exception) {
                logger.error("세션 초기화 실패: sender={}, recipient={}", senderId, recipientId, e)
                throw IllegalStateException("세션 초기화 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    /**
     * 사용자에게 오류 메시지 전송
     */
    private fun sendErrorToUser(headerAccessor: SimpMessageHeaderAccessor, errorMessage: String) {
        try {
            val userId = extractUserId(headerAccessor)
            if (userId != null) {
                val errorResponse = mapOf(
                    "success" to false,
                    "error" to errorMessage,
                    "timestamp" to System.currentTimeMillis()
                )
                messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/messages/error",
                    errorResponse
                )
            }
        } catch (e: Exception) {
            logger.error("오류 메시지 전송 실패", e)
        }
    }

    /**
     * 사용자에게 성공 응답 전송
     */
    private fun sendSuccessToUser(headerAccessor: SimpMessageHeaderAccessor, messageId: String) {
        try {
            val userId = extractUserId(headerAccessor)
            if (userId != null) {
                val successResponse = mapOf(
                    "success" to true,
                    "messageId" to messageId,
                    "timestamp" to System.currentTimeMillis()
                )
                messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/messages/success",
                    successResponse
                )
            }
        } catch (e: Exception) {
            logger.error("성공 응답 전송 실패", e)
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
