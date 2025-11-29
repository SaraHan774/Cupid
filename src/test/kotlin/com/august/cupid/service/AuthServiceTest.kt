package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.User
import com.august.cupid.repository.UserRepository
import com.august.cupid.security.TokenBlacklistService
import com.august.cupid.util.JwtUtil
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.*
import io.mockk.junit5.MockKExtension

/**
 * AuthService 단위 테스트
 * 인증 관련 비즈니스 로직 테스트
 */
@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var jwtUtil: JwtUtil

    @MockK
    private lateinit var tokenBlacklistService: TokenBlacklistService

    private lateinit var authService: AuthService

    private val testUserId = UUID.randomUUID()
    private val testUsername = "testuser"
    private val testEmail = "test@example.com"
    private val testPassword = "password123"
    private val testPasswordHash = "encoded_password_hash"

    @BeforeEach
    fun setUp() {
        authService = AuthService(
            userRepository,
            passwordEncoder,
            jwtUtil,
            tokenBlacklistService
        )
    }

    @Test
    fun `{given} 유효한_사용자명과_비밀번호일때 {when} 로그인하면 {then} 액세스_토큰과_리프레시_토큰을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val loginRequest = LoginRequest(
            username = testUsername,
            password = testPassword
        )
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = testPasswordHash,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val accessToken = "access-token"
        val refreshToken = "refresh-token"
        val expiresIn = 86400000L

        every { userRepository.findByUsernameAndIsActiveTrue(testUsername) } returns user
        every { passwordEncoder.matches(testPassword, testPasswordHash) } returns true
        every { jwtUtil.generateAccessToken(testUserId, testUsername, testEmail) } returns accessToken
        every { jwtUtil.generateRefreshToken(testUserId) } returns refreshToken
        every { jwtUtil.getExpirationTime(accessToken) } returns expiresIn
        every { userRepository.save(any()) } returns user.copy(
            lastSeenAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // When: 테스트 대상 메서드 실행
        val result = authService.login(loginRequest)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data?.accessToken).isEqualTo(accessToken)
        assertThat(result.data?.refreshToken).isEqualTo(refreshToken)
        assertThat(result.data?.expiresIn).isEqualTo(expiresIn)
        assertThat(result.data?.user?.id).isEqualTo(testUserId)
        assertThat(result.data?.user?.username).isEqualTo(testUsername)
        verify(exactly = 1) { userRepository.findByUsernameAndIsActiveTrue(testUsername) }
        verify(exactly = 1) { passwordEncoder.matches(testPassword, testPasswordHash) }
        verify(exactly = 1) { jwtUtil.generateAccessToken(testUserId, testUsername, testEmail) }
        verify(exactly = 1) { jwtUtil.generateRefreshToken(testUserId) }
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `{given} 존재하지_않는_사용자명일때 {when} 로그인하면 {then} 실패_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val loginRequest = LoginRequest(
            username = "nonexistent",
            password = testPassword
        )

        every { userRepository.findByUsernameAndIsActiveTrue("nonexistent") } returns null

        // When: 테스트 대상 메서드 실행
        val result = authService.login(loginRequest)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("사용자명 또는 비밀번호가 올바르지 않습니다")
        verify(exactly = 1) { userRepository.findByUsernameAndIsActiveTrue("nonexistent") }
        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        verify(exactly = 0) { jwtUtil.generateAccessToken(any(), any(), any()) }
    }

    @Test
    fun `{given} 잘못된_비밀번호일때 {when} 로그인하면 {then} 실패_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val loginRequest = LoginRequest(
            username = testUsername,
            password = "wrong_password"
        )
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = testPasswordHash,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { userRepository.findByUsernameAndIsActiveTrue(testUsername) } returns user
        every { passwordEncoder.matches("wrong_password", testPasswordHash) } returns false

        // When: 테스트 대상 메서드 실행
        val result = authService.login(loginRequest)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("사용자명 또는 비밀번호가 올바르지 않습니다")
        verify(exactly = 1) { userRepository.findByUsernameAndIsActiveTrue(testUsername) }
        verify(exactly = 1) { passwordEncoder.matches("wrong_password", testPasswordHash) }
        verify(exactly = 0) { jwtUtil.generateAccessToken(any(), any(), any()) }
    }

    @Test
    fun `{given} 유효한_리프레시_토큰일때 {when} 토큰_갱신하면 {then} 새로운_액세스_토큰과_리프레시_토큰을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val refreshToken = "valid-refresh-token"
        val refreshTokenRequest = RefreshTokenRequest(refreshToken)
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = testPasswordHash,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val newAccessToken = "new-access-token"
        val newRefreshToken = "new-refresh-token"
        val expiresIn = 86400000L

        every { jwtUtil.validateRefreshToken(refreshToken) } returns true
        every { jwtUtil.getUserIdFromToken(refreshToken) } returns testUserId
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { jwtUtil.generateAccessToken(testUserId, testUsername, testEmail) } returns newAccessToken
        every { jwtUtil.generateRefreshToken(testUserId) } returns newRefreshToken
        every { jwtUtil.getExpirationTime(newAccessToken) } returns expiresIn

        // When: 테스트 대상 메서드 실행
        val result = authService.refreshToken(refreshTokenRequest)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data?.accessToken).isEqualTo(newAccessToken)
        assertThat(result.data?.refreshToken).isEqualTo(newRefreshToken)
        assertThat(result.data?.expiresIn).isEqualTo(expiresIn)
        verify(exactly = 1) { jwtUtil.validateRefreshToken(refreshToken) }
        verify(exactly = 1) { jwtUtil.getUserIdFromToken(refreshToken) }
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 1) { jwtUtil.generateAccessToken(testUserId, testUsername, testEmail) }
        verify(exactly = 1) { jwtUtil.generateRefreshToken(testUserId) }
    }

    @Test
    fun `{given} 만료된_리프레시_토큰일때 {when} 토큰_갱신하면 {then} 실패_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val expiredRefreshToken = "expired-refresh-token"
        val refreshTokenRequest = RefreshTokenRequest(expiredRefreshToken)

        every { jwtUtil.validateRefreshToken(expiredRefreshToken) } returns false

        // When: 테스트 대상 메서드 실행
        val result = authService.refreshToken(refreshTokenRequest)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("유효하지 않은 Refresh Token입니다")
        verify(exactly = 1) { jwtUtil.validateRefreshToken(expiredRefreshToken) }
        verify(exactly = 0) { jwtUtil.getUserIdFromToken(any()) }
        verify(exactly = 0) { userRepository.findById(any()) }
    }

    @Test
    fun `{given} 유효한_액세스_토큰일때 {when} 로그아웃하면 {then} 토큰이_블랙리스트에_추가되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val accessToken = "valid-access-token"
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = testPasswordHash,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { tokenBlacklistService.addTokenToBlacklist(accessToken) } just Runs

        // When: 테스트 대상 메서드 실행
        val result = authService.logout(testUserId, accessToken)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("로그아웃이 성공적으로 완료되었습니다")
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 1) { tokenBlacklistService.addTokenToBlacklist(accessToken) }
    }

    @Test
    fun `{given} 존재하지_않는_사용자일때 {when} 로그아웃하면 {then} 실패_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val accessToken = "valid-access-token"
        val nonExistentUserId = UUID.randomUUID()

        every { userRepository.findById(nonExistentUserId) } returns Optional.empty()

        // When: 테스트 대상 메서드 실행
        val result = authService.logout(nonExistentUserId, accessToken)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("사용자를 찾을 수 없습니다")
        verify(exactly = 1) { userRepository.findById(nonExistentUserId) }
        verify(exactly = 0) { tokenBlacklistService.addTokenToBlacklist(any()) }
    }

    @Test
    fun `{given} 유효한_액세스_토큰일때 {when} 토큰_검증하면 {then} 토큰_정보를_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val accessToken = "valid-access-token"
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = testPasswordHash,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val tokenInfo = TokenInfo(
            userId = testUserId,
            username = testUsername,
            email = testEmail,
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            tokenType = "ACCESS"
        )

        every { jwtUtil.validateAccessToken(accessToken) } returns true
        every { jwtUtil.getTokenInfo(accessToken) } returns tokenInfo
        every { userRepository.findById(testUserId) } returns Optional.of(user)

        // When: 테스트 대상 메서드 실행
        val result = authService.validateToken(accessToken)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data?.userId).isEqualTo(testUserId)
        assertThat(result.data?.username).isEqualTo(testUsername)
        verify(exactly = 1) { jwtUtil.validateAccessToken(accessToken) }
        verify(exactly = 1) { jwtUtil.getTokenInfo(accessToken) }
        verify(exactly = 1) { userRepository.findById(testUserId) }
    }

    @Test
    fun `{given} 만료된_액세스_토큰일때 {when} 토큰_검증하면 {then} 실패_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val expiredToken = "expired-access-token"

        every { jwtUtil.validateAccessToken(expiredToken) } returns false

        // When: 테스트 대상 메서드 실행
        val result = authService.validateToken(expiredToken)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("유효하지 않은 토큰입니다")
        verify(exactly = 1) { jwtUtil.validateAccessToken(expiredToken) }
        verify(exactly = 0) { jwtUtil.getTokenInfo(any()) }
        verify(exactly = 0) { userRepository.findById(any()) }
    }
}

