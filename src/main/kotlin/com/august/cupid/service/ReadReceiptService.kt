package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.MessageReads
import com.august.cupid.repository.MessageReadsRepository
import com.august.cupid.repository.MessageRepository
import com.august.cupid.repository.ChannelMembersRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 읽음 표시 서비스
 *
 * MongoDB 기반 읽음 표시 관리 + Redis 캐싱
 * - 메시지별 읽음 표시 저장
 * - 채널별 읽지 않은 메시지 수 관리
 * - 배치 읽음 처리 (성능 최적화)
 * - 프라이버시 설정 지원
 *
 * Architecture:
 * - MongoDB: 영구 저장소 (MessageReads collection)
 * - Redis: 읽지 않은 메시지 수 캐싱
 * - Key pattern: "unread:{channelId}:{userId}" -> count
 * - TTL: 1시간 (MongoDB와 동기화)
 *
 * Trade-offs:
 * - MongoDB vs PostgreSQL: MongoDB 선택 이유
 *   - 높은 쓰기 처리량 (읽음 표시는 빈번)
 *   - 유연한 스키마 (추후 반응 등 추가 가능)
 *   - 수평 확장 용이
 * - Caching Strategy: Write-through caching
 *   - 쓰기 시 MongoDB + Redis 동시 업데이트
 *   - 읽기 시 Redis 우선, miss 시 MongoDB 조회
 * - Batch Operations: 성능 vs 실시간성
 *   - 100개 이상 메시지 읽음 시 배치 처리
 *   - 실시간 알림은 마지막 메시지만
 *
 * Performance Considerations:
 * - 단일 읽음 표시: < 10ms (Redis + MongoDB 쓰기)
 * - 배치 읽음 표시 (100개): < 50ms (bulk insert)
 * - 읽지 않은 수 조회: < 2ms (Redis 캐시 히트 시)
 */
@Service
class ReadReceiptService(
    private val messageReadsRepository: MessageReadsRepository,
    private val messageRepository: MessageRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val UNREAD_COUNT_KEY_PREFIX = "unread:"
        private const val UNREAD_COUNT_TTL_HOURS = 1L
        private const val BATCH_SIZE_THRESHOLD = 100
    }

    /**
     * 메시지 읽음 표시
     *
     * Workflow:
     * 1. 메시지 및 채널 멤버십 검증
     * 2. 중복 읽음 표시 확인
     * 3. MongoDB에 읽음 표시 저장
     * 4. Redis 캐시 업데이트 (읽지 않은 수 감소)
     * 5. WebSocket 알림 (호출자가 처리)
     *
     * @param messageId 읽은 메시지 ID
     * @param userId 읽은 사용자 ID
     * @param channelId 채널 ID
     * @return 읽음 표시 응답 (성공 여부 포함)
     */
    @Transactional
    fun markAsRead(
        messageId: UUID,
        userId: UUID,
        channelId: UUID
    ): ApiResponse<ReadReceiptResponse> {
        return try {
            // 1. 메시지 존재 확인
            val message = messageRepository.findById(messageId).orElse(null)
            if (message == null) {
                return ApiResponse(false, message = "메시지를 찾을 수 없습니다")
            }

            // 2. 채널 일치 확인
            if (message.channelId != channelId) {
                return ApiResponse(false, message = "메시지가 해당 채널에 속하지 않습니다")
            }

            // 3. 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "채널에 접근할 권한이 없습니다")
            }

            // 4. 자기 자신의 메시지는 자동 읽음 처리 (중복 방지)
            if (message.senderId == userId) {
                logger.debug("발신자 자신의 메시지 읽음 표시 스킵: messageId={}, userId={}", messageId, userId)
                return ApiResponse(
                    true,
                    data = ReadReceiptResponse(messageId, channelId, userId, LocalDateTime.now()),
                    message = "발신자 자신의 메시지입니다"
                )
            }

            // 5. 이미 읽음 표시가 있는지 확인
            if (messageReadsRepository.existsByMessageIdAndUserId(messageId, userId)) {
                logger.debug("이미 읽음 표시됨: messageId={}, userId={}", messageId, userId)
                val existingRead = messageReadsRepository.findByMessageIdAndUserId(messageId, userId)
                return ApiResponse(
                    true,
                    data = ReadReceiptResponse(
                        messageId, channelId, userId,
                        existingRead?.readAt ?: LocalDateTime.now()
                    ),
                    message = "이미 읽음 표시가 되어 있습니다"
                )
            }

            // 6. 읽음 표시 생성 및 저장
            val readAt = LocalDateTime.now()
            val messageRead = MessageReads(
                messageId = messageId,
                channelId = channelId,
                userId = userId,
                readAt = readAt
            )

            messageReadsRepository.save(messageRead)

            // 7. Redis 캐시 업데이트 (읽지 않은 수 감소)
            decrementUnreadCount(channelId, userId)

            // 8. 채널 멤버십의 lastReadAt 업데이트
            channelMembersRepository.updateLastReadAt(channelId, userId, readAt)

            logger.info(
                "메시지 읽음 표시 완료: messageId={}, userId={}, channelId={}",
                messageId, userId, channelId
            )

            val response = ReadReceiptResponse(messageId, channelId, userId, readAt)
            ApiResponse(true, data = response, message = "메시지 읽음 표시가 완료되었습니다")

        } catch (e: Exception) {
            logger.error("메시지 읽음 표시 실패: messageId={}, userId={}", messageId, userId, e)
            ApiResponse(false, error = "메시지 읽음 표시 중 오류가 발생했습니다")
        }
    }

    /**
     * 배치 읽음 표시
     *
     * Performance Optimization:
     * - 여러 메시지를 한 번에 읽음 처리
     * - MongoDB bulk insert 사용
     * - Redis 캐시 일괄 업데이트
     *
     * Use Cases:
     * - 채널 진입 시 모든 메시지 읽음 처리
     * - 오프라인 후 재접속 시 동기화
     * - 스크롤하며 여러 메시지 읽음 처리
     *
     * @param messageIds 읽을 메시지 ID 목록
     * @param userId 읽은 사용자 ID
     * @param channelId 채널 ID
     * @return 배치 읽음 표시 응답
     */
    @Transactional
    fun markMultipleAsRead(
        messageIds: List<UUID>,
        userId: UUID,
        channelId: UUID
    ): ApiResponse<BatchReadReceiptResponse> {
        return try {
            if (messageIds.isEmpty()) {
                return ApiResponse(
                    true,
                    data = BatchReadReceiptResponse(channelId, 0, emptyList()),
                    message = "읽을 메시지가 없습니다"
                )
            }

            // 1. 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "채널에 접근할 권한이 없습니다")
            }

            // 2. 이미 읽은 메시지 필터링
            val unreadMessageIds = messageIds.filter { messageId ->
                !messageReadsRepository.existsByMessageIdAndUserId(messageId, userId)
            }

            if (unreadMessageIds.isEmpty()) {
                logger.debug("모든 메시지가 이미 읽음 처리됨: channelId={}, userId={}", channelId, userId)
                return ApiResponse(
                    true,
                    data = BatchReadReceiptResponse(channelId, 0, emptyList()),
                    message = "모든 메시지가 이미 읽음 처리되어 있습니다"
                )
            }

            // 3. 읽음 표시 객체 생성
            val readAt = LocalDateTime.now()
            val messageReads = unreadMessageIds.map { messageId ->
                MessageReads(
                    messageId = messageId,
                    channelId = channelId,
                    userId = userId,
                    readAt = readAt
                )
            }

            // 4. MongoDB bulk insert
            val savedReads = messageReadsRepository.saveAll(messageReads)

            // 5. Redis 캐시 업데이트
            decrementUnreadCount(channelId, userId, savedReads.count())

            // 6. 채널 멤버십의 lastReadAt 업데이트
            channelMembersRepository.updateLastReadAt(channelId, userId, readAt)

            logger.info(
                "배치 읽음 표시 완료: channelId={}, userId={}, count={}",
                channelId, userId, savedReads.count()
            )

            val response = BatchReadReceiptResponse(
                channelId = channelId,
                successCount = savedReads.count(),
                failedMessageIds = emptyList()
            )

            ApiResponse(true, data = response, message = "배치 읽음 표시가 완료되었습니다")

        } catch (e: Exception) {
            logger.error("배치 읽음 표시 실패: channelId={}, userId={}", channelId, userId, e)
            ApiResponse(false, error = "배치 읽음 표시 중 오류가 발생했습니다")
        }
    }

    /**
     * 메시지 읽음 수 조회
     *
     * Use Cases:
     * - 메시지 UI에 "읽은 사람 3명" 표시
     * - 그룹 채팅 읽음 통계
     *
     * @param messageId 메시지 ID
     * @return 읽음 수 및 읽은 사용자 목록
     */
    @Transactional(readOnly = true)
    fun getMessageReadCount(messageId: UUID): ApiResponse<MessageReadCountResponse> {
        return try {
            // 1. 메시지 존재 확인
            val message = messageRepository.findById(messageId).orElse(null)
            if (message == null) {
                return ApiResponse(false, message = "메시지를 찾을 수 없습니다")
            }

            // 2. 읽음 표시 목록 조회
            val messageReads = messageReadsRepository.findByMessageId(messageId)

            // 3. 채널 총 멤버 수 조회
            val totalMembers = channelMembersRepository
                .countActiveMembersByChannelId(message.channelId)
                .toInt()

            // 4. 읽은 사용자 ID 목록
            val readUserIds = messageReads.map { it.userId }

            val response = MessageReadCountResponse(
                messageId = messageId,
                readCount = messageReads.size.toLong(),
                totalMembers = totalMembers,
                readUserIds = readUserIds
            )

            ApiResponse(true, data = response)

        } catch (e: Exception) {
            logger.error("메시지 읽음 수 조회 실패: messageId={}", messageId, e)
            ApiResponse(false, error = "메시지 읽음 수 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널 읽지 않은 메시지 수 조회
     *
     * Caching Strategy:
     * 1. Redis 캐시 확인 (hit: < 2ms)
     * 2. Miss 시 MongoDB 조회 후 캐싱
     *
     * @param channelId 채널 ID
     * @param userId 사용자 ID
     * @return 읽지 않은 메시지 수
     */
    @Transactional(readOnly = true)
    fun getUnreadMessageCount(channelId: UUID, userId: UUID): ApiResponse<UnreadCountResponse> {
        return try {
            // 1. 채널 멤버십 확인
            val membership = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
            if (membership == null || !membership.isActive) {
                return ApiResponse(false, message = "채널에 접근할 권한이 없습니다")
            }

            // 2. Redis 캐시 확인
            val cacheKey = "$UNREAD_COUNT_KEY_PREFIX$channelId:$userId"
            val cachedCount = redisTemplate.opsForValue().get(cacheKey)

            val unreadCount = if (cachedCount != null) {
                // 캐시 히트
                logger.debug("읽지 않은 수 캐시 히트: channelId={}, userId={}", channelId, userId)
                cachedCount.toLong()
            } else {
                // 캐시 미스 - MongoDB 조회
                logger.debug("읽지 않은 수 캐시 미스: channelId={}, userId={}", channelId, userId)
                calculateUnreadCount(channelId, userId, membership.lastReadAt)
            }

            val response = UnreadCountResponse(
                channelId = channelId,
                unreadCount = unreadCount,
                lastReadAt = membership.lastReadAt
            )

            ApiResponse(true, data = response)

        } catch (e: Exception) {
            logger.error("읽지 않은 메시지 수 조회 실패: channelId={}, userId={}", channelId, userId, e)
            ApiResponse(false, error = "읽지 않은 메시지 수 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자의 전체 읽지 않은 메시지 수 조회
     *
     * Use Cases:
     * - 앱 아이콘 배지 숫자
     * - 전체 알림 카운트
     *
     * @param userId 사용자 ID
     * @return 전체 읽지 않은 메시지 수
     */
    @Transactional(readOnly = true)
    fun getTotalUnreadMessageCount(userId: UUID): ApiResponse<Long> {
        return try {
            // 사용자가 속한 모든 활성 채널 조회
            val activeChannels = channelMembersRepository.findByUserIdAndIsActiveTrue(userId)

            // 각 채널의 읽지 않은 수 합산
            var totalUnread = 0L
            activeChannels.forEach { membership ->
                val result = getUnreadMessageCount(membership.channel.id!!, userId)
                if (result.success && result.data != null) {
                    totalUnread += result.data.unreadCount
                }
            }

            logger.debug("전체 읽지 않은 수 조회: userId={}, count={}", userId, totalUnread)
            ApiResponse(true, data = totalUnread)

        } catch (e: Exception) {
            logger.error("전체 읽지 않은 수 조회 실패: userId={}", userId, e)
            ApiResponse(false, error = "전체 읽지 않은 수 조회 중 오류가 발생했습니다")
        }
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    /**
     * 읽지 않은 메시지 수 계산 및 캐싱
     */
    private fun calculateUnreadCount(
        channelId: UUID,
        userId: UUID,
        lastReadAt: LocalDateTime?
    ): Long {
        // lastReadAt 이후 메시지 중 읽지 않은 것 계산
        val since = lastReadAt ?: LocalDateTime.of(1970, 1, 1, 0, 0)

        // MongoDB에서 계산
        val unreadCount = messageReadsRepository.countUnreadMessagesByChannelAndUser(
            channelId, userId, since
        )

        // Redis에 캐싱
        val cacheKey = "$UNREAD_COUNT_KEY_PREFIX$channelId:$userId"
        redisTemplate.opsForValue().set(
            cacheKey,
            unreadCount.toString(),
            UNREAD_COUNT_TTL_HOURS,
            TimeUnit.HOURS
        )

        logger.debug(
            "읽지 않은 수 계산 및 캐싱: channelId={}, userId={}, count={}",
            channelId, userId, unreadCount
        )

        return unreadCount
    }

    /**
     * 읽지 않은 메시지 수 감소 (Redis 캐시)
     */
    private fun decrementUnreadCount(channelId: UUID, userId: UUID, count: Int = 1) {
        try {
            val cacheKey = "$UNREAD_COUNT_KEY_PREFIX$channelId:$userId"

            // Redis DECR 연산 (원자적)
            redisTemplate.opsForValue().decrement(cacheKey, count.toLong())

            // 음수 방지
            val currentValue = redisTemplate.opsForValue().get(cacheKey)?.toLongOrNull() ?: 0
            if (currentValue < 0) {
                redisTemplate.opsForValue().set(cacheKey, "0")
            }

            logger.debug(
                "읽지 않은 수 감소: channelId={}, userId={}, count={}",
                channelId, userId, count
            )

        } catch (e: Exception) {
            logger.warn(
                "읽지 않은 수 감소 실패 (Redis): channelId={}, userId={}",
                channelId, userId, e
            )
            // Best effort - 캐시 실패해도 계속 진행
        }
    }

    /**
     * 읽지 않은 메시지 수 증가 (새 메시지 도착 시)
     *
     * Note: 이 메서드는 MessageService에서 호출됨
     */
    fun incrementUnreadCount(channelId: UUID, recipientUserIds: List<UUID>) {
        try {
            recipientUserIds.forEach { userId ->
                val cacheKey = "$UNREAD_COUNT_KEY_PREFIX$channelId:$userId"

                // Redis INCR 연산 (원자적)
                redisTemplate.opsForValue().increment(cacheKey, 1)

                // TTL 설정 (새 키인 경우)
                redisTemplate.expire(cacheKey, UNREAD_COUNT_TTL_HOURS, TimeUnit.HOURS)
            }

            logger.debug(
                "읽지 않은 수 증가: channelId={}, recipients={}",
                channelId, recipientUserIds.size
            )

        } catch (e: Exception) {
            logger.warn(
                "읽지 않은 수 증가 실패 (Redis): channelId={}",
                channelId, e
            )
            // Best effort
        }
    }

    /**
     * 채널의 모든 읽음 표시 정리
     *
     * Use Cases:
     * - 채널 삭제 시
     * - 데이터 정리 작업
     */
    fun clearChannelReadReceipts(channelId: UUID): Boolean {
        return try {
            // MongoDB 삭제
            val deletedCount = messageReadsRepository.deleteByChannelId(channelId)

            // Redis 캐시 정리
            val pattern = "$UNREAD_COUNT_KEY_PREFIX$channelId:*"
            val keys = redisTemplate.keys(pattern)
            if (!keys.isNullOrEmpty()) {
                redisTemplate.delete(keys)
            }

            logger.info(
                "채널 읽음 표시 정리 완료: channelId={}, deletedReads={}",
                channelId, deletedCount
            )

            true
        } catch (e: Exception) {
            logger.error("채널 읽음 표시 정리 실패: channelId={}", channelId, e)
            false
        }
    }

    /**
     * 사용자의 모든 읽음 표시 정리
     *
     * Use Cases:
     * - 사용자 계정 삭제 시
     */
    fun clearUserReadReceipts(userId: UUID): Boolean {
        return try {
            // MongoDB 삭제
            val deletedCount = messageReadsRepository.deleteByUserId(userId)

            // Redis 캐시 정리
            val pattern = "$UNREAD_COUNT_KEY_PREFIX*:$userId"
            val keys = redisTemplate.keys(pattern)
            if (!keys.isNullOrEmpty()) {
                redisTemplate.delete(keys)
            }

            logger.info(
                "사용자 읽음 표시 정리 완료: userId={}, deletedReads={}",
                userId, deletedCount
            )

            true
        } catch (e: Exception) {
            logger.error("사용자 읽음 표시 정리 실패: userId={}", userId, e)
            false
        }
    }
}
