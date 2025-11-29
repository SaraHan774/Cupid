package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.*
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.ChannelRepository
import com.august.cupid.repository.MatchRepository
import com.august.cupid.repository.UserRepository
import io.mockk.*
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.LocalDateTime
import java.util.*

/**
 * ChannelService 단위 테스트
 * 채널 관련 비즈니스 로직 테스트
 */
class ChannelServiceTest {

    private lateinit var channelRepository: ChannelRepository
    private lateinit var channelMembersRepository: ChannelMembersRepository
    private lateinit var userRepository: UserRepository
    private lateinit var matchRepository: MatchRepository
    private lateinit var entityManager: EntityManager
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var channelService: ChannelService

    private val testUserId = UUID.randomUUID()
    private val testChannelId = UUID.randomUUID()
    private val testMatchId = UUID.randomUUID()
    private val testUsername = "testuser"
    private val testEmail = "test@example.com"

    @BeforeEach
    fun setUp() {
        channelRepository = mockk<ChannelRepository>(relaxed = true)
        channelMembersRepository = mockk<ChannelMembersRepository>(relaxed = true)
        userRepository = mockk<UserRepository>()
        matchRepository = mockk<MatchRepository>()
        entityManager = mockk<EntityManager>(relaxed = true)
        messagingTemplate = mockk<SimpMessagingTemplate>()

        channelService = ChannelService(
            channelRepository,
            channelMembersRepository,
            userRepository,
            matchRepository,
            entityManager,
            messagingTemplate
        )
    }

    @Test
    fun `{given} 1대1_채널_타입과_매칭ID일때 {when} 채널_생성하면 {then} 채널이_생성되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val createChannelRequest = CreateChannelRequest(
            type = "DIRECT",
            matchId = testMatchId,
            name = null
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
        val user2 = User(
            id = UUID.randomUUID(),
            username = "user2",
            email = "user2@example.com",
            passwordHash = "hash",
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val match = Match(
            id = testMatchId,
            user1 = user,
            user2 = user2,
            matchedAt = LocalDateTime.now()
        )
        val savedChannel = Channel(
            id = testChannelId,
            type = ChannelType.DIRECT,
            name = null,
            creator = user,
            match = match,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { matchRepository.findById(testMatchId) } returns Optional.of(match)
        every { channelRepository.existsByMatchId(testMatchId) } returns false
        every { channelRepository.save(any()) } returns savedChannel
        every { channelRepository.flush() } just Runs
        every { channelMembersRepository.save(any<ChannelMembers>()) } returns mockk<ChannelMembers>(relaxed = true)

        // When: 테스트 대상 메서드 실행
        val result = channelService.createChannel(createChannelRequest, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data?.id).isEqualTo(testChannelId)
        assertThat(result.data?.type).isEqualTo("DIRECT")
        assertThat(result.data?.matchId).isEqualTo(testMatchId)
        assertThat(result.message).contains("채널이 성공적으로 생성되었습니다")
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 2) { matchRepository.findById(testMatchId) } // 52번 라인과 65번 라인에서 각각 호출
        verify(exactly = 1) { channelRepository.existsByMatchId(testMatchId) }
        verify(exactly = 1) { channelRepository.save(any()) }
        verify(exactly = 1) { channelMembersRepository.save(any<ChannelMembers>()) }
    }

    @Test
    fun `{given} 그룹_채널_타입과_채널명일때 {when} 채널_생성하면 {then} 채널이_생성되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val channelName = "테스트 그룹 채널"
        val createChannelRequest = CreateChannelRequest(
            type = "GROUP",
            matchId = null,
            name = channelName
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
        val savedChannel = Channel(
            id = testChannelId,
            type = ChannelType.GROUP,
            name = channelName,
            creator = user,
            match = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { channelRepository.save(any()) } returns savedChannel
        every { channelRepository.flush() } just Runs
        every { channelMembersRepository.save(any<ChannelMembers>()) } returns mockk<ChannelMembers>(relaxed = true)

        // When: 테스트 대상 메서드 실행
        val result = channelService.createChannel(createChannelRequest, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data?.id).isEqualTo(testChannelId)
        assertThat(result.data?.type).isEqualTo("GROUP")
        assertThat(result.data?.name).isEqualTo(channelName)
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 0) { matchRepository.findById(any()) }
        verify(exactly = 1) { channelRepository.save(any()) }
        verify(exactly = 1) { channelMembersRepository.save(any<ChannelMembers>()) }
    }

    @Test
    fun `{given} 이미_존재하는_매칭ID일때 {when} 채널_생성하면 {then} 중복_오류를_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val createChannelRequest = CreateChannelRequest(
            type = "DIRECT",
            matchId = testMatchId,
            name = null
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
        val user2 = User(
            id = UUID.randomUUID(),
            username = "user2",
            email = "user2@example.com",
            passwordHash = "hash",
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val match = Match(
            id = testMatchId,
            user1 = user,
            user2 = user2,
            matchedAt = LocalDateTime.now()
        )

        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { matchRepository.findById(testMatchId) } returns Optional.of(match)
        every { channelRepository.existsByMatchId(testMatchId) } returns true

        // When: 테스트 대상 메서드 실행
        val result = channelService.createChannel(createChannelRequest, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.data).isNull()
        assertThat(result.message).contains("이미 해당 매칭으로 채널이 생성되었습니다")
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 1) { matchRepository.findById(testMatchId) }
        verify(exactly = 1) { channelRepository.existsByMatchId(testMatchId) }
        verify(exactly = 0) { channelRepository.save(any()) }
    }

    @Test
    fun `{given} 유효한_사용자ID일때 {when} 채널_조회하면 {then} 해당_사용자의_채널_목록을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val user = User(
            id = testUserId,
            username = testUsername,
            email = testEmail,
            passwordHash = "hash",
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val channel1 = Channel(
            id = testChannelId,
            type = ChannelType.DIRECT,
            name = null,
            creator = user,
            match = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val channel2 = Channel(
            id = UUID.randomUUID(),
            type = ChannelType.GROUP,
            name = "그룹 채널",
            creator = user,
            match = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { channelRepository.findChannelsByUserId(testUserId) } returns listOf(channel1, channel2)

        // When: 테스트 대상 메서드 실행
        val result = channelService.getUserChannels(testUserId, 0, 20)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.data).isNotNull()
        assertThat(result.data).hasSize(2)
        assertThat(result.data?.first()?.id).isEqualTo(testChannelId)
        verify(exactly = 1) { channelRepository.findChannelsByUserId(testUserId) }
    }

    @Test
    fun `{given} 채널ID와_사용자ID일때 {when} 채널_멤버_추가하면 {then} 멤버가_추가되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val newUserId = UUID.randomUUID()
        val inviterId = testUserId
        val user = User(
            id = newUserId,
            username = "newuser",
            email = "new@example.com",
            passwordHash = "hash",
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val channel = Channel(
            id = testChannelId,
            type = ChannelType.GROUP,
            name = "테스트 채널",
            creator = mockk<User>(relaxed = true),
            match = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val inviterMembership = ChannelMembers(
            id = UUID.randomUUID(),
            channel = channel,
            user = mockk<User>(relaxed = true),
            role = ChannelRole.ADMIN,
            isActive = true,
            joinedAt = LocalDateTime.now()
        )

        every { channelRepository.findById(testChannelId) } returns Optional.of(channel)
        every { userRepository.findById(newUserId) } returns Optional.of(user)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, inviterId) } returns inviterMembership
        every { channelMembersRepository.existsByChannelIdAndUserIdAndIsActiveTrue(testChannelId, newUserId) } returns false
        every { channelMembersRepository.save(any<ChannelMembers>()) } returns mockk<ChannelMembers>(relaxed = true)
        every { channelRepository.save(any()) } returns channel
        every { messagingTemplate.convertAndSend(any<String>(), any<Any>()) } just Runs

        // When: 테스트 대상 메서드 실행
        val result = channelService.addUserToChannel(testChannelId, newUserId, inviterId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("사용자가 성공적으로 채널에 추가되었습니다")
        verify(exactly = 1) { channelRepository.findById(testChannelId) }
        verify(exactly = 1) { userRepository.findById(newUserId) }
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, inviterId) }
        verify(exactly = 1) { channelMembersRepository.existsByChannelIdAndUserIdAndIsActiveTrue(testChannelId, newUserId) }
        verify(exactly = 1) { channelMembersRepository.save(any<ChannelMembers>()) }
        verify(exactly = 1) { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }
    }

    @Test
    fun `{given} 채널ID와_사용자ID일때 {when} 채널_멤버_제거하면 {then} 멤버가_제거되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val targetUserId = UUID.randomUUID()
        val removerId = testUserId
        val channel = Channel(
            id = testChannelId,
            type = ChannelType.GROUP,
            name = "테스트 채널",
            creator = mockk<User>(relaxed = true),
            match = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val removerMembership = ChannelMembers(
            id = UUID.randomUUID(),
            channel = channel,
            user = mockk<User>(relaxed = true),
            role = ChannelRole.ADMIN,
            isActive = true,
            joinedAt = LocalDateTime.now()
        )

        every { channelRepository.findById(testChannelId) } returns Optional.of(channel)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, removerId) } returns removerMembership
        every { channelMembersRepository.removeUserFromChannel(testChannelId, targetUserId, any()) } returns 1
        every { channelRepository.save(any()) } returns channel

        // When: 테스트 대상 메서드 실행
        val result = channelService.removeUserFromChannel(testChannelId, targetUserId, removerId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("사용자가 성공적으로 채널에서 제거되었습니다")
        verify(exactly = 1) { channelRepository.findById(testChannelId) }
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, removerId) }
        verify(exactly = 1) { channelMembersRepository.removeUserFromChannel(testChannelId, targetUserId, any()) }
        verify(exactly = 1) { channelRepository.save(any()) }
    }

    @Test
    fun `{given} 채널ID와_사용자ID일때 {when} 채널_나가기하면 {then} 멤버십이_비활성화되고_성공_응답을_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val channel = Channel(
            id = testChannelId,
            type = ChannelType.GROUP,
            name = "테스트 채널",
            creator = mockk<User>(relaxed = true),
            match = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { channelRepository.findById(testChannelId) } returns Optional.of(channel)
        every { channelMembersRepository.removeUserFromChannel(testChannelId, testUserId, any()) } returns 1
        every { channelRepository.save(any()) } returns channel

        // When: 테스트 대상 메서드 실행 (본인이 자신을 제거하는 경우)
        val result = channelService.removeUserFromChannel(testChannelId, testUserId, testUserId)

        // Then: 결과 검증
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("사용자가 성공적으로 채널에서 제거되었습니다")
        verify(exactly = 1) { channelRepository.findById(testChannelId) }
        verify(exactly = 1) { channelMembersRepository.removeUserFromChannel(testChannelId, testUserId, any()) }
        verify(exactly = 1) { channelRepository.save(any()) }
    }

    @Test
    fun `{given} 채널_멤버가_아닌_사용자일때 {when} 채널_멤버_추가하면 {then} 권한_없음_오류를_반환한다`() {
        // Given: 테스트 데이터 준비 및 Mock 설정
        val newUserId = UUID.randomUUID()
        val inviterId = testUserId
        val channel = Channel(
            id = testChannelId,
            type = ChannelType.GROUP,
            name = "테스트 채널",
            creator = mockk<User>(relaxed = true),
            match = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val newUser = User(
            id = newUserId,
            username = "newuser",
            email = "new@example.com",
            passwordHash = "hash",
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { channelRepository.findById(testChannelId) } returns Optional.of(channel)
        every { userRepository.findById(newUserId) } returns Optional.of(newUser)
        every { channelMembersRepository.findByChannelIdAndUserId(testChannelId, inviterId) } returns null

        // When: 테스트 대상 메서드 실행
        val result = channelService.addUserToChannel(testChannelId, newUserId, inviterId)

        // Then: 결과 검증
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("채널에 초대할 권한이 없습니다")
        verify(exactly = 1) { channelRepository.findById(testChannelId) }
        verify(exactly = 1) { userRepository.findById(newUserId) }
        verify(exactly = 1) { channelMembersRepository.findByChannelIdAndUserId(testChannelId, inviterId) }
        verify(exactly = 0) { channelMembersRepository.save(any<ChannelMembers>()) }
        verify(exactly = 0) { channelRepository.save(any()) }
    }
}

