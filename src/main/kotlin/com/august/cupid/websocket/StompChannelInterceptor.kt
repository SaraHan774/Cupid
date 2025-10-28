package com.august.cupid.websocket

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

/**
 * STOMP 채널 인터셉터
 * WebSocket 핸드셰이크에서 설정한 세션 속성을 STOMP 세션으로 전달
 */
@Component
class StompChannelInterceptor : ChannelInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 메시지 전송 전 처리
     * CONNECT 프레임에서 웹소켓 세션 속성을 STOMP 세션 속성으로 복사
     */
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            // WebSocket 세션에서 사용자 ID 가져오기
            val sessionAttributes = accessor.sessionAttributes

            if (sessionAttributes != null) {
                val userId = sessionAttributes["userId"] as? String

                if (userId != null) {
                    // STOMP 세션에 사용자 ID 저장
                    accessor.user = java.security.Principal { userId }
                    logger.info("STOMP 연결: userId={} 설정 완료", userId)
                } else {
                    logger.warn("STOMP 연결: 세션 속성에 userId가 없습니다")
                }
            } else {
                logger.warn("STOMP 연결: sessionAttributes가 null입니다")
            }
        }

        return message
    }
}
