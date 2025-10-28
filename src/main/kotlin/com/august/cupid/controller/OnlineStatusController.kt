package com.august.cupid.controller

import com.august.cupid.service.OnlineStatusService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 온라인 상태 확인 API 컨트롤러
 * 
 * 기능:
 * 1. 사용자 온라인 상태 확인
 * 2. 온라인 사용자 목록 조회
 * 3. 온라인 상태 통계 조회
 */
@RestController
@RequestMapping("/api/v1/online-status")
class OnlineStatusController(
    private val onlineStatusService: OnlineStatusService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 특정 사용자의 온라인 상태 확인
     * 
     * @param userId 확인할 사용자 ID
     * @return 온라인 여부
     */
    @GetMapping("/users/{userId}")
    fun checkUserOnlineStatus(@PathVariable userId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val isOnline = onlineStatusService.isUserOnline(userId)
            
            val response = mapOf(
                "userId" to userId,
                "isOnline" to isOnline,
                "timestamp" to System.currentTimeMillis()
            )
            
            logger.debug("사용자 온라인 상태 확인: userId={}, isOnline={}", userId, isOnline)
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("사용자 온라인 상태 확인 실패: userId={}", userId, e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "온라인 상태 확인 실패",
                "message" to e.message
            ))
        }
    }

    /**
     * 여러 사용자의 온라인 상태 일괄 확인
     * 
     * @param request 사용자 ID 목록이 포함된 요청
     * @return 사용자별 온라인 상태 매핑
     */
    @PostMapping("/users/batch")
    fun checkUsersOnlineStatus(@RequestBody request: BatchOnlineStatusRequest): ResponseEntity<Map<String, Any>> {
        return try {
            val statusMap = onlineStatusService.getUsersOnlineStatus(request.userIds)
            
            val response = mapOf(
                "statusMap" to statusMap,
                "totalUsers" to request.userIds.size,
                "onlineUsers" to statusMap.count { it.value },
                "timestamp" to System.currentTimeMillis()
            )
            
            logger.debug("사용자 온라인 상태 일괄 확인 완료: {}명", request.userIds.size)
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("사용자 온라인 상태 일괄 확인 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "온라인 상태 일괄 확인 실패",
                "message" to e.message
            ))
        }
    }

    /**
     * 모든 온라인 사용자 목록 조회
     * 
     * @return 온라인 사용자 목록
     */
    @GetMapping("/users")
    fun getOnlineUsers(): ResponseEntity<Map<String, Any>> {
        return try {
            val onlineUsers = onlineStatusService.getOnlineUsers()
            
            val response = mapOf(
                "onlineUsers" to onlineUsers,
                "totalOnlineUsers" to onlineUsers.size,
                "timestamp" to System.currentTimeMillis()
            )
            
            logger.debug("온라인 사용자 목록 조회 완료: {}명", onlineUsers.size)
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("온라인 사용자 목록 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "온라인 사용자 목록 조회 실패",
                "message" to e.message
            ))
        }
    }

    /**
     * 온라인 상태 통계 조회
     * 
     * @return 온라인 상태 통계 정보
     */
    @GetMapping("/stats")
    fun getOnlineStatusStats(): ResponseEntity<Map<String, Any>> {
        return try {
            val stats = onlineStatusService.getOnlineStatusStats()
            
            val response = mapOf(
                "totalOnlineUsers" to stats.totalOnlineUsers,
                "onlineUserIds" to stats.onlineUserIds,
                "timestamp" to System.currentTimeMillis()
            )
            
            logger.debug("온라인 상태 통계 조회 완료: {}명 온라인", stats.totalOnlineUsers)
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("온라인 상태 통계 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "온라인 상태 통계 조회 실패",
                "message" to e.message
            ))
        }
    }

    /**
     * 채널 참여자들의 온라인 상태 확인
     * 
     * @param channelId 채널 ID
     * @param request 채널 참여자 ID 목록이 포함된 요청
     * @return 온라인인 참여자 목록
     */
    @PostMapping("/channels/{channelId}/members")
    fun getOnlineChannelMembers(
        @PathVariable channelId: String,
        @RequestBody request: ChannelMembersRequest
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val onlineMembers = onlineStatusService.getOnlineChannelMembers(request.memberIds)
            
            val response = mapOf(
                "channelId" to channelId,
                "totalMembers" to request.memberIds.size,
                "onlineMembers" to onlineMembers,
                "onlineMemberCount" to onlineMembers.size,
                "timestamp" to System.currentTimeMillis()
            )
            
            logger.debug("채널 온라인 멤버 조회 완료: channelId={}, {}명 중 {}명 온라인", 
                channelId, request.memberIds.size, onlineMembers.size)
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("채널 온라인 멤버 조회 실패: channelId={}", channelId, e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "채널 온라인 멤버 조회 실패",
                "message" to e.message
            ))
        }
    }

    /**
     * 일괄 온라인 상태 확인 요청 데이터 클래스
     */
    data class BatchOnlineStatusRequest(
        val userIds: List<String>
    )

    /**
     * 채널 멤버 요청 데이터 클래스
     */
    data class ChannelMembersRequest(
        val memberIds: List<String>
    )
}
