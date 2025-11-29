package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.Message
import com.august.cupid.security.CurrentUser
import com.august.cupid.service.MessageService
import com.august.cupid.service.ReadReceiptService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 메시지 관리 컨트롤러
 * HTTP REST API로 메시지 관리
 *
 * 이 컨트롤러는 얇은 레이어로 설계되어 있습니다:
 * - @CurrentUser로 인증된 사용자 ID 주입
 * - 서비스 호출 및 응답 매핑
 * - 예외는 GlobalExceptionHandler에서 처리
 */
@Tag(
    name = "Chat - Message",
    description = "채팅 서비스 전용 메시지 관리 API (/api/v1/chat) - 메시지 조회/전송/수정/삭제 및 읽음 표시"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/chat")
class MessageController(
    private val messageService: MessageService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val readReceiptService: ReadReceiptService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 채널의 메시지 목록 조회
     * GET /api/v1/chat/channels/{channelId}/messages
     */
    @Operation(
        summary = "메시지 목록 조회",
        description = "특정 채널의 메시지 목록을 페이징하여 조회합니다"
    )
    @GetMapping("/channels/{channelId}/messages")
    fun getChannelMessages(
        @CurrentUser userId: UUID,
        @Parameter(description = "채널 ID") @PathVariable channelId: UUID,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") size: Int
    ): ResponseEntity<ApiResponse<PagedResponse<MessageResponse>>> {
        logger.debug("메시지 목록 조회: userId={}, channelId={}, page={}, size={}", userId, channelId, page, size)

        val messages = messageService.getChannelMessages(channelId, userId, page, size)

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = messages
        ))
    }

    /**
     * 메시지 전송 (HTTP)
     * POST /api/v1/chat/channels/{channelId}/messages
     */
    @Operation(
        summary = "메시지 전송",
        description = "HTTP를 통해 메시지를 전송합니다. WebSocket 연결이 불가능한 경우 사용합니다"
    )
    @PostMapping("/channels/{channelId}/messages")
    fun sendMessage(
        @CurrentUser userId: UUID,
        @Parameter(description = "채널 ID") @PathVariable channelId: UUID,
        @Valid @RequestBody request: SendMessageRequest
    ): ResponseEntity<ApiResponse<MessageResponse>> {
        logger.debug("메시지 전송 요청 (HTTP): userId={}, channelId={}", userId, channelId)

        // channelId를 설정 (URL path variable이 우선)
        val messageRequest = request.copy(channelId = channelId)
        val savedMessage = messageService.sendMessage(messageRequest, userId)

        logger.info("메시지 저장 완료: messageId={}", savedMessage.id)

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(
            success = true,
            data = savedMessage.toResponse(),
            message = "메시지가 전송되었습니다"
        ))
    }

    /**
     * 메시지 수정
     * PUT /api/v1/chat/messages/{messageId}
     */
    @Operation(
        summary = "메시지 수정",
        description = "전송한 메시지의 내용을 수정합니다"
    )
    @PutMapping("/messages/{messageId}")
    fun editMessage(
        @CurrentUser userId: UUID,
        @Parameter(description = "메시지 ID") @PathVariable messageId: UUID,
        @RequestParam newContent: String
    ): ResponseEntity<ApiResponse<MessageResponse>> {
        logger.debug("메시지 수정 요청: userId={}, messageId={}", userId, messageId)

        val updatedMessage = messageService.editMessage(messageId, newContent, userId)

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = updatedMessage.toResponse(),
            message = "메시지가 수정되었습니다"
        ))
    }

    /**
     * 메시지 삭제
     * DELETE /api/v1/chat/messages/{messageId}
     */
    @Operation(
        summary = "메시지 삭제",
        description = "전송한 메시지를 삭제합니다 (Soft Delete)"
    )
    @DeleteMapping("/messages/{messageId}")
    fun deleteMessage(
        @CurrentUser userId: UUID,
        @Parameter(description = "메시지 ID") @PathVariable messageId: UUID
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("메시지 삭제 요청: userId={}, messageId={}", userId, messageId)

        messageService.deleteMessage(messageId, userId)

        return ResponseEntity.ok(ApiResponse(
            success = true,
            message = "메시지가 삭제되었습니다"
        ))
    }

    /**
     * 읽음 표시
     * POST /api/v1/chat/messages/{messageId}/read
     */
    @Operation(
        summary = "읽음 표시",
        description = "메시지를 읽은 것으로 표시합니다"
    )
    @PostMapping("/messages/{messageId}/read")
    fun markAsRead(
        @CurrentUser userId: UUID,
        @Parameter(description = "메시지 ID") @PathVariable messageId: UUID
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("읽음 표시 요청: userId={}, messageId={}", userId, messageId)

        val alreadyRead = messageService.markMessageAsRead(messageId, userId)

        val message = if (alreadyRead) "이미 읽음 표시가 되어 있습니다" else "읽음 표시 완료"

        return ResponseEntity.ok(ApiResponse(
            success = true,
            message = message
        ))
    }

    /**
     * 읽지 않은 메시지 수 조회
     * GET /api/v1/chat/channels/{channelId}/unread-count
     */
    @Operation(
        summary = "읽지 않은 메시지 수 조회",
        description = "특정 채널의 읽지 않은 메시지 개수를 조회합니다"
    )
    @GetMapping("/channels/{channelId}/unread-count")
    fun getUnreadCount(
        @CurrentUser userId: UUID,
        @Parameter(description = "채널 ID") @PathVariable channelId: UUID
    ): ResponseEntity<ApiResponse<Long>> {
        logger.debug("읽지 않은 메시지 수 조회: userId={}, channelId={}", userId, channelId)

        val unreadCount = messageService.getUnreadMessageCount(channelId, userId)

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = unreadCount
        ))
    }

    // ============================================
    // 읽음 표시 (Read Receipts) 엔드포인트
    // ============================================

    /**
     * 메시지 읽음 표시 (개선된 버전)
     * POST /api/v1/chat/channels/{channelId}/messages/{messageId}/read
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
        @CurrentUser userId: UUID,
        @Parameter(description = "채널 ID") @PathVariable channelId: UUID,
        @Parameter(description = "메시지 ID") @PathVariable messageId: UUID
    ): ResponseEntity<ApiResponse<ReadReceiptResponse>> {
        logger.debug("메시지 읽음 표시: userId={}, channelId={}, messageId={}", userId, channelId, messageId)

        // ReadReceiptService로 읽음 표시 처리
        val result = readReceiptService.markAsRead(messageId, userId, channelId)

        if (!result.success || result.data == null) {
            return ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = result.message ?: "읽음 표시 실패"
            ))
        }

        val readReceipt = result.data

        // WebSocket으로 발신자에게 읽음 알림 전송 (best effort)
        sendReadReceiptNotification(messageId, channelId, userId, readReceipt)

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = readReceipt,
            message = "메시지 읽음 표시가 완료되었습니다"
        ))
    }

    /**
     * 배치 읽음 표시
     * POST /api/v1/chat/channels/{channelId}/messages/read-batch
     */
    @Operation(
        summary = "배치 읽음 표시",
        description = "여러 메시지를 한 번에 읽은 것으로 표시합니다 (성능 최적화)"
    )
    @PostMapping("/channels/{channelId}/messages/read-batch")
    fun markMultipleAsRead(
        @CurrentUser userId: UUID,
        @Parameter(description = "채널 ID") @PathVariable channelId: UUID,
        @Valid @RequestBody request: BatchMarkAsReadRequest
    ): ResponseEntity<ApiResponse<BatchReadReceiptResponse>> {
        logger.debug("배치 읽음 표시: userId={}, channelId={}, count={}", userId, channelId, request.messageIds.size)

        val result = readReceiptService.markMultipleAsRead(request.messageIds, userId, channelId)

        if (!result.success || result.data == null) {
            return ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = result.message ?: "배치 읽음 표시 실패"
            ))
        }

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = result.data,
            message = "배치 읽음 표시가 완료되었습니다"
        ))
    }

    /**
     * 메시지 읽음 수 조회
     * GET /api/v1/chat/messages/{messageId}/read-count
     */
    @Operation(
        summary = "메시지 읽음 수 조회",
        description = "특정 메시지를 읽은 사용자 수와 목록을 조회합니다"
    )
    @GetMapping("/messages/{messageId}/read-count")
    fun getMessageReadCount(
        @CurrentUser userId: UUID,
        @Parameter(description = "메시지 ID") @PathVariable messageId: UUID
    ): ResponseEntity<ApiResponse<MessageReadCountResponse>> {
        logger.debug("메시지 읽음 수 조회: userId={}, messageId={}", userId, messageId)

        val result = readReceiptService.getMessageReadCount(messageId)

        if (!result.success || result.data == null) {
            return ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = result.message ?: "읽음 수 조회 실패"
            ))
        }

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = result.data
        ))
    }

    /**
     * 채널 읽지 않은 수 조회 (개선된 버전)
     * GET /api/v1/chat/channels/{channelId}/unread
     */
    @Operation(
        summary = "채널 읽지 않은 메시지 수 조회",
        description = "특정 채널의 읽지 않은 메시지 개수를 조회합니다 (캐싱 최적화)"
    )
    @GetMapping("/channels/{channelId}/unread")
    fun getChannelUnreadCount(
        @CurrentUser userId: UUID,
        @Parameter(description = "채널 ID") @PathVariable channelId: UUID
    ): ResponseEntity<ApiResponse<UnreadCountResponse>> {
        logger.debug("채널 읽지 않은 수 조회: userId={}, channelId={}", userId, channelId)

        val result = readReceiptService.getUnreadMessageCount(channelId, userId)

        if (!result.success || result.data == null) {
            return ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = result.message ?: "읽지 않은 수 조회 실패"
            ))
        }

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = result.data
        ))
    }

    /**
     * 전체 읽지 않은 수 조회
     * GET /api/v1/chat/messages/unread/total
     */
    @Operation(
        summary = "전체 읽지 않은 메시지 수 조회",
        description = "사용자의 모든 채널에서 읽지 않은 메시지 총 개수를 조회합니다"
    )
    @GetMapping("/messages/unread/total")
    fun getTotalUnreadCount(
        @CurrentUser userId: UUID
    ): ResponseEntity<ApiResponse<Long>> {
        logger.debug("전체 읽지 않은 수 조회: userId={}", userId)

        val result = readReceiptService.getTotalUnreadMessageCount(userId)

        if (!result.success) {
            return ResponseEntity.badRequest().body(ApiResponse(
                success = false,
                error = result.message ?: "전체 읽지 않은 수 조회 실패"
            ))
        }

        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = result.data ?: 0L
        ))
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    /**
     * Message 엔티티를 MessageResponse DTO로 변환
     */
    private fun Message.toResponse(): MessageResponse {
        return MessageResponse(
            id = this.id,
            channelId = this.channelId,
            senderId = this.senderId,
            encryptedContent = this.encryptedContent,
            messageType = this.messageType.name,
            fileMetadata = this.fileMetadata?.let { metadata ->
                FileMetadataDto(
                    fileName = metadata.fileName,
                    fileSize = metadata.fileSize,
                    mimeType = metadata.mimeType,
                    fileUrl = metadata.encryptedFileUrl,
                    thumbnailUrl = metadata.thumbnailUrl
                )
            },
            replyToMessageId = null,
            status = this.status.name,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * 읽음 알림을 WebSocket으로 전송 (best effort)
     */
    private fun sendReadReceiptNotification(
        messageId: UUID,
        channelId: UUID,
        readerId: UUID,
        readReceipt: ReadReceiptResponse
    ) {
        try {
            val message = messageService.getMessageById(messageId, readerId)
            val senderId = message.senderId

            val readEvent = ReadReceiptEvent(
                messageId = messageId,
                channelId = channelId,
                userId = readerId,
                readAt = readReceipt.readAt
            )

            messagingTemplate.convertAndSendToUser(
                senderId.toString(),
                "/queue/read-receipts",
                readEvent
            )

            logger.debug("읽음 알림 전송: senderId={}, messageId={}, readerId={}", senderId, messageId, readerId)
        } catch (e: Exception) {
            logger.warn("읽음 알림 전송 실패 (계속 진행): messageId={}, error={}", messageId, e.message)
            // Best effort - 알림 실패해도 읽음 표시는 완료됨
        }
    }
}
