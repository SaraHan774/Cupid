package com.august.cupid.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * 사용자 온라인 상태 관리 서비스
 * 
 * 기능:
 * 1. 사용자 온라인 상태 확인
 * 2. 온라인 사용자 목록 조회
 * 3. 하트비트 처리
 * 4. 연결 해제 처리
 */
@Service
class OnlineStatusService(
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    
    companion object {
        private const val ONLINE_USER_KEY_PREFIX = "user:online:"
        private const val ONLINE_USER_TTL_MINUTES = 5L
    }

    /**
     * 사용자 온라인 상태 확인
     * 
     * @param userId 확인할 사용자 ID
     * @return 온라인 여부
     */
    fun isUserOnline(userId: String): Boolean {
        return try {
            val key = "$ONLINE_USER_KEY_PREFIX$userId"
            val isOnline = redisTemplate.hasKey(key)
            logger.debug("사용자 온라인 상태 확인: userId={}, isOnline={}", userId, isOnline)
            isOnline
        } catch (e: Exception) {
            logger.error("사용자 온라인 상태 확인 실패: userId={}", userId, e)
            false
        }
    }

    /**
     * 사용자 온라인 상태 설정
     * 
     * @param userId 사용자 ID
     * @param sessionId 세션 ID
     */
    fun setUserOnline(userId: String, sessionId: String) {
        try {
            val key = "$ONLINE_USER_KEY_PREFIX$userId"
            redisTemplate.opsForValue().set(key, sessionId, ONLINE_USER_TTL_MINUTES, TimeUnit.MINUTES)
            logger.debug("사용자 온라인 상태 설정: userId={}, sessionId={}", userId, sessionId)
        } catch (e: Exception) {
            logger.error("사용자 온라인 상태 설정 실패: userId={}", userId, e)
        }
    }

    /**
     * 사용자 오프라인 상태 설정
     * 
     * @param userId 사용자 ID
     */
    fun setUserOffline(userId: String) {
        try {
            val key = "$ONLINE_USER_KEY_PREFIX$userId"
            redisTemplate.delete(key)
            logger.debug("사용자 오프라인 상태 설정: userId={}", userId)
        } catch (e: Exception) {
            logger.error("사용자 오프라인 상태 설정 실패: userId={}", userId, e)
        }
    }

    /**
     * 여러 사용자의 온라인 상태 일괄 확인
     * 
     * @param userIds 확인할 사용자 ID 목록
     * @return 사용자 ID와 온라인 상태의 매핑
     */
    fun getUsersOnlineStatus(userIds: List<String>): Map<String, Boolean> {
        return try {
            val statusMap = userIds.associateWith { userId ->
                isUserOnline(userId)
            }
            logger.debug("사용자 온라인 상태 일괄 확인 완료: {}명", userIds.size)
            statusMap
        } catch (e: Exception) {
            logger.error("사용자 온라인 상태 일괄 확인 실패", e)
            emptyMap()
        }
    }

    /**
     * 모든 온라인 사용자 목록 조회
     * 
     * @return 온라인 사용자 ID 목록
     */
    fun getOnlineUsers(): List<String> {
        return try {
            val pattern = "$ONLINE_USER_KEY_PREFIX*"
            val keys = redisTemplate.keys(pattern)
            val onlineUsers = keys?.map { key ->
                key.removePrefix(ONLINE_USER_KEY_PREFIX)
            } ?: emptyList()
            logger.debug("온라인 사용자 목록 조회 완료: {}명", onlineUsers.size)
            onlineUsers
        } catch (e: Exception) {
            logger.error("온라인 사용자 목록 조회 실패", e)
            emptyList()
        }
    }

    /**
     * 사용자 하트비트 처리
     * 연결 상태를 갱신하여 온라인 상태 유지
     * 
     * @param userId 하트비트를 보낸 사용자 ID
     */
    fun processHeartbeat(userId: String) {
        try {
            val key = "$ONLINE_USER_KEY_PREFIX$userId"
            if (redisTemplate.hasKey(key)) {
                redisTemplate.expire(key, ONLINE_USER_TTL_MINUTES, TimeUnit.MINUTES)
                logger.debug("사용자 하트비트 처리 완료: userId={}", userId)
            }
        } catch (e: Exception) {
            logger.error("사용자 하트비트 처리 실패: userId={}", userId, e)
        }
    }

    /**
     * 사용자 연결 해제 처리
     * Redis에서 온라인 상태 제거
     * 
     * @param userId 연결 해제한 사용자 ID
     */
    fun processDisconnection(userId: String) {
        try {
            setUserOffline(userId)
            logger.info("사용자 연결 해제 처리 완료: userId={}", userId)
        } catch (e: Exception) {
            logger.error("사용자 연결 해제 처리 실패: userId={}", userId, e)
        }
    }

    /**
     * 특정 채널의 참여자들 중 온라인인 사용자 필터링
     * 
     * @param channelMembers 채널 참여자 ID 목록
     * @return 온라인인 참여자 ID 목록
     */
    fun getOnlineChannelMembers(channelMembers: List<String>): List<String> {
        return try {
            val onlineStatusMap = getUsersOnlineStatus(channelMembers)
            val onlineMembers = onlineStatusMap.filter { it.value }.keys.toList()
            
            logger.debug("채널 온라인 멤버 필터링 완료: 전체 {}명 중 {}명 온라인", 
                channelMembers.size, onlineMembers.size)
            
            onlineMembers
        } catch (e: Exception) {
            logger.error("채널 온라인 멤버 필터링 실패", e)
            emptyList()
        }
    }

    /**
     * 사용자 온라인 상태 통계 조회
     * 
     * @return 온라인 상태 통계 정보
     */
    fun getOnlineStatusStats(): OnlineStatusStats {
        return try {
            val onlineUsers = getOnlineUsers()
            OnlineStatusStats(
                totalOnlineUsers = onlineUsers.size,
                onlineUserIds = onlineUsers
            )
        } catch (e: Exception) {
            logger.error("온라인 상태 통계 조회 실패", e)
            OnlineStatusStats(0, emptyList())
        }
    }

    /**
     * 온라인 상태 통계 데이터 클래스
     */
    data class OnlineStatusStats(
        val totalOnlineUsers: Int,
        val onlineUserIds: List<String>
    )
}
