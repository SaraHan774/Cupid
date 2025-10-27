package com.august.cupid.repository

import com.august.cupid.model.entity.MessageReads
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 읽음 표시 Repository (MongoDB)
 * 사용자별 메시지 읽음 상태 관리
 */
@Repository
interface MessageReadsRepository : MongoRepository<MessageReads, UUID> {

    /**
     * 메시지 ID로 읽음 표시들 조회
     */
    fun findByMessageId(messageId: UUID): List<MessageReads>

    /**
     * 사용자 ID로 읽음 표시들 조회
     */
    fun findByUserId(userId: UUID): List<MessageReads>

    /**
     * 채널 ID로 읽음 표시들 조회
     */
    fun findByChannelId(channelId: UUID): List<MessageReads>

    /**
     * 메시지와 사용자로 읽음 표시 조회
     */
    fun findByMessageIdAndUserId(messageId: UUID, userId: UUID): MessageReads?

    /**
     * 채널과 사용자로 읽음 표시들 조회
     */
    fun findByChannelIdAndUserIdOrderByReadAtDesc(channelId: UUID, userId: UUID): List<MessageReads>

    /**
     * 메시지 읽음 표시 존재 여부 확인
     */
    fun existsByMessageIdAndUserId(messageId: UUID, userId: UUID): Boolean

    /**
     * 채널의 특정 사용자 읽음 표시 개수 조회
     */
    fun countByChannelIdAndUserId(channelId: UUID, userId: UUID): Long

    /**
     * 메시지의 읽음 표시 개수 조회
     */
    fun countByMessageId(messageId: UUID): Long

    /**
     * 특정 시간 이후의 읽음 표시들 조회
     */
    fun findByReadAtAfterOrderByReadAtDesc(readAt: LocalDateTime): List<MessageReads>

    /**
     * 채널의 특정 시간 이후 읽음 표시들 조회
     */
    fun findByChannelIdAndReadAtAfterOrderByReadAtDesc(channelId: UUID, readAt: LocalDateTime): List<MessageReads>

    /**
     * 사용자의 특정 시간 이후 읽음 표시들 조회
     */
    fun findByUserIdAndReadAtAfterOrderByReadAtDesc(userId: UUID, readAt: LocalDateTime): List<MessageReads>

    /**
     * 채널의 최신 읽음 표시 조회
     */
    fun findFirstByChannelIdAndUserIdOrderByReadAtDesc(channelId: UUID, userId: UUID): MessageReads?

    /**
     * 메시지의 최신 읽음 표시 조회
     */
    fun findFirstByMessageIdOrderByReadAtDesc(messageId: UUID): MessageReads?

    /**
     * 특정 메시지들의 읽음 표시들 조회
     */
    fun findByMessageIdIn(messageIds: List<UUID>): List<MessageReads>

    /**
     * 특정 채널들의 읽음 표시들 조회
     */
    fun findByChannelIdIn(channelIds: List<UUID>): List<MessageReads>

    /**
     * 특정 사용자들의 읽음 표시들 조회
     */
    fun findByUserIdIn(userIds: List<UUID>): List<MessageReads>

    /**
     * 채널의 읽지 않은 메시지 개수 조회 (특정 시간 이후)
     */
    @Query(value = "{ 'channelId': ?0, 'userId': ?1, 'readAt': { '\$lt': ?2 } }", count = true)
    fun countUnreadMessagesByChannelAndUser(channelId: UUID, userId: UUID, since: LocalDateTime): Long

    /**
     * 사용자의 전체 읽지 않은 메시지 개수 조회
     */
    @Query(value = "{ 'userId': ?0, 'readAt': { '\$lt': ?1 } }", count = true)
    fun countUnreadMessagesByUser(userId: UUID, since: LocalDateTime): Long

    /**
     * 채널별 읽음 통계 조회
     */
    @Query(value = "{ 'channelId': ?0 }", count = true)
    fun countReadsByChannelId(channelId: UUID): Long

    /**
     * 사용자별 읽음 통계 조회
     */
    @Query(value = "{ 'userId': ?0 }", count = true)
    fun countReadsByUserId(userId: UUID): Long

    /**
     * 메시지별 읽음 통계 조회
     */
    @Query(value = "{ 'messageId': ?0 }", count = true)
    fun countReadsByMessageId(messageId: UUID): Long

    /**
     * 특정 시간 범위의 읽음 표시들 조회
     */
    fun findByReadAtBetweenOrderByReadAtDesc(startTime: LocalDateTime, endTime: LocalDateTime): List<MessageReads>

    /**
     * 채널의 특정 시간 범위 읽음 표시들 조회
     */
    fun findByChannelIdAndReadAtBetweenOrderByReadAtDesc(
        channelId: UUID, 
        startTime: LocalDateTime, 
        endTime: LocalDateTime
    ): List<MessageReads>

    /**
     * 사용자의 특정 시간 범위 읽음 표시들 조회
     */
    fun findByUserIdAndReadAtBetweenOrderByReadAtDesc(
        userId: UUID, 
        startTime: LocalDateTime, 
        endTime: LocalDateTime
    ): List<MessageReads>

    /**
     * 읽음 표시 삭제 (메시지 삭제 시)
     */
    fun deleteByMessageId(messageId: UUID): Long

    /**
     * 채널의 모든 읽음 표시 삭제 (채널 삭제 시)
     */
    fun deleteByChannelId(channelId: UUID): Long

    /**
     * 사용자의 모든 읽음 표시 삭제 (사용자 삭제 시)
     */
    fun deleteByUserId(userId: UUID): Long

    /**
     * 오래된 읽음 표시들 삭제 (정리 작업)
     */
    fun deleteByReadAtBefore(readAt: LocalDateTime): Long
}
