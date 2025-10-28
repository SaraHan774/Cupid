package com.august.cupid.controller

import com.august.cupid.model.dto.CreateUserRequest
import com.august.cupid.model.dto.LoginRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

/**
 * AuthController 통합 테스트
 * 인증 관련 API 테스트 (회원가입, 로그인, 토큰 관리)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@TestPropertySource(locations = ["classpath:application-test.properties"])
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val testUsername = "testuser"
    private val testPassword = "password123"
    private val testEmail = "test@example.com"

    @BeforeEach
    fun setUp() {
        // 각 테스트 전에 필요한 설정
    }

    @Test
    fun `register should create new user successfully`() {
        val request = CreateUserRequest(
            username = testUsername,
            email = testEmail,
            password = testPassword
        )

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value(testUsername))
            .andExpect(jsonPath("$.data.email").value(testEmail))
            .andExpect(jsonPath("$.data.id").exists())
    }

    @Test
    fun `register should fail with duplicate username`() {
        // First registration
        val request = CreateUserRequest(
            username = "duplicate_user",
            email = "first@example.com",
            password = testPassword
        )

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)

        // Second registration with same username
        val request2 = CreateUserRequest(
            username = "duplicate_user",
            email = "second@example.com",
            password = testPassword
        )

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `login should return tokens for valid credentials`() {
        // Register first
        val registerRequest = CreateUserRequest(
            username = "login_test_user",
            email = "login@example.com",
            password = testPassword
        )

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk)

        // Login
        val loginRequest = LoginRequest(
            username = "login_test_user",
            password = testPassword
        )

        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.refreshToken").exists())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.user").exists())
    }

    @Test
    fun `login should fail with invalid credentials`() {
        // Register first
        val registerRequest = CreateUserRequest(
            username = "invalid_login_test",
            email = "invalid@example.com",
            password = testPassword
        )

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk)

        // Try to login with wrong password
        val loginRequest = LoginRequest(
            username = "invalid_login_test",
            password = "wrong_password"
        )

        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `validate token should return token info for valid token`() {
        // Register and login to get a token
        val registerRequest = CreateUserRequest(
            username = "token_validation_test",
            email = "token@example.com",
            password = testPassword
        )

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk)

        val loginRequest = LoginRequest(
            username = "token_validation_test",
            password = testPassword
        )

        val loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn()

        // Extract token from response
        val responseBody = loginResult.response.contentAsString
        val responseJson = objectMapper.readTree(responseBody)
        val accessToken = responseJson.path("data").path("accessToken").asText()

        // Validate token
        mockMvc.perform(post("/api/v1/auth/validate")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").exists())
            .andExpect(jsonPath("$.data.username").value("token_validation_test"))
    }
}
