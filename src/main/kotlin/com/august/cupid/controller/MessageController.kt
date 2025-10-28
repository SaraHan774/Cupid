package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.MessageService
import com.august.cupid.service.OnlineStatusService
import com.august.cupid.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 메시지 관리 컨트롤러
 * HTTP REST API로 메시지 관리
 */
@RestController
@RequestMapping("/api/v1")
class MessageController(
    private val messageService: MessageService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val onlineStatusService: OnlineStatusService,
    private val notificationService: NotificationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 채널의 메시지 목록 조회
     * GET /api/v1/channels/{channelId}/messages
     */
    @GetMapping("/channels/{channelId}/messages")
    fun getChannelMessages(
        @RequestParam userId: String, // TODO: JWT에서 추출
        @PathVariable channelId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") size: Int
    ): ResponseEntity<Map<String, Any>> {
        logger.info("메시지 목록 조회: userId={}, channelId={}, page={}, size={}", userId, channelId, page, size)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = messageService.getChannelMessages(channelIdUuid, userIdUuid, page, size)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "messages" to (result.data ?: PagedResponse(emptyList(), 0, 0, 0, 0, false, false))
                ))
            } else {
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "메시지 목록 조회 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("메시지 목록 조회 중 오류 발생: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 메시지 전송 (HTTP)
     * POST /api/v1/channels/{channelId}/messages
     */
    @PostMapping("/channels/{channelId}/messages")
    fun sendMessage(
        @RequestParam userId: String, // TODO: JWT에서 추출
        @PathVariable channelId: String,
        @RequestBody request: SendMessageRequest
    ): ResponseEntity<Map<String, Any>> {
        logger.info("메시지 전송 요청 (HTTP): userId={}, channelId={}", userId, channelId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val channelIdUuid = UUID.fromString(channelId)
            
            // channelId를 설정 (URL path variable이 우선)
            val messageRequest = request.copy(channelId = channelIdUuid)
            
            // MessageService로 메시지 저장
            val result = messageService.sendMessage(messageRequest, userIdUuid)
            
            if (!result.success || result.data == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "메시지 전송 실패")
                ))
            }

            val savedMessage = result.data
            logger.info("메시지 저장 완료: messageId={}", savedMessage.id)

            // 온라인 사용자에게 WebSocket 브로드캐스트는 ChatController에서 처리
            // 여기서는 메시지 저장만 처리

            ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                "success" to true,
                "message" to (result.message ?: "메시지가 전송되었습니다"),
                "data" to savedMessage
            ))
        } catch (e: Exception) {
            logger.error("메시지 전송 중 오류 발생: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 메시지 수정
     * PUT /api/v1/messages/{messageId}
     */
    @PutMapping("/messages/{messageId}")
    fun editMessage(
        @RequestParam userId: String,
        @RequestParam newContent: String,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("메시지 수정 요청: userId={}, messageId={}", userId, messageId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val messageIdUuid = UUID.fromString(messageId)
            
            val result = messageService.editMessage(messageIdUuid, newContent, userIdUuid)
            
            if (result.success && result.data != null) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "메시지가 수정되었습니다"),
                    "data" to result.data
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "메시지 수정 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("메시지 수정 중 오류 발생: userId={}, messageId={}", userId, messageId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 메시지 삭제
     * DELETE /api/v1/messages/{messageId}
     */
    @DeleteMapping("/messages/{messageId}")
    fun deleteMessage(
        @RequestParam userId: String,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("메시지 삭제 요청: userId={}, messageId={}", userId, messageId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val messageIdUuid = UUID.fromString(messageId)
            
            val result = messageService.deleteMessage(messageIdUuid, userIdUuid)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "메시지가 삭제되었습니다")
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "메시지 삭제 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("메시지 삭제 중 오류 발생: userId={}, messageId={}", userId, messageId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 읽음 표시
     * POST /api/v1/messages/{messageId}/read
     */
    @PostMapping("/messages/{messageId}/read")
    fun markAsRead(
        @RequestParam userId: String,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("읽음 표시 요청: userId={}, messageId={}", userId, messageId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val messageIdUuid = UUID.fromString(messageId)
            
            val result = messageService.markMessageAsRead(messageIdUuid, userIdUuid)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "읽음 표시 완료")
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "읽음 표시 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("읽음 표시 중 오류 발생: userId={}, messageId={}", userId, messageId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 읽지 않은 메시지 수 조회
     * GET /api/v1/channels/{channelId}/unread-count
     */
    @GetMapping("/channels/{channelId}/unread-count")
    fun getUnreadCount(
        @RequestParam userId: String,
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("읽지 않은 메시지 수 조회: userId={}, channelId={}", userId, channelId)
        
        return try {
            val userIdUuid = UUID.fromString(userId)
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = messageService.getUnreadMessageCount(channelIdUuid, userIdUuid)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "unreadCount" to (result.data ?: 0L)
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "읽지 않은 메시지 수 조회 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("읽지 않은 메시지 수 조회 중 오류 발생: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }
}

