package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.MessageService
import com.august.cupid.service.OnlineStatusService
import com.august.cupid.service.NotificationService
import com.august.cupid.service.ReadReceiptService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 메시지 관리 컨트롤러
 * HTTP REST API로 메시지 관리
 */
@Tag(name = "Message", description = "메시지 관리 API - 메시지 조회/전송/수정/삭제 및 읽음 표시")
@RestController
@RequestMapping("/api/v1")
class MessageController(
    private val messageService: MessageService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val onlineStatusService: OnlineStatusService,
    private val notificationService: NotificationService,
    private val readReceiptService: ReadReceiptService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * JWT에서 사용자 ID 추출
     */
    private fun getUserIdFromAuthentication(authentication: Authentication): UUID? {
        return try {
            val userIdString = authentication.name
            UUID.fromString(userIdString)
        } catch (e: Exception) {
            logger.error("사용자 ID 추출 실패", e)
            null
        }
    }

    /**
     * 채널의 메시지 목록 조회
     * GET /api/v1/channels/{channelId}/messages
     */
    @Operation(
        summary = "메시지 목록 조회",
        description = "특정 채널의 메시지 목록을 페이징하여 조회합니다"
    )
    @GetMapping("/channels/{channelId}/messages")
    fun getChannelMessages(
        authentication: Authentication,
        @PathVariable channelId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("메시지 목록 조회: userId={}, channelId={}, page={}, size={}", userId, channelId, page, size)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)
            
            val result = messageService.getChannelMessages(channelIdUuid, userId, page, size)
            
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
    @Operation(
        summary = "메시지 전송",
        description = "HTTP를 통해 메시지를 전송합니다. WebSocket 연결이 불가능한 경우 사용합니다"
    )
    @PostMapping("/channels/{channelId}/messages")
    fun sendMessage(
        authentication: Authentication,
        @PathVariable channelId: String,
        @Valid @RequestBody request: SendMessageRequest
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("메시지 전송 요청 (HTTP): userId={}, channelId={}", userId, channelId)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)

            // channelId를 설정 (URL path variable이 우선, 없으면 request body에서 가져옴)
            val finalChannelId = request.channelId ?: channelIdUuid
            val messageRequest = request.copy(channelId = finalChannelId)

            // MessageService로 메시지 저장
            val result = messageService.sendMessage(messageRequest, userId)
            
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
    @Operation(
        summary = "메시지 수정",
        description = "전송한 메시지의 내용을 수정합니다"
    )
    @PutMapping("/messages/{messageId}")
    fun editMessage(
        authentication: Authentication,
        @RequestParam newContent: String,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("메시지 수정 요청: userId={}, messageId={}", userId, messageId)
        
        return try {
            val messageIdUuid = UUID.fromString(messageId)
            
            val result = messageService.editMessage(messageIdUuid, newContent, userId)
            
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
    @Operation(
        summary = "메시지 삭제",
        description = "전송한 메시지를 삭제합니다 (Soft Delete)"
    )
    @DeleteMapping("/messages/{messageId}")
    fun deleteMessage(
        authentication: Authentication,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("메시지 삭제 요청: userId={}, messageId={}", userId, messageId)
        
        return try {
            val messageIdUuid = UUID.fromString(messageId)
            
            val result = messageService.deleteMessage(messageIdUuid, userId)
            
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
    @Operation(
        summary = "읽음 표시",
        description = "메시지를 읽은 것으로 표시합니다"
    )
    @PostMapping("/messages/{messageId}/read")
    fun markAsRead(
        authentication: Authentication,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("읽음 표시 요청: userId={}, messageId={}", userId, messageId)
        
        return try {
            val messageIdUuid = UUID.fromString(messageId)
            
            val result = messageService.markMessageAsRead(messageIdUuid, userId)
            
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
    @Operation(
        summary = "읽지 않은 메시지 수 조회",
        description = "특정 채널의 읽지 않은 메시지 개수를 조회합니다"
    )
    @GetMapping("/channels/{channelId}/unread-count")
    fun getUnreadCount(
        authentication: Authentication,
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("읽지 않은 메시지 수 조회: userId={}, channelId={}", userId, channelId)

        return try {
            val channelIdUuid = UUID.fromString(channelId)

            val result = messageService.getUnreadMessageCount(channelIdUuid, userId)

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

    // ============================================
    // 읽음 표시 (Read Receipts) 엔드포인트
    // ============================================

    /**
     * 메시지 읽음 표시 (개선된 버전)
     * POST /api/v1/channels/{channelId}/messages/{messageId}/read
     *
     * 기능:
     * - MongoDB에 읽음 표시 저장
     * - Redis 캐시 업데이트
     * - WebSocket으로 발신자에게 알림
     */
    @Operation(
        summary = "메시지 읽음 표시",
        description = "특정 메시지를 읽은 것으로 표시하고 발신자에게 읽음 알림을 전송합니다"
    )
    @PostMapping("/channels/{channelId}/messages/{messageId}/read")
    fun markMessageAsRead(
        authentication: Authentication,
        @PathVariable channelId: String,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }

        logger.info("메시지 읽음 표시: userId={}, channelId={}, messageId={}", userId, channelId, messageId)

        return try {
            val channelIdUuid = UUID.fromString(channelId)
            val messageIdUuid = UUID.fromString(messageId)

            // ReadReceiptService로 읽음 표시 처리
            val result = readReceiptService.markAsRead(messageIdUuid, userId, channelIdUuid)

            if (!result.success || result.data == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "읽음 표시 실패")
                ))
            }

            val readReceipt = result.data

            // WebSocket으로 발신자에게 읽음 알림 전송
            try {
                // 메시지 발신자 ID 조회
                val message = messageService.getMessageById(messageIdUuid, userId)
                if (message.success && message.data != null) {
                    val senderId = message.data.senderId

                    // 발신자에게 읽음 이벤트 전송
                    val readEvent = ReadReceiptEvent(
                        messageId = messageIdUuid,
                        channelId = channelIdUuid,
                        userId = userId,
                        readAt = readReceipt.readAt
                    )

                    messagingTemplate.convertAndSendToUser(
                        senderId.toString(),
                        "/queue/read-receipts",
                        readEvent
                    )

                    logger.debug(
                        "읽음 알림 전송: senderId={}, messageId={}, readerId={}",
                        senderId, messageIdUuid, userId
                    )
                }
            } catch (e: Exception) {
                logger.warn("읽음 알림 전송 실패 (계속 진행): messageId={}", messageIdUuid, e)
                // Best effort - 알림 실패해도 읽음 표시는 완료됨
            }

            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "메시지 읽음 표시가 완료되었습니다",
                "data" to readReceipt
            ))

        } catch (e: Exception) {
            logger.error("메시지 읽음 표시 중 오류 발생: userId={}, messageId={}", userId, messageId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 배치 읽음 표시
     * POST /api/v1/channels/{channelId}/messages/read-batch
     *
     * 기능:
     * - 여러 메시지를 한 번에 읽음 처리
     * - 성능 최적화 (bulk insert)
     */
    @Operation(
        summary = "배치 읽음 표시",
        description = "여러 메시지를 한 번에 읽은 것으로 표시합니다 (성능 최적화)"
    )
    @PostMapping("/channels/{channelId}/messages/read-batch")
    fun markMultipleAsRead(
        authentication: Authentication,
        @PathVariable channelId: String,
        @Valid @RequestBody request: BatchMarkAsReadRequest
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }

        logger.info(
            "배치 읽음 표시: userId={}, channelId={}, count={}",
            userId, channelId, request.messageIds.size
        )

        return try {
            val channelIdUuid = UUID.fromString(channelId)

            // ReadReceiptService로 배치 읽음 처리
            val result = readReceiptService.markMultipleAsRead(
                request.messageIds,
                userId,
                channelIdUuid
            )

            if (!result.success || result.data == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "배치 읽음 표시 실패")
                ))
            }

            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "배치 읽음 표시가 완료되었습니다",
                "data" to result.data
            ))

        } catch (e: Exception) {
            logger.error("배치 읽음 표시 중 오류 발생: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 메시지 읽음 수 조회
     * GET /api/v1/messages/{messageId}/read-count
     *
     * 기능:
     * - 메시지를 읽은 사람 수 조회
     * - 그룹 채팅에서 "읽은 사람 3명" 표시용
     */
    @Operation(
        summary = "메시지 읽음 수 조회",
        description = "특정 메시지를 읽은 사용자 수와 목록을 조회합니다"
    )
    @GetMapping("/messages/{messageId}/read-count")
    fun getMessageReadCount(
        authentication: Authentication,
        @PathVariable messageId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }

        logger.info("메시지 읽음 수 조회: userId={}, messageId={}", userId, messageId)

        return try {
            val messageIdUuid = UUID.fromString(messageId)

            val result = readReceiptService.getMessageReadCount(messageIdUuid)

            if (!result.success || result.data == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "읽음 수 조회 실패")
                ))
            }

            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to result.data
            ))

        } catch (e: Exception) {
            logger.error("메시지 읽음 수 조회 중 오류 발생: userId={}, messageId={}", userId, messageId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 채널 읽지 않은 수 조회 (개선된 버전)
     * GET /api/v1/channels/{channelId}/unread
     *
     * 기능:
     * - Redis 캐시 활용한 빠른 조회
     * - lastReadAt 정보 포함
     */
    @Operation(
        summary = "채널 읽지 않은 메시지 수 조회",
        description = "특정 채널의 읽지 않은 메시지 개수를 조회합니다 (캐싱 최적화)"
    )
    @GetMapping("/channels/{channelId}/unread")
    fun getChannelUnreadCount(
        authentication: Authentication,
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }

        logger.info("채널 읽지 않은 수 조회: userId={}, channelId={}", userId, channelId)

        return try {
            val channelIdUuid = UUID.fromString(channelId)

            val result = readReceiptService.getUnreadMessageCount(channelIdUuid, userId)

            if (!result.success || result.data == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "읽지 않은 수 조회 실패")
                ))
            }

            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to result.data
            ))

        } catch (e: Exception) {
            logger.error("채널 읽지 않은 수 조회 중 오류 발생: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 전체 읽지 않은 수 조회
     * GET /api/v1/messages/unread/total
     *
     * 기능:
     * - 모든 채널의 읽지 않은 메시지 총합
     * - 앱 아이콘 배지 숫자용
     */
    @Operation(
        summary = "전체 읽지 않은 메시지 수 조회",
        description = "사용자의 모든 채널에서 읽지 않은 메시지 총 개수를 조회합니다"
    )
    @GetMapping("/messages/unread/total")
    fun getTotalUnreadCount(
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }

        logger.info("전체 읽지 않은 수 조회: userId={}", userId)

        return try {
            val result = readReceiptService.getTotalUnreadMessageCount(userId)

            if (!result.success) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "전체 읽지 않은 수 조회 실패")
                ))
            }

            ResponseEntity.ok(mapOf(
                "success" to true,
                "totalUnreadCount" to (result.data ?: 0L)
            ))

        } catch (e: Exception) {
            logger.error("전체 읽지 않은 수 조회 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }
}

