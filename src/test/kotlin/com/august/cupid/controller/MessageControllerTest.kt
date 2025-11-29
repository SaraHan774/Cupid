package com.august.cupid.controller

import com.august.cupid.model.dto.CreateUserRequest
import com.august.cupid.model.dto.LoginRequest
import com.august.cupid.model.dto.SendMessageRequest
import com.august.cupid.model.entity.Channel
import com.august.cupid.model.entity.ChannelMembers
import com.august.cupid.model.entity.ChannelType
import com.august.cupid.model.entity.User
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.ChannelRepository
import com.august.cupid.repository.MessageRepository
import com.august.cupid.repository.UserRepository
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
import java.util.*

/**
 * MessageController 통합 테스트
 *
 * 검증 항목:
 * - @CurrentUser 어노테이션 동작
 * - 타입화된 응답 구조
 * - 예외 발생 시 GlobalExceptionHandler 처리
 * - UUID 경로 변수 자동 변환
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@TestPropertySource(locations = ["classpath:application-test.properties"])
class MessageControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var channelRepository: ChannelRepository

    @Autowired
    private lateinit var channelMembersRepository: ChannelMembersRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private var accessToken: String = ""
    private var userId: UUID = UUID.randomUUID()
    private var channelId: UUID = UUID.randomUUID()
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        // Create test user and get token
        val registerRequest = CreateUserRequest(
            username = "message_test_user_${System.currentTimeMillis()}",
            email = "message_${System.currentTimeMillis()}@example.com",
            password = "password123"
        )

        val registerResult = mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)))
            .andReturn()

        val registerResponse = objectMapper.readTree(registerResult.response.contentAsString)
        userId = UUID.fromString(registerResponse.path("data").path("id").asText())

        val loginRequest = LoginRequest(
            username = registerRequest.username,
            password = registerRequest.password
        )

        val loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn()

        val loginResponse = objectMapper.readTree(loginResult.response.contentAsString)
        accessToken = loginResponse.path("data").path("accessToken").asText()

        // Get the user entity for channel/membership creation
        testUser = userRepository.findById(userId).orElseThrow()

        // Create test channel and membership
        val channel = Channel(type = ChannelType.DIRECT, name = "test-channel", creator = testUser, match = null)
        val savedChannel = channelRepository.save(channel)
        channelId = savedChannel.id!!

        val membership = ChannelMembers(
            channel = savedChannel,
            user = testUser,
            isActive = true
        )
        channelMembersRepository.save(membership)
    }

    // ============================================
    // @CurrentUser 어노테이션 테스트
    // ============================================

    @Test
    fun `request without token should return 403 Forbidden`() {
        mockMvc.perform(get("/api/v1/chat/channels/$channelId/messages"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `request with invalid token should return 403 Forbidden`() {
        mockMvc.perform(get("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `request with valid token should use @CurrentUser correctly`() {
        mockMvc.perform(get("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    // ============================================
    // UUID 경로 변수 자동 변환 테스트
    // ============================================

    @Test
    fun `invalid UUID in path should return 400 Bad Request`() {
        mockMvc.perform(get("/api/v1/chat/channels/not-a-uuid/messages")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER_TYPE"))
    }

    // ============================================
    // 타입화된 응답 구조 테스트
    // ============================================

    @Test
    fun `getChannelMessages should return typed ApiResponse with PagedResponse`() {
        mockMvc.perform(get("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken")
            .param("page", "0")
            .param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data.content").isArray)
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").exists())
            .andExpect(jsonPath("$.data.totalElements").exists())
            .andExpect(jsonPath("$.data.totalPages").exists())
            .andExpect(jsonPath("$.data.hasNext").exists())
            .andExpect(jsonPath("$.data.hasPrevious").exists())
    }

    @Test
    fun `sendMessage should return typed ApiResponse with MessageResponse`() {
        val request = SendMessageRequest(
            channelId = channelId,
            encryptedContent = "encrypted test message",
            messageType = "TEXT"
        )

        mockMvc.perform(post("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("메시지가 전송되었습니다"))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.channelId").value(channelId.toString()))
            .andExpect(jsonPath("$.data.senderId").value(userId.toString()))
            .andExpect(jsonPath("$.data.encryptedContent").value("encrypted test message"))
            .andExpect(jsonPath("$.data.messageType").value("TEXT"))
            .andExpect(jsonPath("$.data.status").value("SENT"))
    }

    // ============================================
    // 예외 처리 테스트 (GlobalExceptionHandler 연동)
    // ============================================

    @Test
    fun `access to non-member channel should return 403 with proper error structure`() {
        // Create another channel without membership
        val anotherChannel = channelRepository.save(
            Channel(type = ChannelType.DIRECT, name = "another-channel", creator = testUser, match = null)
        )

        mockMvc.perform(get("/api/v1/chat/channels/${anotherChannel.id}/messages")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("CHANNEL_ACCESS_DENIED"))
    }

    @Test
    fun `get non-existent message should return 404`() {
        val nonExistentMessageId = UUID.randomUUID()

        mockMvc.perform(post("/api/v1/chat/messages/$nonExistentMessageId/read")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("MESSAGE_NOT_FOUND"))
    }

    // ============================================
    // 메시지 CRUD 통합 테스트
    // ============================================

    @Test
    fun `full message lifecycle - create, read, update, delete`() {
        // 1. Create message
        val createRequest = SendMessageRequest(
            channelId = channelId,
            encryptedContent = "original message",
            messageType = "TEXT"
        )

        val createResult = mockMvc.perform(post("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val messageId = createResponse.path("data").path("id").asText()

        // 2. Read messages (should contain created message)
        mockMvc.perform(get("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalElements").value(1))

        // 3. Update message
        mockMvc.perform(put("/api/v1/chat/messages/$messageId")
            .header("Authorization", "Bearer $accessToken")
            .param("newContent", "updated message"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("메시지가 수정되었습니다"))
            .andExpect(jsonPath("$.data.encryptedContent").value("updated message"))

        // 4. Delete message
        mockMvc.perform(delete("/api/v1/chat/messages/$messageId")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("메시지가 삭제되었습니다"))

        // 5. Verify message is no longer in list (soft deleted)
        mockMvc.perform(get("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalElements").value(0))
    }

    // ============================================
    // 읽지 않은 메시지 수 테스트
    // ============================================

    @Test
    fun `getUnreadCount should return typed ApiResponse with Long`() {
        mockMvc.perform(get("/api/v1/chat/channels/$channelId/unread-count")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isNumber)
    }

    // ============================================
    // 읽음 표시 테스트
    // ============================================

    @Test
    fun `markAsRead should return success for valid message`() {
        // First create a message
        val createRequest = SendMessageRequest(
            channelId = channelId,
            encryptedContent = "message to read",
            messageType = "TEXT"
        )

        val createResult = mockMvc.perform(post("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createRequest)))
            .andReturn()

        val messageId = objectMapper.readTree(createResult.response.contentAsString)
            .path("data").path("id").asText()

        // Mark as read
        mockMvc.perform(post("/api/v1/chat/messages/$messageId/read")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Mark as read again (should still succeed but with different message)
        mockMvc.perform(post("/api/v1/chat/messages/$messageId/read")
            .header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("이미 읽음 표시가 되어 있습니다"))
    }

    // ============================================
    // 권한 테스트
    // ============================================

    @Test
    fun `edit message by non-sender should return 403`() {
        // Create message as current user
        val createRequest = SendMessageRequest(
            channelId = channelId,
            encryptedContent = "message",
            messageType = "TEXT"
        )

        val createResult = mockMvc.perform(post("/api/v1/chat/channels/$channelId/messages")
            .header("Authorization", "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createRequest)))
            .andReturn()

        val messageId = objectMapper.readTree(createResult.response.contentAsString)
            .path("data").path("id").asText()

        // Create another user
        val otherUserRegister = CreateUserRequest(
            username = "other_user_${System.currentTimeMillis()}",
            email = "other_${System.currentTimeMillis()}@example.com",
            password = "password123"
        )

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(otherUserRegister)))

        val otherLoginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(LoginRequest(
                username = otherUserRegister.username,
                password = otherUserRegister.password
            ))))
            .andReturn()

        val otherUserResponse = objectMapper.readTree(otherLoginResult.response.contentAsString)
        val otherUserId = UUID.fromString(otherUserResponse.path("data").path("user").path("id").asText())
        val otherToken = otherUserResponse.path("data").path("accessToken").asText()

        // Get the other user entity and the channel
        val otherUser = userRepository.findById(otherUserId).orElseThrow()
        val channel = channelRepository.findById(channelId).orElseThrow()

        // Add other user to channel
        channelMembersRepository.save(ChannelMembers(
            channel = channel,
            user = otherUser,
            isActive = true
        ))

        // Try to edit message as other user
        mockMvc.perform(put("/api/v1/chat/messages/$messageId")
            .header("Authorization", "Bearer $otherToken")
            .param("newContent", "hacked message"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("MESSAGE_ACCESS_DENIED"))
    }
}
