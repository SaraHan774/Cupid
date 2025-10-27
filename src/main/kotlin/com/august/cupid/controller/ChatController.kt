package com.august.cupid.controller

import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller

/**
 * WebSocket Chat Controller
 * Echo 테스트를 위한 간단한 메시지 핸들러
 */
@Controller
class ChatController {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Echo 메시지 핸들러
     * 클라이언트가 /app/echo로 메시지를 보내면 /topic/echo로 에코
     */
    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    fun echo(message: String): String {
        logger.info("Received message: $message")
        return "Echo: $message"
    }
}
