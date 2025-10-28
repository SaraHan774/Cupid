package com.august.cupid.websocket

import com.august.cupid.security.TokenBlacklistService
import com.august.cupid.util.JwtUtil
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpHeaders
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import java.net.URI
import java.util.*

/**
 * ConnectionInterceptor 단위 테스트
 * JWT 검증 로직 및 WebSocket 연결 처리 테스트
 */
class ConnectionInterceptorTest {

    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var jwtUtil: JwtUtil
    private lateinit var tokenBlacklistService: TokenBlacklistService
    private lateinit var connectionInterceptor: ConnectionInterceptor

    private val testUserId = UUID.randomUUID()
    private val testToken = "valid-token"
    private val testUsername = "testuser"

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk<RedisTemplate<String, String>>()
        jwtUtil = mockk<JwtUtil>()
        tokenBlacklistService = mockk<TokenBlacklistService>()

        // Redis operations mock
        val valueOps = mockk<ValueOperations<String, String>>()
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.set(any(), any()) } returns mockk()
        every { valueOps.get(any()) } returns null

        connectionInterceptor = ConnectionInterceptor(redisTemplate, jwtUtil, tokenBlacklistService)
    }

    @Test
    fun `beforeHandshake should succeed with valid JWT token`() {
        // Given
        val request = mockRequestWithToken(testToken)
        val response = mockk<ServerHttpResponse>()
        val wsHandler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { jwtUtil.validateAccessToken(testToken) } returns true
        every { jwtUtil.getUserIdFromToken(testToken) } returns testUserId
        every { tokenBlacklistService.isTokenBlacklisted(testToken) } returns false

        // When
        val result = connectionInterceptor.beforeHandshake(request, response, wsHandler, attributes)

        // Then
        kotlin.test.assertTrue(result, "연결이 성공해야 합니다")
        kotlin.test.assertEquals(testUserId.toString(), attributes["userId"], "userId가 attributes에 설정되어야 합니다")
        kotlin.test.assertTrue(attributes.containsKey("sessionId"), "sessionId가 attributes에 설정되어야 합니다")
        
        verify(exactly = 1) { jwtUtil.validateAccessToken(testToken) }
        verify(exactly = 1) { jwtUtil.getUserIdFromToken(testToken) }
        verify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(testToken) }
    }

    @Test
    fun `beforeHandshake should fail when token is missing`() {
        // Given
        val request = mockRequestWithoutToken()
        val response = mockk<ServerHttpResponse>()
        val wsHandler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        // When
        val result = connectionInterceptor.beforeHandshake(request, response, wsHandler, attributes)

        // Then
        kotlin.test.assertFalse(result, "토큰이 없으면 연결이 실패해야 합니다")
        verify(exactly = 0) { jwtUtil.validateAccessToken(any()) }
        verify(exactly = 0) { jwtUtil.getUserIdFromToken(any()) }
    }

    @Test
    fun `beforeHandshake should fail when token is blacklisted`() {
        // Given
        val request = mockRequestWithToken(testToken)
        val response = mockk<ServerHttpResponse>()
        val wsHandler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { tokenBlacklistService.isTokenBlacklisted(testToken) } returns true

        // When
        val result = connectionInterceptor.beforeHandshake(request, response, wsHandler, attributes)

        // Then
        kotlin.test.assertFalse(result, "블랙리스트된 토큰이면 연결이 실패해야 합니다")
        verify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(testToken) }
        verify(exactly = 0) { jwtUtil.validateAccessToken(any()) }
    }

    @Test
    fun `beforeHandshake should fail when token is invalid`() {
        // Given
        val request = mockRequestWithToken(testToken)
        val response = mockk<ServerHttpResponse>()
        val wsHandler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { tokenBlacklistService.isTokenBlacklisted(testToken) } returns false
        every { jwtUtil.validateAccessToken(testToken) } returns false

        // When
        val result = connectionInterceptor.beforeHandshake(request, response, wsHandler, attributes)

        // Then
        kotlin.test.assertFalse(result, "유효하지 않은 토큰이면 연결이 실패해야 합니다")
        verify(exactly = 1) { jwtUtil.validateAccessToken(testToken) }
        verify(exactly = 0) { jwtUtil.getUserIdFromToken(any()) }
    }

    @Test
    fun `beforeHandshake should fail when userId cannot be extracted`() {
        // Given
        val request = mockRequestWithToken(testToken)
        val response = mockk<ServerHttpResponse>()
        val wsHandler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { tokenBlacklistService.isTokenBlacklisted(testToken) } returns false
        every { jwtUtil.validateAccessToken(testToken) } returns true
        every { jwtUtil.getUserIdFromToken(testToken) } returns null

        // When
        val result = connectionInterceptor.beforeHandshake(request, response, wsHandler, attributes)

        // Then
        kotlin.test.assertFalse(result, "userId를 추출할 수 없으면 연결이 실패해야 합니다")
    }

    @Test
    fun `beforeHandshake should extract token from query parameter`() {
        // Given
        val request = mockRequestWithQueryToken(testToken)
        val response = mockk<ServerHttpResponse>()
        val wsHandler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { jwtUtil.validateAccessToken(testToken) } returns true
        every { jwtUtil.getUserIdFromToken(testToken) } returns testUserId
        every { tokenBlacklistService.isTokenBlacklisted(testToken) } returns false

        // When
        val result = connectionInterceptor.beforeHandshake(request, response, wsHandler, attributes)

        // Then
        kotlin.test.assertTrue(result, "쿼리 파라미터에서 토큰을 추출할 수 있어야 합니다")
    }

    // Helper methods
    private fun mockRequestWithToken(token: String): ServerHttpRequest {
        val request = mockk<ServerHttpRequest>()
        val headers = HttpHeaders()
        headers.add("Authorization", "Bearer $token")
        val uri = URI.create("ws://localhost:8080/ws")
        
        every { request.uri } returns uri
        every { request.headers } returns headers
        every { request.uri.query } returns null
        
        return request
    }

    private fun mockRequestWithQueryToken(token: String): ServerHttpRequest {
        val request = mockk<ServerHttpRequest>()
        val headers = HttpHeaders()
        val uri = URI.create("ws://localhost:8080/ws?token=$token")
        
        every { request.uri } returns uri
        every { request.headers } returns headers
        every { request.uri.query } returns "token=$token"
        
        return request
    }

    private fun mockRequestWithoutToken(): ServerHttpRequest {
        val request = mockk<ServerHttpRequest>()
        val headers = HttpHeaders()
        val uri = URI.create("ws://localhost:8080/ws")
        
        every { request.uri } returns uri
        every { request.headers } returns headers
        every { request.uri.query } returns null
        
        return request
    }
}

