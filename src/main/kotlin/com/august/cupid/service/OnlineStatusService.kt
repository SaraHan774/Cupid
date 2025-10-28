package com.august.cupid.service

import com.august.cupid.websocket.ConnectionInterceptor
import org.slf4j.LoggerFactory
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
    private val connectionInterceptor: ConnectionInterceptor
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자 온라인 상태 확인
     * 
     * @param userId 확인할 사용자 ID
     * @return 온라인 여부
     */
    fun isUserOnline(userId: String): Boolean {
        return try {
            val isOnline = connectionInterceptor.isUserOnline(userId)
            logger.debug("사용자 온라인 상태 확인: userId={}, isOnline={}", userId, isOnline)
            isOnline
        } catch (e: Exception) {
            logger.error("사용자 온라인 상태 확인 실패: userId={}", userId, e)
            false
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
            val statusMap = connectionInterceptor.getUsersOnlineStatus(userIds)
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
            val onlineUsers = connectionInterceptor.getOnlineUsers()
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
            connectionInterceptor.refreshUserOnlineStatus(userId)
            logger.debug("사용자 하트비트 처리 완료: userId={}", userId)
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
            connectionInterceptor.removeUserOnlineStatus(userId)
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
