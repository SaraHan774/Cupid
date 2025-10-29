package com.august.cupid.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 타이핑 인디케이터 서비스
 *
 * Redis 기반 실시간 타이핑 상태 관리
 * - 채널별 타이핑 중인 사용자 추적
 * - 자동 만료 (TTL: 10초)
 * - 동시 타이핑 사용자 관리
 *
 * Architecture:
 * - Redis Set을 사용하여 채널당 타이핑 중인 사용자들 저장
 * - Key pattern: "typing:{channelId}"
 * - Value: Set of userId strings
 * - TTL: 10초 (타이핑 활동이 없으면 자동 제거)
 *
 * Trade-offs:
 * - Redis Set vs String: Set 사용으로 동시 타이핑자 효율적 관리
 * - TTL per user vs per channel: 채널별 TTL로 단순화 (성능 우선)
 * - Pub/Sub vs Polling: WebSocket을 통한 Push 방식 (낮은 지연시간)
 */
@Service
class TypingIndicatorService(
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TYPING_KEY_PREFIX = "typing:"
        private const val TYPING_TTL_SECONDS = 10L

        // 개별 사용자 타이핑 상태를 위한 키 패턴
        private const val USER_TYPING_KEY_PREFIX = "typing:user:"
    }

    /**
     * 사용자 타이핑 시작 설정
     *
     * 채널의 타이핑 Set에 사용자 추가하고 TTL 갱신
     *
     * Design Decisions:
     * - Set 자료구조 사용: 중복 방지 및 O(1) 조회
     * - TTL 자동 갱신: 마지막 타이핑 활동 기준으로 만료
     * - 원자적 연산: Redis 트랜잭션 없이도 안전한 Set 연산
     *
     * @param channelId 채널 ID
     * @param userId 타이핑 중인 사용자 ID
     * @return 성공 여부
     */
    fun setTyping(channelId: UUID, userId: UUID): Boolean {
        return try {
            val channelKey = "$TYPING_KEY_PREFIX$channelId"
            val userKey = "$USER_TYPING_KEY_PREFIX$channelId:$userId"

            // 1. 채널별 타이핑 Set에 사용자 추가
            redisTemplate.opsForSet().add(channelKey, userId.toString())

            // 2. 채널 키의 TTL 갱신 (마지막 타이핑 활동 기준)
            redisTemplate.expire(channelKey, TYPING_TTL_SECONDS, TimeUnit.SECONDS)

            // 3. 개별 사용자 타이핑 상태도 저장 (연결 해제 감지용)
            redisTemplate.opsForValue().set(
                userKey,
                "1",
                TYPING_TTL_SECONDS,
                TimeUnit.SECONDS
            )

            logger.debug(
                "사용자 타이핑 시작 설정: channelId={}, userId={}, TTL={}초",
                channelId, userId, TYPING_TTL_SECONDS
            )

            true
        } catch (e: Exception) {
            logger.error("사용자 타이핑 시작 설정 실패: channelId={}, userId={}", channelId, userId, e)
            false
        }
    }

    /**
     * 사용자 타이핑 중지 설정
     *
     * 채널의 타이핑 Set에서 사용자 제거
     *
     * Edge Cases:
     * - 이미 만료된 키: 조용히 무시
     * - 존재하지 않는 사용자: 안전하게 처리
     * - 동시 요청: Set 연산으로 안전
     *
     * @param channelId 채널 ID
     * @param userId 타이핑 중지한 사용자 ID
     * @return 성공 여부
     */
    fun removeTyping(channelId: UUID, userId: UUID): Boolean {
        return try {
            val channelKey = "$TYPING_KEY_PREFIX$channelId"
            val userKey = "$USER_TYPING_KEY_PREFIX$channelId:$userId"

            // 1. 채널별 타이핑 Set에서 사용자 제거
            val removed = redisTemplate.opsForSet().remove(channelKey, userId.toString())

            // 2. 개별 사용자 타이핑 상태 제거
            redisTemplate.delete(userKey)

            logger.debug(
                "사용자 타이핑 중지 설정: channelId={}, userId={}, removed={}",
                channelId, userId, removed
            )

            true
        } catch (e: Exception) {
            logger.error("사용자 타이핑 중지 설정 실패: channelId={}, userId={}", channelId, userId, e)
            false
        }
    }

    /**
     * 채널의 현재 타이핑 중인 사용자 목록 조회
     *
     * Performance Considerations:
     * - Set 크기 제한: 일반적으로 소규모 (< 10명)
     * - O(N) 복잡도: N = 타이핑 중인 사용자 수
     * - 메모리 효율: 만료된 항목 자동 정리
     *
     * @param channelId 채널 ID
     * @return 타이핑 중인 사용자 ID 목록
     */
    fun getTypingUsers(channelId: UUID): List<String> {
        return try {
            val key = "$TYPING_KEY_PREFIX$channelId"
            val typingUsers = redisTemplate.opsForSet().members(key)?.toList() ?: emptyList()

            logger.debug(
                "채널 타이핑 사용자 조회: channelId={}, count={}",
                channelId, typingUsers.size
            )

            typingUsers
        } catch (e: Exception) {
            logger.error("채널 타이핑 사용자 조회 실패: channelId={}", channelId, e)
            emptyList()
        }
    }

    /**
     * 사용자가 특정 채널에서 타이핑 중인지 확인
     *
     * Use Cases:
     * - 클라이언트 상태 동기화
     * - 중복 이벤트 필터링
     * - 디버깅 및 모니터링
     *
     * @param channelId 채널 ID
     * @param userId 확인할 사용자 ID
     * @return 타이핑 중 여부
     */
    fun isUserTyping(channelId: UUID, userId: UUID): Boolean {
        return try {
            val key = "$TYPING_KEY_PREFIX$channelId"
            val isTyping = redisTemplate.opsForSet().isMember(key, userId.toString()) ?: false

            logger.debug(
                "사용자 타이핑 상태 확인: channelId={}, userId={}, isTyping={}",
                channelId, userId, isTyping
            )

            isTyping
        } catch (e: Exception) {
            logger.error("사용자 타이핑 상태 확인 실패: channelId={}, userId={}", channelId, userId, e)
            false
        }
    }

    /**
     * 사용자의 모든 채널에서 타이핑 상태 제거
     *
     * Use Cases:
     * - WebSocket 연결 해제 시
     * - 사용자 로그아웃 시
     * - 네트워크 장애 복구 시
     *
     * Implementation Notes:
     * - 모든 채널을 스캔하지 않고 개별 사용자 키만 정리
     * - 채널별 Set은 TTL로 자동 정리되므로 일부 지연 허용
     * - 성능 최적화: O(1) 연산으로 처리
     *
     * @param userId 정리할 사용자 ID
     */
    fun clearUserTypingStatus(userId: UUID) {
        try {
            // 개별 사용자 타이핑 키 패턴으로 조회 및 삭제
            val pattern = "$USER_TYPING_KEY_PREFIX*:$userId"
            val keys = redisTemplate.keys(pattern)

            if (!keys.isNullOrEmpty()) {
                // 모든 관련 키 삭제
                redisTemplate.delete(keys)

                // 각 키에서 채널 ID 추출하여 Set에서도 제거
                keys.forEach { key ->
                    try {
                        // 키 형식: "typing:user:{channelId}:{userId}"
                        val parts = key.split(":")
                        if (parts.size >= 4) {
                            val channelId = UUID.fromString(parts[2])
                            val channelKey = "$TYPING_KEY_PREFIX$channelId"
                            redisTemplate.opsForSet().remove(channelKey, userId.toString())
                        }
                    } catch (e: Exception) {
                        logger.warn("타이핑 상태 정리 중 키 파싱 실패: key={}", key, e)
                    }
                }

                logger.info(
                    "사용자 모든 타이핑 상태 정리 완료: userId={}, clearedKeys={}",
                    userId, keys.size
                )
            } else {
                logger.debug("정리할 타이핑 상태 없음: userId={}", userId)
            }
        } catch (e: Exception) {
            logger.error("사용자 타이핑 상태 정리 실패: userId={}", userId, e)
        }
    }

    /**
     * 특정 채널의 모든 타이핑 상태 정리
     *
     * Use Cases:
     * - 채널 삭제 시
     * - 채널 초기화 시
     * - 관리자 작업
     *
     * @param channelId 정리할 채널 ID
     */
    fun clearChannelTypingStatus(channelId: UUID) {
        try {
            val channelKey = "$TYPING_KEY_PREFIX$channelId"

            // 타이핑 중인 사용자 목록 조회
            val typingUsers = getTypingUsers(channelId)

            // 채널별 Set 삭제
            redisTemplate.delete(channelKey)

            // 개별 사용자 타이핑 키도 정리
            typingUsers.forEach { userIdStr ->
                try {
                    val userKey = "$USER_TYPING_KEY_PREFIX$channelId:$userIdStr"
                    redisTemplate.delete(userKey)
                } catch (e: Exception) {
                    logger.warn("개별 타이핑 키 삭제 실패: userId={}", userIdStr, e)
                }
            }

            logger.info(
                "채널 타이핑 상태 정리 완료: channelId={}, clearedUsers={}",
                channelId, typingUsers.size
            )
        } catch (e: Exception) {
            logger.error("채널 타이핑 상태 정리 실패: channelId={}", channelId, e)
        }
    }

    /**
     * 타이핑 인디케이터 통계 조회
     *
     * Use Cases:
     * - 모니터링 및 디버깅
     * - 시스템 상태 확인
     * - 성능 분석
     *
     * @return 타이핑 인디케이터 통계
     */
    fun getTypingStatistics(): TypingStatistics {
        return try {
            val pattern = "$TYPING_KEY_PREFIX*"
            val keys = redisTemplate.keys(pattern)

            var totalTypingUsers = 0
            val activeChannels = mutableListOf<UUID>()

            keys?.forEach { key ->
                try {
                    // "typing:{channelId}" 형식에서 channelId 추출
                    if (!key.contains(":user:")) {  // 채널 키만 처리
                        val channelIdStr = key.removePrefix(TYPING_KEY_PREFIX)
                        val channelId = UUID.fromString(channelIdStr)

                        val userCount = redisTemplate.opsForSet().size(key) ?: 0L
                        if (userCount > 0) {
                            activeChannels.add(channelId)
                            totalTypingUsers += userCount.toInt()
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("통계 처리 중 키 파싱 실패: key={}", key, e)
                }
            }

            TypingStatistics(
                totalTypingUsers = totalTypingUsers,
                activeChannelsCount = activeChannels.size,
                activeChannelIds = activeChannels
            )
        } catch (e: Exception) {
            logger.error("타이핑 통계 조회 실패", e)
            TypingStatistics(0, 0, emptyList())
        }
    }

    /**
     * 타이핑 인디케이터 통계 데이터 클래스
     */
    data class TypingStatistics(
        val totalTypingUsers: Int,
        val activeChannelsCount: Int,
        val activeChannelIds: List<UUID>
    )
}
