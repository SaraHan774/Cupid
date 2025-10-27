package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.Message
import com.august.cupid.model.entity.MessageStatus
import com.august.cupid.model.entity.MessageType
import com.august.cupid.model.entity.FileMetadata
import com.august.cupid.model.entity.EditHistory
import com.august.cupid.repository.MessageRepository
import com.august.cupid.repository.MessageReadsRepository
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 서비스
 * 메시지 관련 비즈니스 로직 처리
 */
@Service
@Transactional
class MessageService(
    private val messageRepository: MessageRepository,
    private val messageReadsRepository: MessageReadsRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 메시지 전송
     */
    fun sendMessage(request: SendMessageRequest, senderId: UUID): ApiResponse<MessageResponse> {
        return try {
            // 발신자 존재 확인
            val sender = userRepository.findById(senderId).orElse(null)
            if (sender == null) {
                return ApiResponse(false, message = "발신자를 찾을 수 없습니다")
            }

            // 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(request.channelId, senderId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "채널에 메시지를 보낼 권한이 없습니다")
            }

            // 답장 메시지 확인 (Message 엔티티에 replyToMessageId 필드가 없으므로 주석 처리)
            // if (request.replyToMessageId != null) {
            //     val replyMessage = messageRepository.findById(request.replyToMessageId).orElse(null)
            //     if (replyMessage == null || replyMessage.channelId != request.channelId) {
            //         return ApiResponse(false, message = "답장할 메시지를 찾을 수 없습니다")
            //     }
            // }

            // 파일 메타데이터 변환
            val fileMetadata = request.fileMetadata?.let { dto ->
                FileMetadata(
                    fileName = dto.fileName,
                    fileSize = dto.fileSize,
                    mimeType = dto.mimeType,
                    encryptedFileUrl = dto.fileUrl, // fileUrl을 encryptedFileUrl로 매핑
                    thumbnailUrl = dto.thumbnailUrl
                )
            }

            // 메시지 생성
            val message = Message(
                channelId = request.channelId,
                senderId = senderId,
                encryptedContent = request.encryptedContent,
                messageType = MessageType.valueOf(request.messageType.uppercase()),
                fileMetadata = fileMetadata
                // replyToMessageId 필드가 없으므로 제외
            )

            val savedMessage = messageRepository.save(message)

            logger.info("메시지 전송 완료: ${savedMessage.id} -> 채널 ${savedMessage.channelId}")

            ApiResponse(true, data = savedMessage.toResponse(), message = "메시지가 성공적으로 전송되었습니다")
        } catch (e: Exception) {
            logger.error("메시지 전송 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 전송 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널의 메시지 목록 조회
     */
    @Transactional(readOnly = true)
    fun getChannelMessages(
        channelId: UUID, 
        userId: UUID, 
        page: Int = 0, 
        size: Int = 50
    ): ApiResponse<PagedResponse<MessageResponse>> {
        return try {
            // 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "채널에 접근할 권한이 없습니다")
            }

            val pageable = PageRequest.of(page, size)
            val messages = messageRepository.findByChannelIdAndStatusNotOrderByCreatedAtDesc(
                channelId, 
                MessageStatus.DELETED, 
                pageable
            )
            
            val messageResponses = messages.content.map { it.toResponse() }
            val pagedResponse = PagedResponse(
                content = messageResponses,
                page = messages.number,
                size = messages.size,
                totalElements = messages.totalElements,
                totalPages = messages.totalPages,
                hasNext = messages.hasNext(),
                hasPrevious = messages.hasPrevious()
            )

            ApiResponse(true, data = pagedResponse)
        } catch (e: Exception) {
            logger.error("채널 메시지 목록 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 메시지 목록 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 메시지 ID로 조회
     */
    @Transactional(readOnly = true)
    fun getMessageById(messageId: UUID, userId: UUID): ApiResponse<MessageResponse> {
        return try {
            val message = messageRepository.findById(messageId).orElse(null)
            if (message == null) {
                return ApiResponse(false, message = "메시지를 찾을 수 없습니다")
            }

            // 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(message.channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "메시지에 접근할 권한이 없습니다")
            }

            ApiResponse(true, data = message.toResponse())
        } catch (e: Exception) {
            logger.error("메시지 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 메시지 수정
     */
    fun editMessage(messageId: UUID, newContent: String, userId: UUID): ApiResponse<MessageResponse> {
        return try {
            val message = messageRepository.findById(messageId).orElse(null)
            if (message == null) {
                return ApiResponse(false, message = "메시지를 찾을 수 없습니다")
            }

            // 발신자 확인
            if (message.senderId != userId) {
                return ApiResponse(false, message = "메시지를 수정할 권한이 없습니다")
            }

            // 삭제된 메시지는 수정 불가
            if (message.status == MessageStatus.DELETED) {
                return ApiResponse(false, message = "삭제된 메시지는 수정할 수 없습니다")
            }

            // 수정 이력 추가
            val editHistory = EditHistory(
                encryptedContent = message.encryptedContent,
                editedAt = LocalDateTime.now()
            )

            // 메시지 내용 업데이트
            messageRepository.updateMessageContent(messageId, newContent, LocalDateTime.now(), editHistory)

            // 업데이트된 메시지 조회
            val updatedMessage = messageRepository.findById(messageId).orElse(message)
            // Message는 data class이므로 새 인스턴스 생성 필요
            // 실제로는 별도의 업데이트 메서드가 필요할 수 있음

            logger.info("메시지 수정 완료: ${message.id}")

            ApiResponse(true, data = updatedMessage.toResponse(), message = "메시지가 성공적으로 수정되었습니다")
        } catch (e: Exception) {
            logger.error("메시지 수정 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 수정 중 오류가 발생했습니다")
        }
    }

    /**
     * 메시지 삭제
     */
    fun deleteMessage(messageId: UUID, userId: UUID): ApiResponse<String> {
        return try {
            val message = messageRepository.findById(messageId).orElse(null)
            if (message == null) {
                return ApiResponse(false, message = "메시지를 찾을 수 없습니다")
            }

            // 발신자 확인
            if (message.senderId != userId) {
                return ApiResponse(false, message = "메시지를 삭제할 권한이 없습니다")
            }

            // 이미 삭제된 메시지
            if (message.status == MessageStatus.DELETED) {
                return ApiResponse(false, message = "이미 삭제된 메시지입니다")
            }

            // 메시지 삭제 (soft delete)
            messageRepository.deleteMessage(messageId, LocalDateTime.now(), LocalDateTime.now())

            logger.info("메시지 삭제 완료: ${message.id}")

            ApiResponse(true, message = "메시지가 성공적으로 삭제되었습니다")
        } catch (e: Exception) {
            logger.error("메시지 삭제 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 삭제 중 오류가 발생했습니다")
        }
    }

    /**
     * 메시지 읽음 표시
     */
    fun markMessageAsRead(messageId: UUID, userId: UUID): ApiResponse<String> {
        return try {
            val message = messageRepository.findById(messageId).orElse(null)
            if (message == null) {
                return ApiResponse(false, message = "메시지를 찾을 수 없습니다")
            }

            // 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(message.channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "메시지에 접근할 권한이 없습니다")
            }

            // 이미 읽음 표시가 있는지 확인
            val existingRead = messageReadsRepository.findByMessageIdAndUserId(messageId, userId)
            if (existingRead != null) {
                return ApiResponse(true, message = "이미 읽음 표시가 되어 있습니다")
            }

            // 읽음 표시 생성
            val messageRead = com.august.cupid.model.entity.MessageReads(
                messageId = messageId,
                channelId = message.channelId,
                userId = userId,
                readAt = LocalDateTime.now()
            )
            messageReadsRepository.save(messageRead)

            logger.info("메시지 읽음 표시 완료: ${message.id} -> 사용자 ${userId}")

            ApiResponse(true, message = "메시지 읽음 표시가 완료되었습니다")
        } catch (e: Exception) {
            logger.error("메시지 읽음 표시 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 읽음 표시 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널의 읽지 않은 메시지 개수 조회
     */
    @Transactional(readOnly = true)
    fun getUnreadMessageCount(channelId: UUID, userId: UUID): ApiResponse<Long> {
        return try {
            // 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "채널에 접근할 권한이 없습니다")
            }

            val lastReadAt = membership.lastReadAt ?: LocalDateTime.of(1970, 1, 1, 0, 0)
            val unreadCount = messageReadsRepository.countUnreadMessagesByChannelAndUser(channelId, userId, lastReadAt)

            ApiResponse(true, data = unreadCount)
        } catch (e: Exception) {
            logger.error("읽지 않은 메시지 개수 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "읽지 않은 메시지 개수 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자의 전체 읽지 않은 메시지 개수 조회
     */
    @Transactional(readOnly = true)
    fun getTotalUnreadMessageCount(userId: UUID): ApiResponse<Long> {
        return try {
            val lastSeenAt = LocalDateTime.now().minusDays(7) // 최근 7일 기준
            val unreadCount = messageReadsRepository.countUnreadMessagesByUser(userId, lastSeenAt)

            ApiResponse(true, data = unreadCount)
        } catch (e: Exception) {
            logger.error("전체 읽지 않은 메시지 개수 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "전체 읽지 않은 메시지 개수 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 메시지 검색
     */
    @Transactional(readOnly = true)
    fun searchMessages(
        channelId: UUID, 
        searchTerm: String, 
        userId: UUID, 
        page: Int = 0, 
        size: Int = 20
    ): ApiResponse<PagedResponse<MessageResponse>> {
        return try {
            // 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "채널에 접근할 권한이 없습니다")
            }

            val pageable = PageRequest.of(page, size)
            val messages = messageRepository.searchMessagesByMetadata(channelId, searchTerm, pageable)
            
            val messageResponses = messages.content.map { it.toResponse() }
            val pagedResponse = PagedResponse(
                content = messageResponses,
                page = messages.number,
                size = messages.size,
                totalElements = messages.totalElements,
                totalPages = messages.totalPages,
                hasNext = messages.hasNext(),
                hasPrevious = messages.hasPrevious()
            )

            ApiResponse(true, data = pagedResponse)
        } catch (e: Exception) {
            logger.error("메시지 검색 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 검색 중 오류가 발생했습니다")
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
                    fileUrl = metadata.encryptedFileUrl, // encryptedFileUrl을 fileUrl로 매핑
                    thumbnailUrl = metadata.thumbnailUrl
                )
            },
            replyToMessageId = null, // Message 엔티티에 replyToMessageId 필드가 없음
            status = this.status.name,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
