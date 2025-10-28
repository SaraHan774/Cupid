package com.august.cupid.config

import com.august.cupid.websocket.ConnectionInterceptor
import com.august.cupid.websocket.StompChannelInterceptor
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * WebSocket STOMP 설정
 * 실시간 메시징을 위한 WebSocket 엔드포인트 및 Message Broker 설정
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val connectionInterceptor: ConnectionInterceptor,
    private val stompChannelInterceptor: StompChannelInterceptor
) : WebSocketMessageBrokerConfigurer {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Message Broker 설정
     * 클라이언트가 구독할 수 있는 destination prefix 설정
     */
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // SimpleBroker 활성화 (메모리 기반 브로커)
        // /topic: 일대다 메시징 (브로드캐스트)
        // /queue: 일대일 메시징
        registry.enableSimpleBroker("/topic", "/queue")
        
        // 클라이언트가 보낸 메시지를 처리할 destination prefix
        registry.setApplicationDestinationPrefixes("/app")
        
        logger.info("Message Broker 설정 완료: /topic, /queue")
    }

    /**
     * STOMP 엔드포인트 등록
     * 클라이언트가 연결할 WebSocket 엔드포인트 설정
     */
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // WebSocket 엔드포인트: /ws
        registry.addEndpoint("/ws")
            .addInterceptors(connectionInterceptor)  // ConnectionInterceptor 등록
            .setAllowedOriginPatterns("*")  // CORS 설정 (개발 환경용 - allowCredentials 사용 시 필요)
            .withSockJS()  // SockJS fallback 지원

        logger.info("WebSocket 엔드포인트 등록 완료: /ws (with ConnectionInterceptor)")
    }

    /**
     * Client Inbound Channel 설정
     * STOMP 메시지를 받을 때 사용되는 채널에 인터셉터 등록
     */
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompChannelInterceptor)
        logger.info("STOMP 채널 인터셉터 등록 완료")
    }
}
