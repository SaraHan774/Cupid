package com.august.cupid.websocket

import com.august.cupid.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.concurrent.TimeUnit

/**
 * WebSocket 연결 상태 추적 인터셉터
 * 
 * 기능:
 * 1. WebSocket 연결 시 Redis에 온라인 상태 저장 (5분 TTL)
 * 2. 연결 해제 시 Redis에서 온라인 상태 제거
 * 3. JWT 토큰에서 사용자 ID 추출
 * 4. 하트비트로 연결 상태 갱신
 */
@Component
class ConnectionInterceptor(
    private val redisTemplate: RedisTemplate<String, String>,
    private val jwtUtil: JwtUtil
) : HandshakeInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Redis 키 패턴
        private const val USER_ONLINE_KEY_PREFIX = "user:online:"
        private const val USER_SESSION_KEY_PREFIX = "session:"
        
        // TTL 설정 (초)
        private const val ONLINE_TTL_SECONDS = 300L  // 5분
        private const val SESSION_TTL_SECONDS = 3600L  // 1시간
    }

    /**
     * WebSocket 연결 전 처리
     * JWT 토큰 검증 및 사용자 ID 추출 후 Redis에 온라인 상태 저장
     */
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        try {
            // 1. JWT 토큰 추출
            val token = extractTokenFromRequest(request)
            if (token == null) {
                logger.warn("WebSocket 연결 실패: JWT 토큰이 없습니다")
                return false
            }

            // 2. JWT 토큰 검증 및 사용자 ID 추출
            val userId = jwtUtil.getUserIdFromToken(token)?.toString()
            if (userId == null) {
                logger.warn("WebSocket 연결 실패: 유효하지 않은 JWT 토큰")
                return false
            }

            // 3. 세션 ID 생성 (WebSocket 세션 추적용)
            val sessionId = generateSessionId(userId)

            // 4. Redis에 온라인 상태 저장
            setUserOnlineStatus(userId, sessionId)

            // 5. WebSocket 세션에 사용자 정보 저장
            attributes["userId"] = userId
            attributes["sessionId"] = sessionId

            logger.info("WebSocket 연결 성공: userId={}, sessionId={}", userId, sessionId)
            return true

        } catch (e: Exception) {
            logger.error("WebSocket 연결 처리 중 오류 발생", e)
            return false
        }
    }

    /**
     * WebSocket 연결 후 처리
     * 현재는 추가 작업 없음
     */
    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        if (exception != null) {
            logger.error("WebSocket 연결 후 처리 중 오류 발생", exception)
        }
    }

    /**
     * HTTP 요청에서 JWT 토큰 추출
     * Authorization 헤더 또는 쿼리 파라미터에서 토큰 추출
     */
    private fun extractTokenFromRequest(request: ServerHttpRequest): String? {
        // 1. Authorization 헤더에서 추출
        val authHeader = request.headers.getFirst("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        // 2. 쿼리 파라미터에서 추출 (SockJS fallback용)
        val queryParams = request.uri.query
        if (queryParams != null) {
            val tokenParam = queryParams.split("&")
                .find { it.startsWith("token=") }
                ?.substring(6)
            if (tokenParam != null) {
                return tokenParam
            }
        }

        return null
    }

    /**
     * 사용자 온라인 상태를 Redis에 저장
     * 
     * @param userId 사용자 ID
     * @param sessionId WebSocket 세션 ID
     */
    private fun setUserOnlineStatus(userId: String, sessionId: String) {
        try {
            // 온라인 상태 저장 (5분 TTL)
            redisTemplate.opsForValue().set(
                "$USER_ONLINE_KEY_PREFIX$userId",
                "true",
                ONLINE_TTL_SECONDS,
                TimeUnit.SECONDS
            )

            // 세션 정보 저장 (1시간 TTL)
            redisTemplate.opsForValue().set(
                "$USER_SESSION_KEY_PREFIX$userId",
                sessionId,
                SESSION_TTL_SECONDS,
                TimeUnit.SECONDS
            )

            logger.debug("사용자 온라인 상태 저장 완료: userId={}, sessionId={}", userId, sessionId)

        } catch (e: Exception) {
            logger.error("Redis에 온라인 상태 저장 실패: userId={}", userId, e)
        }
    }

    /**
     * 사용자 온라인 상태를 Redis에서 제거
     * 
     * @param userId 사용자 ID
     */
    fun removeUserOnlineStatus(userId: String) {
        try {
            redisTemplate.delete("$USER_ONLINE_KEY_PREFIX$userId")
            redisTemplate.delete("$USER_SESSION_KEY_PREFIX$userId")

            logger.info("사용자 온라인 상태 제거 완료: userId={}", userId)

        } catch (e: Exception) {
            logger.error("Redis에서 온라인 상태 제거 실패: userId={}", userId, e)
        }
    }

    /**
     * 사용자 온라인 상태 확인
     * 
     * @param userId 사용자 ID
     * @return 온라인 여부
     */
    fun isUserOnline(userId: String): Boolean {
        return try {
            val onlineStatus = redisTemplate.opsForValue().get("$USER_ONLINE_KEY_PREFIX$userId")
            onlineStatus == "true"
        } catch (e: Exception) {
            logger.error("Redis에서 온라인 상태 확인 실패: userId={}", userId, e)
            false
        }
    }

    /**
     * 사용자 온라인 상태 갱신 (하트비트)
     * TTL을 다시 설정하여 연결 상태 유지
     * 
     * @param userId 사용자 ID
     */
    fun refreshUserOnlineStatus(userId: String) {
        try {
            // TTL 갱신
            redisTemplate.expire("$USER_ONLINE_KEY_PREFIX$userId", ONLINE_TTL_SECONDS, TimeUnit.SECONDS)
            redisTemplate.expire("$USER_SESSION_KEY_PREFIX$userId", SESSION_TTL_SECONDS, TimeUnit.SECONDS)

            logger.debug("사용자 온라인 상태 갱신 완료: userId={}", userId)

        } catch (e: Exception) {
            logger.error("Redis에서 온라인 상태 갱신 실패: userId={}", userId, e)
        }
    }

    /**
     * WebSocket 세션 ID 생성
     * 
     * @param userId 사용자 ID
     * @return 생성된 세션 ID
     */
    private fun generateSessionId(userId: String): String {
        val timestamp = System.currentTimeMillis()
        val random = (Math.random() * 1000).toInt()
        return "ws-session-$userId-$timestamp-$random"
    }

    /**
     * 모든 온라인 사용자 목록 조회
     * 
     * @return 온라인 사용자 ID 목록
     */
    fun getOnlineUsers(): List<String> {
        return try {
            val keys = redisTemplate.keys("$USER_ONLINE_KEY_PREFIX*")
            keys?.map { it.substring(USER_ONLINE_KEY_PREFIX.length) } ?: emptyList()
        } catch (e: Exception) {
            logger.error("온라인 사용자 목록 조회 실패", e)
            emptyList()
        }
    }

    /**
     * 특정 사용자들의 온라인 상태 일괄 확인
     * 
     * @param userIds 확인할 사용자 ID 목록
     * @return 사용자 ID와 온라인 상태의 매핑
     */
    fun getUsersOnlineStatus(userIds: List<String>): Map<String, Boolean> {
        return try {
            val result = mutableMapOf<String, Boolean>()
            
            userIds.forEach { userId ->
                result[userId] = isUserOnline(userId)
            }
            
            result
        } catch (e: Exception) {
            logger.error("사용자 온라인 상태 일괄 확인 실패", e)
            emptyMap()
        }
    }
}
