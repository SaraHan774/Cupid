package com.august.cupid.service

import com.august.cupid.exception.*
import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.Message
import com.august.cupid.model.entity.MessageReads
import com.august.cupid.model.entity.MessageStatus
import com.august.cupid.model.entity.MessageType
import com.august.cupid.model.entity.FileMetadata
import com.august.cupid.repository.MessageRepository
import com.august.cupid.repository.MessageReadsRepository
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 서비스
 * 메시지 관련 비즈니스 로직 처리
 *
 * 이 서비스는 도메인 객체를 반환하고, 오류 상황에서는 예외를 발생시킵니다.
 * 예외는 GlobalExceptionHandler에서 일관된 API 응답으로 변환됩니다.
 */
@Service
@Transactional
class MessageService(
    private val messageRepository: MessageRepository,
    private val messageReadsRepository: MessageReadsRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userRepository: UserRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 메시지 전송
     *
     * @param request 메시지 전송 요청
     * @param senderId 발신자 ID
     * @return 저장된 메시지
     * @throws BadRequestException 채널 ID가 없는 경우
     * @throws UserNotFoundException 발신자를 찾을 수 없는 경우
     * @throws ChannelAccessDeniedException 채널 접근 권한이 없는 경우
     */
    fun sendMessage(request: SendMessageRequest, senderId: UUID): Message {
        val channelId = request.channelId
            ?: throw BadRequestException("채널 ID는 필수입니다", field = "channelId")

        // 발신자 존재 확인
        userRepository.findById(senderId).orElseThrow {
            UserNotFoundException(userId = senderId)
        }

        // 채널 멤버십 확인
        val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, senderId)
        if (membership == null || !membership.isActive) {
            throw ChannelAccessDeniedException(channelId, senderId)
        }

        // 파일 메타데이터 변환
        val fileMetadata = request.fileMetadata?.let { dto ->
            FileMetadata(
                fileName = dto.fileName,
                fileSize = dto.fileSize,
                mimeType = dto.mimeType,
                encryptedFileUrl = dto.fileUrl,
                thumbnailUrl = dto.thumbnailUrl
            )
        }

        // 메시지 생성
        val message = Message(
            channelId = channelId,
            senderId = senderId,
            encryptedContent = request.encryptedContent,
            messageType = MessageType.valueOf(request.messageType.uppercase()),
            fileMetadata = fileMetadata
        )

        val savedMessage = messageRepository.save(message)
        logger.info("메시지 전송 완료: messageId={}, channelId={}", savedMessage.id, savedMessage.channelId)

        // WebSocket으로 실시간 브로드캐스트 (best effort)
        broadcastMessage(savedMessage)

        return savedMessage
    }

    /**
     * 채널의 메시지 목록 조회
     *
     * @param channelId 채널 ID
     * @param userId 요청 사용자 ID
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 페이징된 메시지 목록
     * @throws ChannelAccessDeniedException 채널 접근 권한이 없는 경우
     */
    @Transactional(readOnly = true)
    fun getChannelMessages(
        channelId: UUID,
        userId: UUID,
        page: Int = 0,
        size: Int = 50
    ): PagedResponse<MessageResponse> {
        validateChannelAccess(channelId, userId)

        val pageable = PageRequest.of(page, size)
        val messages = messageRepository.findByChannelIdAndStatusNotOrderByCreatedAtDesc(
            channelId,
            MessageStatus.DELETED,
            pageable
        )

        return PagedResponse(
            content = messages.content.map { it.toResponse() },
            page = messages.number,
            size = messages.size,
            totalElements = messages.totalElements,
            totalPages = messages.totalPages,
            hasNext = messages.hasNext(),
            hasPrevious = messages.hasPrevious()
        )
    }

    /**
     * 메시지 ID로 조회
     *
     * @param messageId 메시지 ID
     * @param userId 요청 사용자 ID
     * @return 메시지
     * @throws MessageNotFoundException 메시지를 찾을 수 없는 경우
     * @throws ChannelAccessDeniedException 채널 접근 권한이 없는 경우
     */
    @Transactional(readOnly = true)
    fun getMessageById(messageId: UUID, userId: UUID): Message {
        val message = messageRepository.findById(messageId).orElseThrow {
            MessageNotFoundException(messageId)
        }

        validateChannelAccess(message.channelId, userId)

        return message
    }

    /**
     * 메시지 수정
     *
     * @param messageId 메시지 ID
     * @param newContent 새 내용
     * @param userId 요청 사용자 ID
     * @return 수정된 메시지
     * @throws MessageNotFoundException 메시지를 찾을 수 없는 경우
     * @throws MessageAccessDeniedException 메시지 수정 권한이 없는 경우
     * @throws BadRequestException 삭제된 메시지인 경우
     */
    fun editMessage(messageId: UUID, newContent: String, userId: UUID): Message {
        val message = messageRepository.findById(messageId).orElseThrow {
            MessageNotFoundException(messageId)
        }

        // 발신자 확인
        if (message.senderId != userId) {
            throw MessageAccessDeniedException("메시지를 수정할 권한이 없습니다")
        }

        // 삭제된 메시지는 수정 불가
        if (message.status == MessageStatus.DELETED) {
            throw BadRequestException("삭제된 메시지는 수정할 수 없습니다")
        }

        // 메시지 내용 업데이트
        val updated = messageRepository.updateMessageContent(
            messageId = messageId,
            newEncryptedContent = newContent,
            previousContent = message.encryptedContent
        )

        if (!updated) {
            throw BadRequestException("메시지 업데이트에 실패했습니다")
        }

        logger.info("메시지 수정 완료: messageId={}", messageId)

        return messageRepository.findById(messageId).orElse(message)
    }

    /**
     * 메시지 삭제 (soft delete)
     *
     * @param messageId 메시지 ID
     * @param userId 요청 사용자 ID
     * @throws MessageNotFoundException 메시지를 찾을 수 없는 경우
     * @throws MessageAccessDeniedException 메시지 삭제 권한이 없는 경우
     * @throws BadRequestException 이미 삭제된 메시지인 경우
     */
    fun deleteMessage(messageId: UUID, userId: UUID) {
        val message = messageRepository.findById(messageId).orElseThrow {
            MessageNotFoundException(messageId)
        }

        // 발신자 확인
        if (message.senderId != userId) {
            throw MessageAccessDeniedException("메시지를 삭제할 권한이 없습니다")
        }

        // 이미 삭제된 메시지
        if (message.status == MessageStatus.DELETED) {
            throw BadRequestException("이미 삭제된 메시지입니다")
        }

        // 메시지 삭제 (soft delete)
        val deleted = messageRepository.softDeleteMessage(messageId)
        if (!deleted) {
            throw BadRequestException("메시지 삭제에 실패했습니다")
        }

        logger.info("메시지 삭제 완료: messageId={}", messageId)
    }

    /**
     * 메시지 읽음 표시
     *
     * @param messageId 메시지 ID
     * @param userId 요청 사용자 ID
     * @return 이미 읽음 표시가 있었는지 여부
     * @throws MessageNotFoundException 메시지를 찾을 수 없는 경우
     * @throws ChannelAccessDeniedException 채널 접근 권한이 없는 경우
     */
    fun markMessageAsRead(messageId: UUID, userId: UUID): Boolean {
        val message = messageRepository.findById(messageId).orElseThrow {
            MessageNotFoundException(messageId)
        }

        validateChannelAccess(message.channelId, userId)

        // 이미 읽음 표시가 있는지 확인
        val existingRead = messageReadsRepository.findByMessageIdAndUserId(messageId, userId)
        if (existingRead != null) {
            return true // 이미 읽음
        }

        // 읽음 표시 생성
        val messageRead = MessageReads(
            messageId = messageId,
            channelId = message.channelId,
            userId = userId,
            readAt = LocalDateTime.now()
        )
        messageReadsRepository.save(messageRead)

        logger.debug("메시지 읽음 표시 완료: messageId={}, userId={}", messageId, userId)

        return false // 새로 읽음 표시됨
    }

    /**
     * 채널의 읽지 않은 메시지 개수 조회
     *
     * @param channelId 채널 ID
     * @param userId 요청 사용자 ID
     * @return 읽지 않은 메시지 개수
     * @throws ChannelAccessDeniedException 채널 접근 권한이 없는 경우
     */
    @Transactional(readOnly = true)
    fun getUnreadMessageCount(channelId: UUID, userId: UUID): Long {
        val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
        if (membership == null || !membership.isActive) {
            throw ChannelAccessDeniedException(channelId, userId)
        }

        val lastReadAt = membership.lastReadAt ?: LocalDateTime.of(1970, 1, 1, 0, 0)
        return messageReadsRepository.countUnreadMessagesByChannelAndUser(channelId, userId, lastReadAt)
    }

    /**
     * 사용자의 전체 읽지 않은 메시지 개수 조회
     *
     * @param userId 사용자 ID
     * @return 읽지 않은 메시지 총 개수
     */
    @Transactional(readOnly = true)
    fun getTotalUnreadMessageCount(userId: UUID): Long {
        val lastSeenAt = LocalDateTime.now().minusDays(7) // 최근 7일 기준
        return messageReadsRepository.countUnreadMessagesByUser(userId, lastSeenAt)
    }

    /**
     * 메시지 검색
     *
     * @param channelId 채널 ID
     * @param searchTerm 검색어
     * @param userId 요청 사용자 ID
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 페이징된 검색 결과
     * @throws ChannelAccessDeniedException 채널 접근 권한이 없는 경우
     */
    @Transactional(readOnly = true)
    fun searchMessages(
        channelId: UUID,
        searchTerm: String,
        userId: UUID,
        page: Int = 0,
        size: Int = 20
    ): PagedResponse<MessageResponse> {
        validateChannelAccess(channelId, userId)

        val pageable = PageRequest.of(page, size)
        val messages = messageRepository.searchMessagesByMetadata(channelId, searchTerm, pageable)

        return PagedResponse(
            content = messages.content.map { it.toResponse() },
            page = messages.number,
            size = messages.size,
            totalElements = messages.totalElements,
            totalPages = messages.totalPages,
            hasNext = messages.hasNext(),
            hasPrevious = messages.hasPrevious()
        )
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    /**
     * 채널 접근 권한 검증
     */
    private fun validateChannelAccess(channelId: UUID, userId: UUID) {
        val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
        if (membership == null || !membership.isActive) {
            throw ChannelAccessDeniedException(channelId, userId)
        }
    }

    /**
     * WebSocket으로 메시지 브로드캐스트 (best effort)
     */
    private fun broadcastMessage(message: Message) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/channel/${message.channelId}",
                message.toResponse()
            )
            logger.debug("WebSocket 브로드캐스트 완료: channelId={}", message.channelId)
        } catch (e: Exception) {
            logger.warn("WebSocket 브로드캐스트 실패 (계속 진행): channelId={}, error={}", message.channelId, e.message)
            // 브로드캐스트 실패해도 메시지는 저장되었으므로 예외를 던지지 않음
        }
    }

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
}
