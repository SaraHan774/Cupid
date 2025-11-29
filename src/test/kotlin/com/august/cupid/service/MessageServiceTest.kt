package com.august.cupid.service

import com.august.cupid.exception.*
import com.august.cupid.model.dto.SendMessageRequest
import com.august.cupid.model.entity.*
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.MessageReadsRepository
import com.august.cupid.repository.MessageRepository
import com.august.cupid.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
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
class MessageServiceTest {

    private lateinit var messageService: MessageService
    private lateinit var messageRepository: MessageRepository
    private lateinit var messageReadsRepository: MessageReadsRepository
    private lateinit var channelMembersRepository: ChannelMembersRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messagingTemplate: SimpMessagingTemplate

    private val testUserId = UUID.randomUUID()
    private val testChannelId = UUID.randomUUID()
    private val testMessageId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        messageRepository = mock(MessageRepository::class.java)
        messageReadsRepository = mock(MessageReadsRepository::class.java)
        channelMembersRepository = mock(ChannelMembersRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        messagingTemplate = mock(SimpMessagingTemplate::class.java)

        messageService = MessageService(
            messageRepository,
            messageReadsRepository,
            channelMembersRepository,
            userRepository,
            messagingTemplate
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
        `when`(userRepository.findById(testUserId)).thenReturn(Optional.empty())

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
        val user = User(id = testUserId, username = "testuser", email = "test@test.com", passwordHash = "hash")
        `when`(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(null)

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
        val user = User(id = testUserId, username = "testuser", email = "test@test.com", passwordHash = "hash")
        val membership = ChannelMembers(
            channelId = testChannelId,
            userId = testUserId,
            isActive = false
        )
        `when`(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(membership)

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
        val user = User(id = testUserId, username = "testuser", email = "test@test.com", passwordHash = "hash")
        val membership = ChannelMembers(channelId = testChannelId, userId = testUserId, isActive = true)
        val savedMessage = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "encrypted message",
            messageType = MessageType.TEXT
        )

        `when`(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(membership)
        `when`(messageRepository.save(any(Message::class.java))).thenReturn(savedMessage)

        // When
        val result = messageService.sendMessage(request, testUserId)

        // Then
        assertNotNull(result)
        assertEquals(testChannelId, result.channelId)
        assertEquals(testUserId, result.senderId)
        assertEquals("encrypted message", result.encryptedContent)
        verify(messageRepository).save(any(Message::class.java))
    }

    // ============================================
    // getChannelMessages 테스트
    // ============================================

    @Test
    fun `getChannelMessages should throw ChannelAccessDeniedException when user has no access`() {
        // Given
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(null)

        // When & Then
        assertThrows<ChannelAccessDeniedException> {
            messageService.getChannelMessages(testChannelId, testUserId)
        }
    }

    @Test
    fun `getChannelMessages should return paged messages for valid member`() {
        // Given
        val membership = ChannelMembers(channelId = testChannelId, userId = testUserId, isActive = true)
        val messages = listOf(
            Message(channelId = testChannelId, senderId = testUserId, encryptedContent = "msg1"),
            Message(channelId = testChannelId, senderId = testUserId, encryptedContent = "msg2")
        )
        val page = PageImpl(messages, PageRequest.of(0, 50), 2)

        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(membership)
        `when`(messageRepository.findByChannelIdAndStatusNotOrderByCreatedAtDesc(
            eq(testChannelId),
            eq(MessageStatus.DELETED),
            any()
        )).thenReturn(page)

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.empty())

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(null)

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
        val membership = ChannelMembers(channelId = testChannelId, userId = testUserId, isActive = true)

        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(membership)

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.empty())

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))

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

        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))
        `when`(messageRepository.updateMessageContent(
            eq(testMessageId),
            eq("new content"),
            eq("old content")
        )).thenReturn(true)
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(updatedMessage))

        // When
        val result = messageService.editMessage(testMessageId, "new content", testUserId)

        // Then
        assertEquals("new content", result.encryptedContent)
        verify(messageRepository).updateMessageContent(testMessageId, "new content", "old content")
    }

    // ============================================
    // deleteMessage 테스트
    // ============================================

    @Test
    fun `deleteMessage should throw MessageNotFoundException when message does not exist`() {
        // Given
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.empty())

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))

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
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))
        `when`(messageRepository.softDeleteMessage(testMessageId)).thenReturn(true)

        // When
        messageService.deleteMessage(testMessageId, testUserId)

        // Then
        verify(messageRepository).softDeleteMessage(testMessageId)
    }

    // ============================================
    // markMessageAsRead 테스트
    // ============================================

    @Test
    fun `markMessageAsRead should throw MessageNotFoundException when message does not exist`() {
        // Given
        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.empty())

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
        val membership = ChannelMembers(channelId = testChannelId, userId = testUserId, isActive = true)
        val existingRead = MessageReads(
            messageId = testMessageId,
            channelId = testChannelId,
            userId = testUserId
        )

        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(membership)
        `when`(messageReadsRepository.findByMessageIdAndUserId(testMessageId, testUserId)).thenReturn(existingRead)

        // When
        val result = messageService.markMessageAsRead(testMessageId, testUserId)

        // Then
        assertTrue(result)
        verify(messageReadsRepository, never()).save(any(MessageReads::class.java))
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
        val membership = ChannelMembers(channelId = testChannelId, userId = testUserId, isActive = true)

        `when`(messageRepository.findById(testMessageId)).thenReturn(Optional.of(message))
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(membership)
        `when`(messageReadsRepository.findByMessageIdAndUserId(testMessageId, testUserId)).thenReturn(null)

        // When
        val result = messageService.markMessageAsRead(testMessageId, testUserId)

        // Then
        assertFalse(result)
        verify(messageReadsRepository).save(any(MessageReads::class.java))
    }

    // ============================================
    // getUnreadMessageCount 테스트
    // ============================================

    @Test
    fun `getUnreadMessageCount should throw ChannelAccessDeniedException when user has no access`() {
        // Given
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(null)

        // When & Then
        assertThrows<ChannelAccessDeniedException> {
            messageService.getUnreadMessageCount(testChannelId, testUserId)
        }
    }

    @Test
    fun `getUnreadMessageCount should return count for valid member`() {
        // Given
        val membership = ChannelMembers(
            channelId = testChannelId,
            userId = testUserId,
            isActive = true,
            lastReadAt = LocalDateTime.now().minusHours(1)
        )
        `when`(channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId)).thenReturn(membership)
        `when`(messageReadsRepository.countUnreadMessagesByChannelAndUser(
            eq(testChannelId),
            eq(testUserId),
            any()
        )).thenReturn(5L)

        // When
        val result = messageService.getUnreadMessageCount(testChannelId, testUserId)

        // Then
        assertEquals(5L, result)
    }
}
