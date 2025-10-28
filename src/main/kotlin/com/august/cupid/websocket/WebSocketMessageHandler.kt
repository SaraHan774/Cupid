package com.august.cupid.websocket

import com.august.cupid.service.OnlineStatusService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket 메시지 핸들러
 * 
 * 기능:
 * 1. 하트비트 처리
 * 2. 타이핑 인디케이터 처리
 * 3. 읽음 표시 처리
 * 4. 연결 상태 모니터링
 */
@Controller
class WebSocketMessageHandler(
    private val onlineStatusService: OnlineStatusService,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 사용자별 마지막 하트비트 시간 추적
    private val lastHeartbeatTime = ConcurrentHashMap<String, Long>()

    companion object {
        // 하트비트 간격 (밀리초)
        private const val HEARTBEAT_INTERVAL_MS = 30000L  // 30초
        
        // 하트비트 타임아웃 (밀리초)
        private const val HEARTBEAT_TIMEOUT_MS = 60000L  // 60초
    }

    /**
     * 하트비트 메시지 처리
     * 클라이언트가 주기적으로 보내는 하트비트를 받아 연결 상태 갱신
     * 
     * @param heartbeat 하트비트 메시지
     * @param headerAccessor WebSocket 세션 정보
     */
    @MessageMapping("/heartbeat")
    fun handleHeartbeat(
        @Payload heartbeat: HeartbeatMessage,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            val userId = headerAccessor.sessionAttributes?.get("userId") as? String
            if (userId == null) {
                logger.warn("하트비트 처리 실패: 사용자 ID가 없습니다")
                return
            }

            // 현재 시간 기록
            val currentTime = System.currentTimeMillis()
            lastHeartbeatTime[userId] = currentTime

            // 온라인 상태 갱신
            onlineStatusService.processHeartbeat(userId)

            // 하트비트 응답 전송
            val response = HeartbeatResponse(
                timestamp = currentTime,
                serverTime = currentTime,
                status = "ok"
            )

            messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/heartbeat",
                response
            )

            logger.debug("하트비트 처리 완료: userId={}, timestamp={}", userId, currentTime)

        } catch (e: Exception) {
            logger.error("하트비트 처리 중 오류 발생", e)
        }
    }

    /**
     * 타이핑 인디케이터 처리
     * 사용자가 메시지를 입력 중임을 다른 사용자들에게 알림
     * 
     * @param typing 타이핑 메시지
     * @param headerAccessor WebSocket 세션 정보
     */
    @MessageMapping("/typing")
    fun handleTyping(
        @Payload typing: TypingMessage,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            val userId = headerAccessor.sessionAttributes?.get("userId") as? String
            if (userId == null) {
                logger.warn("타이핑 처리 실패: 사용자 ID가 없습니다")
                return
            }

            // 채널의 다른 사용자들에게 타이핑 상태 전송
            val typingEvent = TypingEvent(
                userId = userId,
                channelId = typing.channelId,
                isTyping = typing.isTyping,
                timestamp = System.currentTimeMillis()
            )

            messagingTemplate.convertAndSend(
                "/topic/channel.${typing.channelId}.typing",
                typingEvent
            )

            logger.debug("타이핑 인디케이터 처리 완료: userId={}, channelId={}, isTyping={}", 
                userId, typing.channelId, typing.isTyping)

        } catch (e: Exception) {
            logger.error("타이핑 처리 중 오류 발생", e)
        }
    }

    /**
     * 읽음 표시 처리
     * 메시지를 읽었음을 다른 사용자들에게 알림
     * 
     * @param readReceipt 읽음 표시 메시지
     * @param headerAccessor WebSocket 세션 정보
     */
    @MessageMapping("/read")
    fun handleReadReceipt(
        @Payload readReceipt: ReadReceiptMessage,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            val userId = headerAccessor.sessionAttributes?.get("userId") as? String
            if (userId == null) {
                logger.warn("읽음 표시 처리 실패: 사용자 ID가 없습니다")
                return
            }

            val readEvent = ReadReceiptEvent(
                userId = userId,
                messageId = readReceipt.messageId,
                channelId = readReceipt.channelId,
                readAt = System.currentTimeMillis()
            )

            // 채널의 다른 사용자들에게 읽음 표시 전송
            messagingTemplate.convertAndSend(
                "/topic/channel.${readReceipt.channelId}.read",
                readEvent
            )

            logger.debug("읽음 표시 처리 완료: userId={}, messageId={}, channelId={}", 
                userId, readReceipt.messageId, readReceipt.channelId)

        } catch (e: Exception) {
            logger.error("읽음 표시 처리 중 오류 발생", e)
        }
    }

    /**
     * 연결 상태 확인
     * 주기적으로 실행되어 하트비트 타임아웃된 사용자들을 오프라인 처리
     */
    fun checkConnectionStatus() {
        try {
            val currentTime = System.currentTimeMillis()
            val timeoutUsers = mutableListOf<String>()

            // 타임아웃된 사용자 찾기
            lastHeartbeatTime.forEach { (userId, lastTime) ->
                if (currentTime - lastTime > HEARTBEAT_TIMEOUT_MS) {
                    timeoutUsers.add(userId)
                }
            }

            // 타임아웃된 사용자들을 오프라인 처리
            timeoutUsers.forEach { userId ->
                onlineStatusService.processDisconnection(userId)
                lastHeartbeatTime.remove(userId)
                logger.info("하트비트 타임아웃으로 오프라인 처리: userId={}", userId)
            }

            if (timeoutUsers.isNotEmpty()) {
                logger.info("연결 상태 확인 완료: {}명 오프라인 처리", timeoutUsers.size)
            }

        } catch (e: Exception) {
            logger.error("연결 상태 확인 중 오류 발생", e)
        }
    }

    /**
     * 사용자 연결 해제 처리
     * WebSocket 연결이 끊어졌을 때 호출
     * 
     * @param userId 연결 해제한 사용자 ID
     */
    fun handleDisconnection(userId: String) {
        try {
            onlineStatusService.processDisconnection(userId)
            lastHeartbeatTime.remove(userId)
            logger.info("사용자 연결 해제 처리 완료: userId={}", userId)
        } catch (e: Exception) {
            logger.error("사용자 연결 해제 처리 중 오류 발생: userId={}", userId, e)
        }
    }

    /**
     * 하트비트 메시지 데이터 클래스
     */
    data class HeartbeatMessage(
        val timestamp: Long,
        val clientInfo: String? = null
    )

    /**
     * 하트비트 응답 데이터 클래스
     */
    data class HeartbeatResponse(
        val timestamp: Long,
        val serverTime: Long,
        val status: String
    )

    /**
     * 타이핑 메시지 데이터 클래스
     */
    data class TypingMessage(
        val channelId: String,
        val isTyping: Boolean
    )

    /**
     * 타이핑 이벤트 데이터 클래스
     */
    data class TypingEvent(
        val userId: String,
        val channelId: String,
        val isTyping: Boolean,
        val timestamp: Long
    )

    /**
     * 읽음 표시 메시지 데이터 클래스
     */
    data class ReadReceiptMessage(
        val messageId: String,
        val channelId: String
    )

    /**
     * 읽음 표시 이벤트 데이터 클래스
     */
    data class ReadReceiptEvent(
        val userId: String,
        val messageId: String,
        val channelId: String,
        val readAt: Long
    )

    /**
     * 연결 상태 통계 조회
     * 
     * @return 연결 상태 통계 정보
     */
    fun getConnectionStats(): ConnectionStats {
        return try {
            val totalConnections = lastHeartbeatTime.size
            val currentTime = System.currentTimeMillis()
            val activeConnections = lastHeartbeatTime.count { (_, lastTime) ->
                currentTime - lastTime <= HEARTBEAT_TIMEOUT_MS
            }

            ConnectionStats(
                totalConnections = totalConnections,
                activeConnections = activeConnections,
                inactiveConnections = totalConnections - activeConnections,
                lastCheckTime = currentTime
            )
        } catch (e: Exception) {
            logger.error("연결 상태 통계 조회 실패", e)
            ConnectionStats(0, 0, 0, System.currentTimeMillis())
        }
    }

    /**
     * 연결 상태 통계 데이터 클래스
     */
    data class ConnectionStats(
        val totalConnections: Int,
        val activeConnections: Int,
        val inactiveConnections: Int,
        val lastCheckTime: Long
    )
}
