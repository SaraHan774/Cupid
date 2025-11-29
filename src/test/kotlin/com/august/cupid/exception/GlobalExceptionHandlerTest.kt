package com.august.cupid.exception

import com.august.cupid.service.EncryptionMetricsService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.*

/**
 * GlobalExceptionHandler 단위 테스트
 *
 * 검증 항목:
 * - 비즈니스 예외 처리
 * - 검증 예외 처리
 * - HTTP 상태 코드 매핑
 * - 응답 구조 일관성
 */
class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler
    private lateinit var metricsService: EncryptionMetricsService

    @BeforeEach
    fun setUp() {
        metricsService = mock(EncryptionMetricsService::class.java)
        handler = GlobalExceptionHandler(metricsService)
    }

    // ============================================
    // 비즈니스 예외 테스트
    // ============================================

    @Test
    fun `handleUnauthorizedException should return 401 with error details`() {
        // Given
        val exception = UnauthorizedException("인증이 필요합니다")

        // When
        val response = handler.handleUnauthorizedException(exception)

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("인증이 필요합니다", response.body!!.error)
        assertEquals("UNAUTHORIZED", response.body!!.errorCode)
    }

    @Test
    fun `handleAccessDeniedException should return 403 with error details`() {
        // Given
        val resourceId = UUID.randomUUID()
        val exception = AccessDeniedException(
            message = "접근 권한이 없습니다",
            resourceType = "channel",
            resourceId = resourceId
        )

        // When
        val response = handler.handleAccessDeniedException(exception)

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("접근 권한이 없습니다", response.body!!.error)
        assertEquals("ACCESS_DENIED", response.body!!.errorCode)
    }

    @Test
    fun `handleUserNotFoundException should return 404 with error details`() {
        // Given
        val userId = UUID.randomUUID()
        val exception = UserNotFoundException(userId = userId)

        // When
        val response = handler.handleUserNotFoundException(exception)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("USER_NOT_FOUND", response.body!!.errorCode)
        assertTrue(response.body!!.error!!.contains(userId.toString()))
    }

    @Test
    fun `handleChannelNotFoundException should return 404 with error details`() {
        // Given
        val channelId = UUID.randomUUID()
        val exception = ChannelNotFoundException(channelId)

        // When
        val response = handler.handleChannelNotFoundException(exception)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("CHANNEL_NOT_FOUND", response.body!!.errorCode)
        assertTrue(response.body!!.error!!.contains(channelId.toString()))
    }

    @Test
    fun `handleChannelAccessDeniedException should return 403`() {
        // Given
        val channelId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val exception = ChannelAccessDeniedException(channelId, userId)

        // When
        val response = handler.handleChannelAccessDeniedException(exception)

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("CHANNEL_ACCESS_DENIED", response.body!!.errorCode)
    }

    @Test
    fun `handleMessageNotFoundException should return 404`() {
        // Given
        val messageId = UUID.randomUUID()
        val exception = MessageNotFoundException(messageId)

        // When
        val response = handler.handleMessageNotFoundException(exception)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("MESSAGE_NOT_FOUND", response.body!!.errorCode)
    }

    @Test
    fun `handleMessageAccessDeniedException should return 403`() {
        // Given
        val exception = MessageAccessDeniedException("메시지를 수정할 권한이 없습니다")

        // When
        val response = handler.handleMessageAccessDeniedException(exception)

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("MESSAGE_ACCESS_DENIED", response.body!!.errorCode)
    }

    @Test
    fun `handleBadRequestException should return 400`() {
        // Given
        val exception = BadRequestException("잘못된 요청입니다", field = "channelId")

        // When
        val response = handler.handleBadRequestException(exception)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("BAD_REQUEST", response.body!!.errorCode)
    }

    @Test
    fun `handleDuplicateResourceException should return 409 CONFLICT`() {
        // Given
        val exception = DuplicateResourceException(
            message = "이미 존재하는 사용자입니다",
            resourceType = "user",
            field = "username"
        )

        // When
        val response = handler.handleDuplicateResourceException(exception)

        // Then
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("USER_DUPLICATE", response.body!!.errorCode)
    }

    @Test
    fun `handleAuthenticationFailedException should return 401`() {
        // Given
        val exception = AuthenticationFailedException("잘못된 비밀번호입니다")

        // When
        val response = handler.handleAuthenticationFailedException(exception)

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("AUTHENTICATION_FAILED", response.body!!.errorCode)
    }

    @Test
    fun `handleInvalidTokenException should return 401`() {
        // Given
        val exception = InvalidTokenException("토큰이 만료되었습니다")

        // When
        val response = handler.handleInvalidTokenException(exception)

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("INVALID_TOKEN", response.body!!.errorCode)
    }

    // ============================================
    // 검증 예외 테스트
    // ============================================

    @Test
    fun `handleValidationException should return 400 with validation errors`() {
        // Given
        val bindingResult = mock(BindingResult::class.java)
        val fieldErrors = listOf(
            FieldError("request", "username", "사용자명은 필수입니다"),
            FieldError("request", "email", "이메일 형식이 올바르지 않습니다")
        )
        `when`(bindingResult.fieldErrors).thenReturn(fieldErrors)

        val exception = mock(MethodArgumentNotValidException::class.java)
        `when`(exception.bindingResult).thenReturn(bindingResult)

        // When
        val response = handler.handleValidationException(exception)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("VALIDATION_ERROR", response.body!!.errorCode)
        assertNotNull(response.body!!.validationErrors)
        assertEquals(2, response.body!!.validationErrors!!.size)
        assertTrue(response.body!!.validationErrors!!.any { it.contains("username") })
        assertTrue(response.body!!.validationErrors!!.any { it.contains("email") })
    }

    @Test
    fun `handleTypeMismatchException should return 400 for invalid UUID`() {
        // Given
        val exception = mock(MethodArgumentTypeMismatchException::class.java)
        `when`(exception.name).thenReturn("channelId")
        `when`(exception.value).thenReturn("not-a-uuid")
        `when`(exception.requiredType).thenReturn(UUID::class.java)

        // When
        val response = handler.handleTypeMismatchException(exception)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("INVALID_PARAMETER_TYPE", response.body!!.errorCode)
        assertTrue(response.body!!.error!!.contains("channelId"))
    }

    @Test
    fun `handleIllegalArgumentException should return 400`() {
        // Given
        val exception = IllegalArgumentException("잘못된 입력값입니다")

        // When
        val response = handler.handleIllegalArgumentException(exception)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("INVALID_INPUT", response.body!!.errorCode)
    }

    // ============================================
    // 암호화 예외 테스트
    // ============================================

    @Test
    fun `handleKeysNotFoundException should return 404`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val exception = KeysNotFoundException(userId = userId)

        // When
        val response = handler.handleKeysNotFoundException(exception)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("KEYS_NOT_FOUND", response.body!!.errorCode)
    }

    @Test
    fun `handleKeyGenerationException should return 500 and record metric`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val exception = KeyGenerationException("키 생성 실패", userId = userId)

        // When
        val response = handler.handleKeyGenerationException(exception)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("KEY_GENERATION_ERROR", response.body!!.errorCode)

        // Verify metric was recorded
        verify(metricsService).incrementErrorCount(
            errorType = eq("KeyGenerationException"),
            operation = eq("generate"),
            tags = anyMap()
        )
    }

    @Test
    fun `handleGenericException should return 500 for unexpected errors`() {
        // Given
        val exception = RuntimeException("Unexpected error")

        // When
        val response = handler.handleGenericException(exception)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("INTERNAL_SERVER_ERROR", response.body!!.errorCode)
        // Generic error should not expose internal details
        assertEquals("서버 오류가 발생했습니다. 관리자에게 문의하세요.", response.body!!.error)
    }

    // ============================================
    // 기본 BusinessException 테스트
    // ============================================

    @Test
    fun `handleBusinessException should correctly handle custom BusinessException`() {
        // Given
        val exception = object : BusinessException(
            message = "커스텀 비즈니스 오류",
            errorCode = "CUSTOM_ERROR",
            httpStatus = HttpStatus.UNPROCESSABLE_ENTITY
        ) {}

        // When
        val response = handler.handleBusinessException(exception)

        // Then
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertEquals("CUSTOM_ERROR", response.body!!.errorCode)
        assertEquals("커스텀 비즈니스 오류", response.body!!.error)
    }
}
