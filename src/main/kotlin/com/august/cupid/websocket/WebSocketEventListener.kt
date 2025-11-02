package com.august.cupid.websocket

import com.august.cupid.service.OnlineStatusService
import com.august.cupid.service.EncryptionService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.*

/**
 * WebSocket 연결 이벤트 리스너
 * 
 * 기능:
 * 1. WebSocket 연결 시 이벤트 처리
 * 2. WebSocket 연결 해제 시 이벤트 처리
 * 3. 사용자 온라인 상태 관리
 */
@Component
class WebSocketEventListener(
    private val onlineStatusService: OnlineStatusService,
    private val messageHandler: WebSocketMessageHandler,
    private val encryptionService: EncryptionService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * WebSocket 연결 이벤트 처리
     * 사용자가 WebSocket에 연결되었을 때 호출
     * 
     * @param event 연결 이벤트
     */
    @EventListener
    fun handleWebSocketConnectListener(event: SessionConnectedEvent) {
        try {
            val headerAccessor = StompHeaderAccessor.wrap(event.message)
            val sessionId = headerAccessor.sessionId
            
            // 쿼리 파라미터에서 사용자 ID 추출
            val userId = extractUserIdFromQuery(headerAccessor)

            if (userId != null && sessionId != null) {
                logger.info("WebSocket 연결됨: userId={}, sessionId={}", userId, sessionId)
                
                // 사용자 온라인 상태 설정
                onlineStatusService.setUserOnline(userId, sessionId)
                
                // Task 3: 사용자가 처음 연결할 때 자동 세션 초기화 (백그라운드에서 비동기 처리)
                // 주의: 모든 사용자와의 세션을 미리 초기화하는 것은 비효율적이므로
                // 실제로는 첫 메시지 전송 시 초기화하는 것이 더 적절함
                // 여기서는 사용자가 키를 생성했는지만 확인
                try {
                    val userIdUuid = UUID.fromString(userId)
                    val keyStatus = encryptionService.getKeyStatus(userIdUuid)
                    if (keyStatus.hasIdentityKey) {
                        logger.debug("사용자 암호화 키 확인됨: userId={}", userId)
                        // 키가 있으면 첫 메시지 전송 시 자동으로 세션이 초기화됨
                    } else {
                        logger.debug("사용자 암호화 키가 없음: userId={}", userId)
                        // 키가 없으면 클라이언트에게 알림 필요 (별도 처리)
                    }
                } catch (e: Exception) {
                    logger.debug("암호화 키 상태 확인 실패 (무시): userId={}", userId, e)
                    // 키 상태 확인 실패는 치명적이지 않음
                }
                
                // 연결 상태 알림 (선택사항)
                // 다른 사용자들에게 온라인 상태 변경 알림을 보낼 수 있음
                notifyUserStatusChange(userId, true)
            } else {
                logger.warn("WebSocket 연결됨: 사용자 ID 또는 세션 ID를 추출할 수 없습니다, userId={}, sessionId={}", userId, sessionId)
            }

        } catch (e: Exception) {
            logger.error("WebSocket 연결 이벤트 처리 중 오류 발생", e)
        }
    }

    /**
     * WebSocket 연결 해제 이벤트 처리
     * 사용자가 WebSocket 연결을 해제했을 때 호출
     * 
     * @param event 연결 해제 이벤트
     */
    @EventListener
    fun handleWebSocketDisconnectListener(event: SessionDisconnectEvent) {
        try {
            val headerAccessor = StompHeaderAccessor.wrap(event.message)
            val sessionId = headerAccessor.sessionId
            
            // 쿼리 파라미터에서 사용자 ID 추출
            val userId = extractUserIdFromQuery(headerAccessor)

            if (userId != null) {
                logger.info("WebSocket 연결 해제됨: userId={}, sessionId={}", userId, sessionId)
                
                // 사용자 온라인 상태 제거
                onlineStatusService.setUserOffline(userId)
                messageHandler.handleDisconnection(userId)
                
                // 연결 해제 상태 알림 (선택사항)
                notifyUserStatusChange(userId, false)
            } else {
                logger.warn("WebSocket 연결 해제됨: 사용자 ID를 추출할 수 없습니다, sessionId={}", sessionId)
            }

        } catch (e: Exception) {
            logger.error("WebSocket 연결 해제 이벤트 처리 중 오류 발생", e)
        }
    }

    /**
     * 사용자 ID 추출
     * StompChannelInterceptor에서 설정한 Principal에서 사용자 ID 추출
     *
     * @param headerAccessor STOMP 헤더 접근자
     * @return 사용자 ID 또는 null
     */
    private fun extractUserIdFromQuery(headerAccessor: StompHeaderAccessor): String? {
        return try {
            // Principal (user)에서 사용자 ID 추출
            val user = headerAccessor.user
            val userId = user?.name

            if (userId != null) {
                logger.debug("사용자 ID 추출 성공: userId={}", userId)
            } else {
                // Fallback: 세션 속성에서 추출 시도
                val sessionAttributes = headerAccessor.sessionAttributes
                val fallbackUserId = sessionAttributes?.get("userId") as? String

                if (fallbackUserId != null) {
                    logger.debug("세션 속성에서 사용자 ID 추출 성공: userId={}", fallbackUserId)
                    return fallbackUserId
                }

                logger.warn("사용자 ID를 추출할 수 없습니다. user={}, sessionAttributes={}", user, sessionAttributes)
            }

            userId
        } catch (e: Exception) {
            logger.error("사용자 ID 추출 실패", e)
            null
        }
    }

    /**
     * 사용자 상태 변경 알림
     * 다른 사용자들에게 온라인/오프라인 상태 변경을 알림
     * 
     * @param userId 상태가 변경된 사용자 ID
     * @param isOnline 온라인 여부
     */
    private fun notifyUserStatusChange(userId: String, isOnline: Boolean) {
        try {
            // 현재는 로그만 출력 (실제 구현에서는 WebSocket으로 알림 전송)
            logger.debug("사용자 상태 변경: userId={}, isOnline={}", userId, isOnline)
            
            // 향후 구현: 실제 상태 변경 알림 전송
            // val statusEvent = UserStatusEvent(
            //     userId = userId,
            //     isOnline = isOnline,
            //     timestamp = System.currentTimeMillis()
            // )
            // messagingTemplate.convertAndSend("/topic/user.status", statusEvent)

        } catch (e: Exception) {
            logger.error("사용자 상태 변경 알림 처리 중 오류 발생: userId={}", userId, e)
        }
    }

    /**
     * 사용자 상태 이벤트 데이터 클래스
     */
    data class UserStatusEvent(
        val userId: String,
        val isOnline: Boolean,
        val timestamp: Long
    )
}
