package com.august.cupid.util

import com.august.cupid.model.dto.TokenInfo
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.Key
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * JwtUtil 단위 테스트
 * JWT 토큰 생성, 검증, 파싱 테스트
 */
class JwtUtilTest {

    private val secret = "test-secret-key-for-jwt-token-generation-and-validation-testing-purposes-only"
    private val expiration = TimeUnit.HOURS.toMillis(1) // 1시간
    private val refreshExpiration = TimeUnit.DAYS.toMillis(7) // 7일
    
    private lateinit var jwtUtil: JwtUtil

    private val testUserId = UUID.randomUUID()
    private val testUsername = "testuser"
    private val testEmail = "test@example.com"

    @BeforeEach
    fun setUp() {
        jwtUtil = JwtUtil(secret, expiration, refreshExpiration)
    }

    @Test
    fun `{given} 사용자ID_사용자명_이메일일때 {when} 액세스_토큰_생성하면 {then} 유효한_JWT_토큰을_반환한다`() {
        // Given: 테스트 데이터 준비
        // When: 테스트 대상 메서드 실행
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)

        // Then: 결과 검증
        assertThat(token).isNotNull()
        assertThat(token).isNotEmpty()
        assertThat(token.split(".")).hasSize(3) // JWT는 header.payload.signature 형식
        
        // 토큰에서 정보 추출하여 검증
        val extractedUserId = jwtUtil.getUserIdFromToken(token)
        val extractedUsername = jwtUtil.getUsernameFromToken(token)
        val extractedEmail = jwtUtil.getEmailFromToken(token)
        
        assertThat(extractedUserId).isEqualTo(testUserId)
        assertThat(extractedUsername).isEqualTo(testUsername)
        assertThat(extractedEmail).isEqualTo(testEmail)
        
        // 토큰 타입 검증
        val tokenType = jwtUtil.getTokenTypeFromToken(token)
        assertThat(tokenType).isEqualTo("ACCESS")
        
        // 토큰 유효성 검증
        assertThat(jwtUtil.validateAccessToken(token)).isTrue()
    }

    @Test
    fun `{given} 사용자ID일때 {when} 리프레시_토큰_생성하면 {then} 유효한_리프레시_JWT_토큰을_반환한다`() {
        // Given: 테스트 데이터 준비
        // When: 테스트 대상 메서드 실행
        val token = jwtUtil.generateRefreshToken(testUserId)

        // Then: 결과 검증
        assertThat(token).isNotNull()
        assertThat(token).isNotEmpty()
        assertThat(token.split(".")).hasSize(3) // JWT는 header.payload.signature 형식
        
        // 토큰에서 사용자 ID 추출하여 검증
        val extractedUserId = jwtUtil.getUserIdFromToken(token)
        assertThat(extractedUserId).isEqualTo(testUserId)
        
        // 토큰 타입 검증
        val tokenType = jwtUtil.getTokenTypeFromToken(token)
        assertThat(tokenType).isEqualTo("REFRESH")
        
        // 토큰 유효성 검증
        assertThat(jwtUtil.validateRefreshToken(token)).isTrue()
    }

    @Test
    fun `{given} 유효한_액세스_토큰일때 {when} 토큰_검증하면 {then} true를_반환한다`() {
        // Given: 테스트 데이터 준비
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)

        // When: 테스트 대상 메서드 실행
        val isValid = jwtUtil.validateAccessToken(token)

        // Then: 결과 검증
        assertThat(isValid).isTrue()
    }

    @Test
    fun `{given} 만료된_토큰일때 {when} 토큰_검증하면 {then} false를_반환한다`() {
        // Given: 테스트 데이터 준비 - 만료된 토큰 생성
        val expiredJwtUtil = JwtUtil(secret, -1000, refreshExpiration) // 음수 만료 시간으로 만료된 토큰 생성
        val expiredToken = expiredJwtUtil.generateAccessToken(testUserId, testUsername, testEmail)
        
        // 만료 확인
        Thread.sleep(100) // 토큰이 확실히 만료되도록 대기

        // When: 테스트 대상 메서드 실행
        val isValid = jwtUtil.validateAccessToken(expiredToken)

        // Then: 결과 검증
        assertThat(isValid).isFalse()
    }

    @Test
    fun `{given} 유효한_토큰일때 {when} 토큰에서_사용자ID_추출하면 {then} 사용자ID를_반환한다`() {
        // Given: 테스트 데이터 준비
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)

        // When: 테스트 대상 메서드 실행
        val extractedUserId = jwtUtil.getUserIdFromToken(token)

        // Then: 결과 검증
        assertThat(extractedUserId).isNotNull()
        assertThat(extractedUserId).isEqualTo(testUserId)
    }

    @Test
    fun `{given} 유효한_토큰일때 {when} 토큰에서_사용자명_추출하면 {then} 사용자명을_반환한다`() {
        // Given: 테스트 데이터 준비
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)

        // When: 테스트 대상 메서드 실행
        val extractedUsername = jwtUtil.getUsernameFromToken(token)

        // Then: 결과 검증
        assertThat(extractedUsername).isNotNull()
        assertThat(extractedUsername).isEqualTo(testUsername)
    }

    @Test
    fun `{given} Bearer_접두사가_있는_토큰일때 {when} Bearer_접두사_제거하면 {then} 접두사가_제거된_토큰을_반환한다`() {
        // Given: 테스트 데이터 준비
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)
        val tokenWithBearer = "Bearer $token"

        // When: 테스트 대상 메서드 실행
        val tokenWithoutBearer = jwtUtil.removeBearerPrefix(tokenWithBearer)

        // Then: 결과 검증
        assertThat(tokenWithoutBearer).isEqualTo(token)
        assertThat(tokenWithoutBearer).doesNotStartWith("Bearer ")
    }

    @Test
    fun `{given} 접두사가_없는_토큰일때 {when} Bearer_접두사_추가하면 {then} Bearer_접두사가_추가된_토큰을_반환한다`() {
        // Given: 테스트 데이터 준비
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)

        // When: 테스트 대상 메서드 실행
        val tokenWithBearer = jwtUtil.addBearerPrefix(token)

        // Then: 결과 검증
        assertThat(tokenWithBearer).startsWith("Bearer ")
        assertThat(tokenWithBearer).isEqualTo("Bearer $token")
    }

    @Test
    fun `{given} 유효한_토큰일때 {when} 토큰_정보_추출하면 {then} TokenInfo를_반환한다`() {
        // Given: 테스트 데이터 준비
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)

        // When: 테스트 대상 메서드 실행
        val tokenInfo = jwtUtil.getTokenInfo(token)

        // Then: 결과 검증
        assertThat(tokenInfo).isNotNull()
        assertThat(tokenInfo?.userId).isEqualTo(testUserId)
        assertThat(tokenInfo?.username).isEqualTo(testUsername)
        assertThat(tokenInfo?.email).isEqualTo(testEmail)
        assertThat(tokenInfo?.tokenType).isEqualTo("ACCESS")
        assertThat(tokenInfo?.issuedAt).isNotNull()
        assertThat(tokenInfo?.expiresAt).isNotNull()
        assertThat(tokenInfo?.expiresAt).isGreaterThan(tokenInfo?.issuedAt ?: 0)
    }

    @Test
    fun `{given} 유효한_토큰일때 {when} 만료_시간_조회하면 {then} 남은_시간을_반환한다`() {
        // Given: 테스트 데이터 준비
        val token = jwtUtil.generateAccessToken(testUserId, testUsername, testEmail)

        // When: 테스트 대상 메서드 실행
        val expirationTime = jwtUtil.getExpirationTime(token)

        // Then: 결과 검증
        assertThat(expirationTime).isNotNull()
        assertThat(expirationTime).isGreaterThan(0)
        assertThat(expirationTime).isLessThanOrEqualTo(expiration)
    }
}

