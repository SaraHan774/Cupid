package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.*
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.MessageReadsRepository
import com.august.cupid.repository.MessageRepository
import com.august.cupid.repository.UserRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.LocalDateTime
import java.util.*

/**
 * MessageService 단위 테스트
 * 메시지 관련 비즈니스 로직 테스트
 */
class MessageServiceTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var messageReadsRepository: MessageReadsRepository
    private lateinit var channelMembersRepository: ChannelMembersRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var messageService: MessageService

    private val testUserId = UUID.randomUUID()
    private val testChannelId = UUID.randomUUID()
    private val testMessageId = UUID.randomUUID()
    private val testUsername = "testuser"
    private val testEmail = "test@example.com"

    @BeforeEach
    fun setUp() {
        messageRepository = mockk<MessageRepository>(relaxed = true)
        messageReadsRepository = mockk<MessageReadsRepository>()
        channelMembersRepository = mockk<ChannelMembersRepository>()
        userRepository = mockk<UserRepository>()
        messagingTemplate = mockk<SimpMessagingTemplate>()

        messageService = MessageService(
            messageRepository,
            messageReadsRepository,
            channelMembersRepository,
            userRepository,
            messagingTemplate
        )
    }

    @Test
    fun `{given} 유효한_채널ID와_암호화된_내용일때 {when} 메시지_전송하면 {then} 메시지가_저장되고_WebSocket으로_브로드캐스트된다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val sendMessageRequest = SendMessageRequest(
            channelId = testChannelId,
            encryptedContent = "encrypted_message_content",
            messageType = "TEXT"
        )
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = "hash",
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val channelMembers = ChannelMembers(
            id = UUID.randomUUID(),
            channel = mockk<Channel>(relaxed = true),
            user = user,
            isActive = true,
            joinedAt = LocalDateTime.now()
        )
        val savedMessage = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "encrypted_message_content",
            messageType = MessageType.TEXT,
            status = MessageStatus.SENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns channelMembers
        every { messageRepository.save(any()) } returns savedMessage
        every { messagingTemplate.convertAndSend(any<String>(), any<Any>()) } just Runs

        // When: 테스트 대상 메서드 실행
        val result = messageService.sendMessage(sendMessageRequest, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data?.id).isEqualTo(testMessageId)
        assertThat(result.data?.channelId).isEqualTo(testChannelId)
        assertThat(result.data?.senderId).isEqualTo(testUserId)
        assertThat(result.data?.encryptedContent).isEqualTo("encrypted_message_content")
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) }
        verify(exactly = 1) { messageRepository.save(any()) }
        verify(exactly = 1) { messagingTemplate.convertAndSend("/topic/channel/$testChannelId", any<Any>()) }
    }

    @Test
    fun `{given} 채널_멤버가_아닌_사용자일때 {when} 메시지_전송하면 {then} 권한_없음_오류를_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val sendMessageRequest = SendMessageRequest(
            channelId = testChannelId,
            encryptedContent = "encrypted_message_content",
            messageType = "TEXT"
        )
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = "hash",
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns null

        // When: 테스트 대상 메서드 실행
        val result = messageService.sendMessage(sendMessageRequest, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("채널에 메시지를 보낼 권한이 없습니다")
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) }
        verify(exactly = 0) { messageRepository.save(any()) }
        verify(exactly = 0) { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }
    }

    @Test
    fun `{given} 유효한_채널ID와_페이지_정보일때 {when} 메시지_조회하면 {then} 해당_채널의_메시지_목록을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val page = 0
        val size = 50
        val channelMembers = ChannelMembers(
            id = UUID.randomUUID(),
            channel = mockk<Channel>(relaxed = true),
            user = mockk<User>(relaxed = true),
            isActive = true,
            joinedAt = LocalDateTime.now()
        )
        val message1 = Message(
            id = UUID.randomUUID(),
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "message1",
            messageType = MessageType.TEXT,
            status = MessageStatus.SENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val message2 = Message(
            id = UUID.randomUUID(),
            channelId = testChannelId,
            senderId = UUID.randomUUID(),
            encryptedContent = "message2",
            messageType = MessageType.TEXT,
            status = MessageStatus.SENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val messages = listOf(message1, message2)
        val pageable = PageRequest.of(page, size)
        val messagePage = PageImpl(messages, pageable, messages.size.toLong())

        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns channelMembers
        every {
            messageRepository.findByChannelIdAndStatusNotOrderByCreatedAtDesc(
                testChannelId,
                MessageStatus.DELETED,
                pageable
            )
        } returns messagePage

        // When: 테스트 대상 메서드 실행
        val result = messageService.getChannelMessages(testChannelId, testUserId, page, size)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data?.content).hasSize(2)
        assertThat(result.data?.page).isEqualTo(page)
        assertThat(result.data?.size).isEqualTo(size)
        assertThat(result.data?.totalElements).isEqualTo(2L)
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) }
        verify(exactly = 1) {
            messageRepository.findByChannelIdAndStatusNotOrderByCreatedAtDesc(
                testChannelId,
                MessageStatus.DELETED,
                pageable
            )
        }
    }

    @Test
    fun `{given} 본인이_작성한_메시지ID와_새로운_내용일때 {when} 메시지_수정하면 {then} 메시지가_수정되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val newContent = "수정된_암호화된_내용"
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "원본_암호화된_내용",
            messageType = MessageType.TEXT,
            status = MessageStatus.SENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val updatedMessage = message.copy(
            encryptedContent = newContent,
            updatedAt = LocalDateTime.now()
        )

        every { messageRepository.findById(testMessageId) } returnsMany listOf(
            Optional.of(message),
            Optional.of(updatedMessage)
        )

        // When: 테스트 대상 메서드 실행
        val result = messageService.editMessage(testMessageId, newContent, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.message).contains("메시지가 성공적으로 수정되었습니다")
        verify(exactly = 2) { messageRepository.findById(testMessageId) }
        verify(exactly = 1) {
            messageRepository.updateMessageContent(
                testMessageId,
                newContent,
                any(),
                any<EditHistory>()
            )
        }
    }

    @Test
    fun `{given} 타인이_작성한_메시지ID일때 {when} 메시지_수정하면 {then} 권한_없음_오류를_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val newContent = "수정된_암호화된_내용"
        val otherUserId = UUID.randomUUID()
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = otherUserId,
            encryptedContent = "원본_암호화된_내용",
            messageType = MessageType.TEXT,
            status = MessageStatus.SENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { messageRepository.findById(testMessageId) } returns Optional.of(message)

        // When: 테스트 대상 메서드 실행
        val result = messageService.editMessage(testMessageId, newContent, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("메시지를 수정할 권한이 없습니다")
        verify(exactly = 1) { messageRepository.findById(testMessageId) }
        verify(exactly = 0) { messageRepository.updateMessageContent(any(), any(), any(), any()) }
    }

    @Test
    fun `{given} 본인이_작성한_메시지ID일때 {when} 메시지_삭제하면 {then} 메시지가_삭제되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = testUserId,
            encryptedContent = "암호화된_내용",
            messageType = MessageType.TEXT,
            status = MessageStatus.SENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { messageRepository.findById(testMessageId) } returns Optional.of(message)

        // When: 테스트 대상 메서드 실행
        val result = messageService.deleteMessage(testMessageId, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("메시지가 성공적으로 삭제되었습니다")
        verify(exactly = 1) { messageRepository.findById(testMessageId) }
        verify(exactly = 1) {
            messageRepository.deleteMessage(
                testMessageId,
                any<LocalDateTime>(),
                any<LocalDateTime>()
            )
        }
    }

    @Test
    fun `{given} 메시지ID와_사용자ID일때 {when} 읽음_확인_처리하면 {then} 읽음_상태가_저장되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val message = Message(
            id = testMessageId,
            channelId = testChannelId,
            senderId = UUID.randomUUID(),
            encryptedContent = "암호화된_내용",
            messageType = MessageType.TEXT,
            status = MessageStatus.SENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val channelMembers = ChannelMembers(
            id = UUID.randomUUID(),
            channel = mockk<Channel>(relaxed = true),
            user = mockk<User>(relaxed = true),
            isActive = true,
            joinedAt = LocalDateTime.now()
        )
        val messageRead = MessageReads(
            messageId = testMessageId,
            channelId = testChannelId,
            userId = testUserId,
            readAt = LocalDateTime.now()
        )

        every { messageRepository.findById(testMessageId) } returns Optional.of(message)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns channelMembers
        every { messageReadsRepository.findByMessageIdAndUserId(testMessageId, testUserId) } returns null
        every { messageReadsRepository.save(any<MessageReads>()) } returns messageRead

        // When: 테스트 대상 메서드 실행
        val result = messageService.markMessageAsRead(testMessageId, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("메시지 읽음 표시가 완료되었습니다")
        verify(exactly = 1) { messageRepository.findById(testMessageId) }
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) }
        verify(exactly = 1) { messageReadsRepository.findByMessageIdAndUserId(testMessageId, testUserId) }
        verify(exactly = 1) { messageReadsRepository.save(any<MessageReads>()) }
    }

    @Test
    fun `{given} 채널_멤버가_아닌_사용자일때 {when} 메시지_조회하면 {then} 권한_없음_오류를_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val page = 0
        val size = 50

        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) } returns null

        // When: 테스트 대상 메서드 실행
        val result = messageService.getChannelMessages(testChannelId, testUserId, page, size)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("채널에 접근할 권한이 없습니다")
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, testUserId) }
        verify(exactly = 0) { messageRepository.findByChannelIdAndStatusNotOrderByCreatedAtDesc(any(), any(), any()) }
    }
}

