package com.august.cupid.service

import com.august.cupid.exception.*
import com.august.cupid.model.dto.SendMessageRequest
import com.august.cupid.model.entity.*
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.MessageReadsRepository
import com.august.cupid.repository.MessageRepository
import com.august.cupid.repository.UserRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.LocalDateTime
import java.util.*

/**
 * MessageService 단위 테스트
 *
 * 검증 항목:
 * - 메시지 전송 시 예외 처리
 * - 채널 접근 권한 검증
 * - 메시지 수정/삭제 권한 검증
 * - 정상 동작 확인
 */
@ExtendWith(MockKExtension::class)
class MessageServiceTest {

    @MockK
    private lateinit var messageRepository: MessageRepository

    @MockK
    private lateinit var messageReadsRepository: MessageReadsRepository

    @MockK
    private lateinit var channelMembersRepository: ChannelMembersRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var messagingTemplate: SimpMessagingTemplate

    private lateinit var messageService: MessageService

    private val testUserId = UUID.randomUUID()
    private val testChannelId = UUID.randomUUID()
    private val testMessageId = UUID.randomUUID()

    // Test fixtures
    private lateinit var testUser: User
    private lateinit var testChannel: Channel

    @BeforeEach
    fun setUp() {
        // Create test fixtures
        testUser = User(id = testUserId, username = "testuser", email = "test@test.com", passwordHash = "hash")
        testChannel = Channel(id = testChannelId, type = ChannelType.DIRECT, name = "test channel", creator = testUser, match = null)

        messageService = MessageService(
            messageRepository,
            messageReadsRepository,
            channelMembersRepository,
            userRepository,
            messagingTemplate
        )
    }

    // Helper method to create ChannelMembers
    private fun createMembership(
        channel: Channel = testChannel,
        user: User = testUser,
        isActive: Boolean = true,
        lastReadAt: LocalDateTime? = null
    ): ChannelMembers {
        return ChannelMembers(
            channel = channel,
            user = user,
            isActive = isActive,
            lastReadAt = lastReadAt
        )
    }

    // ============================================
    // sendMessage 테스트
    // ============================================

    @Test
    fun `sendMessage should throw BadRequestException when channelId is null`() {
        // Given
        val request = SendMessageRequest(
            channelId = null,
            encryptedContent = "test content",
            messageType = "TEXT"
        )

        // When & Then
        val exception = assertThrows<BadRequestException> {
            messageService.sendMessage(request, testUserId)
        }
        assertEquals("채널 ID는 필수입니다", exception.message)
        assertEquals("channelId", exception.field)
    }

    @Test
    fun `sendMessage should throw UserNotFoundException when sender does not exist`() {
        // Given
        val request = SendMessageRequest(
            channelId = testChannelId,
            encryptedContent = "test content",
            messageType = "TEXT"
        )
        every { userRepository.findById(testUserId) } returns Optional.empty()

        // When & Then
        val exception = assertThrows<UserNotFoundException> {
            messageService.sendMessage(request, testUserId)
        }
        assertTrue(exception.message!!.contains(testUserId.toString()))
    }

    @Test
    fun `sendMessage should throw ChannelAccessDeniedException when user is not channel member`() {
        // Given
        val request = SendMessageRequest(
            channelId = testChannelId,
            encryptedContent = "test content",
            messageType = "TEXT"
        )
        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns null

        // When & Then
        assertThrows<ChannelAccessDeniedException> {
            messageService.sendMessage(request, testUserId)
        }
    }

    @Test
    fun `sendMessage should throw ChannelAccessDeniedException when membership is not active`() {
        // Given
        val request = SendMessageRequest(
            channelId = testChannelId,
            encryptedContent = "test content",
            messageType = "TEXT"
        )
        val membership = createMembership(isActive = false)
        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns membership

        // When & Then
        assertThrows<ChannelAccessDeniedException> {
            messageService.sendMessage(request, testUserId)
        }
    }

    @Test
    fun `sendMessage should successfully create and return message`() {
        // Given
        val request = SendMessageRequest(
            channelId = testChannelId,
            encryptedContent = "encrypted message",
            messageType = "TEXT"
        )
        val membership = createMembership()
        val savedMessage = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "encrypted message",
            messageType = MessageType.TEXT
        )

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns membership
        every { messageRepository.save(any<Message>()) } returns savedMessage
        every { messagingTemplate.convertAndSend(any<String>(), any<Any>()) } just Runs

        // When
        val result = messageService.sendMessage(request, testUserId)

        // Then
        assertNotNull(result)
        assertEquals(testChannelId, result.channelId)
        assertEquals(testUserId, result.senderId)
        assertEquals("encrypted message", result.encryptedContent)
        verify { messageRepository.save(any<Message>()) }
    }

    // ============================================
    // getChannelMessages 테스트
    // ============================================

    @Test
    fun `getChannelMessages should throw ChannelAccessDeniedException when user has no access`() {
        // Given
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns null

        // When & Then
        assertThrows<ChannelAccessDeniedException> {
            messageService.getChannelMessages(testChannelId, testUserId)
        }
    }

    @Test
    fun `getChannelMessages should return paged messages for valid member`() {
        // Given
        val membership = createMembership()
        val messages = listOf(
            Message(channelId = testChannelId, senderId = testUserId, encryptedContent = "msg1"),
            Message(channelId = testChannelId, senderId = testUserId, encryptedContent = "msg2")
        )
        val page = PageImpl(messages, PageRequest.of(0, 50), 2)

        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns membership
        every {
            messageRepository.findByChannelIdAndStatusNotOrderByCreatedAtDesc(
                testChannelId,
                MessageStatus.DELETED,
                any<Pageable>()
            )
        } returns page

        // When
        val result = messageService.getChannelMessages(testChannelId, testUserId)

        // Then
        assertEquals(2, result.content.size)
        assertEquals(0, result.page)
        assertEquals(2, result.totalElements)
    }

    // ============================================
    // getMessageById 테스트
    // ============================================

    @Test
    fun `getMessageById should throw MessageNotFoundException when message does not exist`() {
        // Given
        every { messageRepository.findById(testMessageId) } returns Optional.empty()

        // When & Then
        assertThrows<MessageNotFoundException> {
            messageService.getMessageById(testMessageId, testUserId)
        }
    }

    @Test
    fun `getMessageById should throw ChannelAccessDeniedException when user has no channel access`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = UUID.randomUUID(),
            encryptedContent = "content"
        )
        every { messageRepository.findById(testMessageId) } returns Optional.of(message)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns null

        // When & Then
        assertThrows<ChannelAccessDeniedException> {
            messageService.getMessageById(testMessageId, testUserId)
        }
    }

    @Test
    fun `getMessageById should return message for valid request`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "content"
        )
        val membership = createMembership()

        every { messageRepository.findById(testMessageId) } returns Optional.of(message)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns membership

        // When
        val result = messageService.getMessageById(testMessageId, testUserId)

        // Then
        assertEquals(testMessageId, result.id)
        assertEquals(testChannelId, result.channelId)
    }

    // ============================================
    // editMessage 테스트
    // ============================================

    @Test
    fun `editMessage should throw MessageNotFoundException when message does not exist`() {
        // Given
        every { messageRepository.findById(testMessageId) } returns Optional.empty()

        // When & Then
        assertThrows<MessageNotFoundException> {
            messageService.editMessage(testMessageId, "new content", testUserId)
        }
    }

    @Test
    fun `editMessage should throw MessageAccessDeniedException when user is not sender`() {
        // Given
        val otherUserId = UUID.randomUUID()
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = otherUserId,
            encryptedContent = "content"
        )
        every { messageRepository.findById(testMessageId) } returns Optional.of(message)

        // When & Then
        val exception = assertThrows<MessageAccessDeniedException> {
            messageService.editMessage(testMessageId, "new content", testUserId)
        }
        assertEquals("메시지를 수정할 권한이 없습니다", exception.message)
    }

    @Test
    fun `editMessage should throw BadRequestException when message is deleted`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "content",
            status = MessageStatus.DELETED
        )
        every { messageRepository.findById(testMessageId) } returns Optional.of(message)

        // When & Then
        val exception = assertThrows<BadRequestException> {
            messageService.editMessage(testMessageId, "new content", testUserId)
        }
        assertEquals("삭제된 메시지는 수정할 수 없습니다", exception.message)
    }

    @Test
    fun `editMessage should successfully update message`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "old content"
        )
        val updatedMessage = message.copy(encryptedContent = "new content")

        every { messageRepository.findById(testMessageId) } returns Optional.of(message) andThen Optional.of(updatedMessage)
        every {
            messageRepository.updateMessageContent(
                testMessageId,
                "new content",
                "old content"
            )
        } returns true

        // When
        val result = messageService.editMessage(testMessageId, "new content", testUserId)

        // Then
        assertEquals("new content", result.encryptedContent)
        verify { messageRepository.updateMessageContent(testMessageId, "new content", "old content") }
    }

    // ============================================
    // deleteMessage 테스트
    // ============================================

    @Test
    fun `deleteMessage should throw MessageNotFoundException when message does not exist`() {
        // Given
        every { messageRepository.findById(testMessageId) } returns Optional.empty()

        // When & Then
        assertThrows<MessageNotFoundException> {
            messageService.deleteMessage(testMessageId, testUserId)
        }
    }

    @Test
    fun `deleteMessage should throw MessageAccessDeniedException when user is not sender`() {
        // Given
        val otherUserId = UUID.randomUUID()
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = otherUserId,
            encryptedContent = "content"
        )
        every { messageRepository.findById(testMessageId) } returns Optional.of(message)

        // When & Then
        val exception = assertThrows<MessageAccessDeniedException> {
            messageService.deleteMessage(testMessageId, testUserId)
        }
        assertEquals("메시지를 삭제할 권한이 없습니다", exception.message)
    }

    @Test
    fun `deleteMessage should throw BadRequestException when message is already deleted`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "content",
            status = MessageStatus.DELETED
        )
        every { messageRepository.findById(testMessageId) } returns Optional.of(message)

        // When & Then
        val exception = assertThrows<BadRequestException> {
            messageService.deleteMessage(testMessageId, testUserId)
        }
        assertEquals("이미 삭제된 메시지입니다", exception.message)
    }

    @Test
    fun `deleteMessage should successfully soft delete message`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "content"
        )
        every { messageRepository.findById(testMessageId) } returns Optional.of(message)
        every { messageRepository.softDeleteMessage(testMessageId) } returns true

        // When
        messageService.deleteMessage(testMessageId, testUserId)

        // Then
        verify { messageRepository.softDeleteMessage(testMessageId) }
    }

    // ============================================
    // markMessageAsRead 테스트
    // ============================================

    @Test
    fun `markMessageAsRead should throw MessageNotFoundException when message does not exist`() {
        // Given
        every { messageRepository.findById(testMessageId) } returns Optional.empty()

        // When & Then
        assertThrows<MessageNotFoundException> {
            messageService.markMessageAsRead(testMessageId, testUserId)
        }
    }

    @Test
    fun `markMessageAsRead should return true when already marked as read`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = UUID.randomUUID(),
            encryptedContent = "content"
        )
        val membership = createMembership()
        val existingRead = MessageReads(
            messageId = testMessageId,
            channelId = testChannelId,
            userId = testUserId
        )

        every { messageRepository.findById(testMessageId) } returns Optional.of(message)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns membership
        every { messageReadsRepository.findByMessageIdAndUserId(testMessageId, testUserId) } returns existingRead

        // When
        val result = messageService.markMessageAsRead(testMessageId, testUserId)

        // Then
        assertTrue(result)
        verify(exactly = 0) { messageReadsRepository.save(any<MessageReads>()) }
    }

    @Test
    fun `markMessageAsRead should create new read receipt and return false`() {
        // Given
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = UUID.randomUUID(),
            encryptedContent = "content"
        )
        val membership = createMembership()

        every { messageRepository.findById(testMessageId) } returns Optional.of(message)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns membership
        every { messageReadsRepository.findByMessageIdAndUserId(testMessageId, testUserId) } returns null
        every { messageReadsRepository.save(any<MessageReads>()) } returns mockk()

        // When
        val result = messageService.markMessageAsRead(testMessageId, testUserId)

        // Then
        assertFalse(result)
        verify { messageReadsRepository.save(any<MessageReads>()) }
    }

    // ============================================
    // getUnreadMessageCount 테스트
    // ============================================

    @Test
    fun `getUnreadMessageCount should throw ChannelAccessDeniedException when user has no access`() {
        // Given
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns null

        // When & Then
        assertThrows<ChannelAccessDeniedException> {
            messageService.getUnreadMessageCount(testChannelId, testUserId)
        }
    }

    @Test
    fun `getUnreadMessageCount should return count for valid member`() {
        // Given
        val membership = createMembership(lastReadAt = LocalDateTime.now().minusHours(1))
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns membership
        every {
            messageReadsRepository.countUnreadMessagesByChannelAndUser(
                testChannelId,
                testUserId,
                any<LocalDateTime>()
            )
        } returns 5L

        // When
        val result = messageService.getUnreadMessageCount(testChannelId, testUserId)

        // Then
        assertEquals(5L, result)
    }
}
