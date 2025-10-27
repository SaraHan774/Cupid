package com.august.cupid.repository

import com.august.cupid.model.entity.Message
import com.august.cupid.model.entity.MessageStatus
import com.august.cupid.model.entity.MessageType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 Repository (MongoDB)
 * E2E 암호화된 메시지 데이터 관리
 */
@Repository
interface MessageRepository : MongoRepository<Message, UUID> {

    /**
     * 채널 ID로 메시지들 조회 (페이징, 삭제되지 않은 메시지만)
     */
    fun findByChannelIdAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID, 
        status: MessageStatus, 
        pageable: Pageable
    ): Page<Message>

    /**
     * 채널의 최신 메시지 조회
     */
    fun findFirstByChannelIdAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID, 
        status: MessageStatus
    ): Message?

    /**
     * 발신자 ID로 메시지들 조회
     */
    fun findBySenderIdOrderByCreatedAtDesc(senderId: UUID, pageable: Pageable): Page<Message>

    /**
     * 메시지 타입별 조회
     */
    fun findByChannelIdAndMessageTypeAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID, 
        messageType: MessageType, 
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>

    /**
     * 특정 시간 이후의 메시지들 조회
     */
    fun findByChannelIdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID, 
        createdAt: LocalDateTime, 
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>

    /**
     * 특정 시간 이전의 메시지들 조회 (페이징)
     */
    fun findByChannelIdAndCreatedAtBeforeAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID, 
        createdAt: LocalDateTime, 
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>

    /**
     * 채널의 메시지 개수 조회
     */
    fun countByChannelIdAndStatusNot(channelId: UUID, status: MessageStatus): Long

    /**
     * 발신자의 메시지 개수 조회
     */
    fun countBySenderId(senderId: UUID): Long

    /**
     * 특정 시간 이후의 메시지 개수 조회
     */
    fun countByChannelIdAndCreatedAtAfterAndStatusNot(
        channelId: UUID, 
        createdAt: LocalDateTime, 
        status: MessageStatus
    ): Long

    /**
     * 메시지 상태 업데이트
     */
    @Query("{ 'id': ?0 }, { '\$set': { 'status': ?1, 'updatedAt': ?2 } }")
    fun updateMessageStatus(messageId: UUID, status: MessageStatus, updatedAt: LocalDateTime): Void

    /**
     * 메시지 삭제 (soft delete)
     */
    @Query("{ 'id': ?0 }, { '\$set': { 'status': 'DELETED', 'deletedAt': ?1, 'updatedAt': ?2 } }")
    fun deleteMessage(messageId: UUID, deletedAt: LocalDateTime, updatedAt: LocalDateTime): Void

    /**
     * 메시지 내용 업데이트 (수정)
     */
    @Query("{ 'id': ?0 }, { '\$set': { 'encryptedContent': ?1, 'updatedAt': ?2 }, '\$push': { 'editHistory': ?3 } }")
    fun updateMessageContent(
        messageId: UUID, 
        encryptedContent: String, 
        updatedAt: LocalDateTime, 
        editHistory: com.august.cupid.model.entity.EditHistory
    ): Void

    /**
     * 채널의 모든 메시지 삭제
     */
    fun deleteByChannelId(channelId: UUID): Long

    /**
     * 특정 시간 이전의 오래된 메시지들 삭제
     */
    fun deleteByCreatedAtBefore(createdAt: LocalDateTime): Long

    /**
     * 파일 메타데이터가 있는 메시지들 조회
     */
    fun findByFileMetadataIsNotNullAndStatusNotOrderByCreatedAtDesc(
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>

    /**
     * 특정 파일 타입의 메시지들 조회
     */
    fun findByChannelIdAndFileMetadataMimeTypeContainingAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID,
        mimeType: String,
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>

    /**
     * 메시지 검색 (메타데이터 기반)
     */
    @Query("{ 'channelId': ?0, 'metadata': { '\$regex': ?1, '\$options': 'i' }, 'status': { '\$ne': 'DELETED' } }")
    fun searchMessagesByMetadata(channelId: UUID, searchTerm: String, pageable: Pageable): Page<Message>

    /**
     * 채널별 메시지 통계 조회
     */
    @Query(value = "{ 'channelId': ?0, 'status': { '\$ne': 'DELETED' } }", count = true)
    fun countMessagesByChannelId(channelId: UUID): Long

    /**
     * 발신자별 메시지 통계 조회
     */
    @Query(value = "{ 'senderId': ?0 }", count = true)
    fun countMessagesBySenderId(senderId: UUID): Long

    /**
     * 특정 시간 범위의 메시지들 조회
     */
    fun findByChannelIdAndCreatedAtBetweenAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>
}
