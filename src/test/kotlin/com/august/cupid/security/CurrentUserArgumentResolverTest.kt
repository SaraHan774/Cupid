package com.august.cupid.security

import com.august.cupid.exception.UnauthorizedException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.NativeWebRequest
import java.util.*

/**
 * CurrentUserArgumentResolver 단위 테스트
 *
 * 검증 항목:
 * - UUID 형식의 인증 정보 추출
 * - 인증되지 않은 요청 처리
 * - 유효하지 않은 UUID 처리
 */
class CurrentUserArgumentResolverTest {

    private lateinit var resolver: CurrentUserArgumentResolver
    private lateinit var methodParameter: MethodParameter
    private lateinit var webRequest: NativeWebRequest

    @BeforeEach
    fun setUp() {
        resolver = CurrentUserArgumentResolver()
        methodParameter = mock(MethodParameter::class.java)
        webRequest = mock(NativeWebRequest::class.java)

        // Clear security context before each test
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `supportsParameter should return true when @CurrentUser and UUID type`() {
        // Given
        `when`(methodParameter.hasParameterAnnotation(CurrentUser::class.java)).thenReturn(true)
        `when`(methodParameter.parameterType).thenReturn(UUID::class.java)

        // When
        val result = resolver.supportsParameter(methodParameter)

        // Then
        assertTrue(result)
    }

    @Test
    fun `supportsParameter should return false when no @CurrentUser annotation`() {
        // Given
        `when`(methodParameter.hasParameterAnnotation(CurrentUser::class.java)).thenReturn(false)
        `when`(methodParameter.parameterType).thenReturn(UUID::class.java)

        // When
        val result = resolver.supportsParameter(methodParameter)

        // Then
        assertFalse(result)
    }

    @Test
    fun `supportsParameter should return false when not UUID type`() {
        // Given
        `when`(methodParameter.hasParameterAnnotation(CurrentUser::class.java)).thenReturn(true)
        `when`(methodParameter.parameterType).thenReturn(String::class.java)

        // When
        val result = resolver.supportsParameter(methodParameter)

        // Then
        assertFalse(result)
    }

    @Test
    fun `resolveArgument should return UUID when authentication contains valid UUID string`() {
        // Given
        val expectedUserId = UUID.randomUUID()
        val authentication = UsernamePasswordAuthenticationToken(expectedUserId.toString(), null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        // When
        val result = resolver.resolveArgument(methodParameter, null, webRequest, null)

        // Then
        assertEquals(expectedUserId, result)
    }

    @Test
    fun `resolveArgument should return UUID when authentication principal is UUID`() {
        // Given
        val expectedUserId = UUID.randomUUID()
        val authentication = UsernamePasswordAuthenticationToken(expectedUserId, null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        // When
        val result = resolver.resolveArgument(methodParameter, null, webRequest, null)

        // Then
        assertEquals(expectedUserId, result)
    }

    @Test
    fun `resolveArgument should throw UnauthorizedException when no authentication`() {
        // Given
        SecurityContextHolder.clearContext()

        // When & Then
        val exception = assertThrows<UnauthorizedException> {
            resolver.resolveArgument(methodParameter, null, webRequest, null)
        }
        assertEquals("인증 정보가 없습니다", exception.message)
    }

    @Test
    fun `resolveArgument should throw UnauthorizedException when authentication principal is null`() {
        // Given
        val authentication = UsernamePasswordAuthenticationToken(null, null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        // When & Then
        val exception = assertThrows<UnauthorizedException> {
            resolver.resolveArgument(methodParameter, null, webRequest, null)
        }
        assertEquals("인증 정보를 찾을 수 없습니다", exception.message)
    }

    @Test
    fun `resolveArgument should throw UnauthorizedException when principal is not valid UUID`() {
        // Given
        val authentication = UsernamePasswordAuthenticationToken("invalid-uuid", null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        // When & Then
        val exception = assertThrows<UnauthorizedException> {
            resolver.resolveArgument(methodParameter, null, webRequest, null)
        }
        assertEquals("유효하지 않은 인증 정보입니다", exception.message)
    }

    @Test
    fun `resolveArgument should throw UnauthorizedException when principal is unsupported type`() {
        // Given
        val authentication = UsernamePasswordAuthenticationToken(12345L, null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        // When & Then
        val exception = assertThrows<UnauthorizedException> {
            resolver.resolveArgument(methodParameter, null, webRequest, null)
        }
        assertEquals("지원하지 않는 인증 타입입니다", exception.message)
    }
}
