package com.august.cupid.websocket

import com.august.cupid.service.OnlineStatusService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent

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
    private val messageHandler: WebSocketMessageHandler
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
            val userId = headerAccessor.sessionAttributes?.get("userId") as? String

            if (userId != null) {
                logger.info("WebSocket 연결됨: userId={}, sessionId={}", userId, sessionId)
                
                // 연결 상태 알림 (선택사항)
                // 다른 사용자들에게 온라인 상태 변경 알림을 보낼 수 있음
                notifyUserStatusChange(userId, true)
            } else {
                logger.warn("WebSocket 연결됨: 사용자 ID가 없습니다, sessionId={}", sessionId)
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
            val userId = headerAccessor.sessionAttributes?.get("userId") as? String

            if (userId != null) {
                logger.info("WebSocket 연결 해제됨: userId={}, sessionId={}", userId, sessionId)
                
                // 사용자 온라인 상태 제거
                messageHandler.handleDisconnection(userId)
                
                // 연결 해제 상태 알림 (선택사항)
                notifyUserStatusChange(userId, false)
            } else {
                logger.warn("WebSocket 연결 해제됨: 사용자 ID가 없습니다, sessionId={}", sessionId)
            }

        } catch (e: Exception) {
            logger.error("WebSocket 연결 해제 이벤트 처리 중 오류 발생", e)
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
            val statusEvent = UserStatusEvent(
                userId = userId,
                isOnline = isOnline,
                timestamp = System.currentTimeMillis()
            )

            // 모든 사용자에게 상태 변경 알림
            // 실제 구현에서는 해당 사용자와 관련된 사용자들에게만 알림을 보낼 수 있음
            // 예: 같은 채널의 사용자들, 친구 목록 등
            
            logger.debug("사용자 상태 변경 알림: userId={}, isOnline={}", userId, isOnline)

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
