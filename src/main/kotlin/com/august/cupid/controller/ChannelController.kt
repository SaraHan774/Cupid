package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.ChannelService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 채널 관리 컨트롤러
 * 채팅방 관리 API
 */
@Tag(name = "Channel", description = "채널 관리 API - 채널 조회/생성/나가기 및 멤버 관리")
@RestController
@RequestMapping("/api/v1/channels")
class ChannelController(
    private val channelService: ChannelService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * JWT에서 사용자 ID 추출
     */
    private fun getUserIdFromAuthentication(authentication: Authentication): UUID? {
        return try {
            val userIdString = authentication.name
            UUID.fromString(userIdString)
        } catch (e: Exception) {
            logger.error("사용자 ID 추출 실패", e)
            null
        }
    }

    /**
     * 채널 목록 조회
     * GET /api/v1/channels
     */
    @Operation(summary = "채널 목록 조회", description = "사용자가 참여 중인 채널 목록을 조회합니다")
    @GetMapping
    fun getChannels(
        authentication: Authentication,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("채널 목록 조회 요청: userId={}, page={}, size={}", userId, page, size)
        
        return try {
            val result = channelService.getUserChannels(userId, page, size)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "channels" to (result.data ?: emptyList<ChannelResponse>()),
                    "total" to (result.data?.size ?: 0)
                ))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "채널 목록 조회 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("채널 목록 조회 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 채널 상세 정보 조회
     * GET /api/v1/channels/{channelId}
     */
    @Operation(summary = "채널 상세 조회", description = "특정 채널의 상세 정보를 조회합니다")
    @GetMapping("/{channelId}")
    fun getChannel(
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("채널 상세 조회: channelId={}", channelId)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)
            val result = channelService.getChannelById(channelIdUuid)
            
            if (result.success && result.data != null) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "channel" to result.data
                ))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "채널을 찾을 수 없습니다")
                ))
            }
        } catch (e: Exception) {
            logger.error("채널 상세 조회 중 오류 발생: channelId={}", channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 채널 생성
     * POST /api/v1/channels
     */
    @Operation(summary = "채널 생성", description = "새로운 채널을 생성합니다 (1:1 또는 그룹)")
    @PostMapping
    fun createChannel(
        authentication: Authentication,
        @RequestBody request: CreateChannelRequest
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("채널 생성 요청: userId={}, type={}", userId, request.type)
        
        return try {
            val result = channelService.createChannel(request, userId)
            
            if (result.success && result.data != null) {
                ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "채널이 생성되었습니다"),
                    "channel" to result.data
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "채널 생성 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("채널 생성 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 채널 나가기
     * DELETE /api/v1/channels/{channelId}/leave
     */
    @Operation(summary = "채널 나가기", description = "참여 중인 채널에서 나갑니다")
    @DeleteMapping("/{channelId}/leave")
    fun leaveChannel(
        authentication: Authentication,
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "error" to "인증 정보를 찾을 수 없습니다"
            ))
        }
        logger.info("채널 나가기 요청: userId={}, channelId={}", userId, channelId)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)
            
            // removeUserFromChannel의 removerId는 사용자 본인
            val result = channelService.removeUserFromChannel(channelIdUuid, userId, userId)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to (result.message ?: "채널에서 나갔습니다")
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.message ?: "채널 나가기 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("채널 나가기 중 오류 발생: userId={}, channelId={}", userId, channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }

    /**
     * 채널 멤버 목록 조회
     * GET /api/v1/channels/{channelId}/members
     */
    @Operation(summary = "채널 멤버 조회", description = "채널의 멤버 목록을 조회합니다")
    @GetMapping("/{channelId}/members")
    fun getChannelMembers(
        @PathVariable channelId: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("채널 멤버 목록 조회: channelId={}", channelId)
        
        return try {
            val channelIdUuid = UUID.fromString(channelId)
            val result = channelService.getChannelMembers(channelIdUuid)
            
            if (result.success) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "members" to (result.data ?: emptyList<UserResponse>())
                ))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "success" to false,
                    "error" to (result.error ?: "멤버 목록 조회 실패")
                ))
            }
        } catch (e: Exception) {
            logger.error("채널 멤버 목록 조회 중 오류 발생: channelId={}", channelId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "error" to "서버 오류가 발생했습니다"
            ))
        }
    }
}

