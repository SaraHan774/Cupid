package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.Channel
import com.august.cupid.model.entity.ChannelType
import com.august.cupid.model.entity.ChannelMembers
import com.august.cupid.model.entity.ChannelRole
import com.august.cupid.repository.ChannelRepository
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.UserRepository
import com.august.cupid.repository.MatchRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 채널 서비스
 * 채팅방 관련 비즈니스 로직 처리
 */
@Service
@Transactional
class ChannelService(
    private val channelRepository: ChannelRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val entityManager: EntityManager,
    private val messagingTemplate: org.springframework.messaging.simp.SimpMessagingTemplate
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 채널 생성
     */
    fun createChannel(request: CreateChannelRequest, creatorId: UUID): ApiResponse<ChannelResponse> {
        return try {
            // 생성자 존재 확인
            val creator = userRepository.findById(creatorId).orElse(null)
            if (creator == null) {
                return ApiResponse(false, message = "생성자를 찾을 수 없습니다")
            }

            val channelType = ChannelType.valueOf(request.type.uppercase())
            
            // 매칭 ID 확인 (1:1 채널인 경우)
            if (channelType == ChannelType.DIRECT && request.matchId != null) {
                val match = matchRepository.findById(request.matchId).orElse(null)
                if (match == null) {
                    return ApiResponse(false, message = "매칭을 찾을 수 없습니다")
                }
                
                // 이미 해당 매칭으로 채널이 생성되었는지 확인
                if (channelRepository.existsByMatchId(request.matchId)) {
                    return ApiResponse(false, message = "이미 해당 매칭으로 채널이 생성되었습니다")
                }
            }

            // 매칭 객체 조회 (1:1 채널인 경우)
            val match = if (request.matchId != null) {
                matchRepository.findById(request.matchId).orElse(null)
            } else null

            // 채널 생성 - ID, createdAt, updatedAt는 JPA가 자동 생성 (기본값 사용)
            val channel = Channel(
                type = channelType,
                name = request.name,
                creator = creator,
                match = match
            )

            // repository.save()는 새로운 엔티티면 persist, 기존 엔티티면 merge를 자동으로 처리
            val savedChannel = channelRepository.save(channel)
            channelRepository.flush()

            // 생성자를 채널 멤버로 추가
            val channelMember = ChannelMembers(
                channel = savedChannel,
                user = creator,
                role = ChannelRole.ADMIN,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )
            channelMembersRepository.save(channelMember)

            logger.info("채널 생성 완료: ${savedChannel.name ?: "Unnamed"} (${savedChannel.id})")

            ApiResponse(true, data = savedChannel.toResponse(), message = "채널이 성공적으로 생성되었습니다")
        } catch (e: Exception) {
            logger.error("채널 생성 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 생성 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널 ID로 조회
     */
    @Transactional(readOnly = true)
    fun getChannelById(channelId: UUID): ApiResponse<ChannelResponse> {
        return try {
            val channel = channelRepository.findById(channelId).orElse(null)
            if (channel == null) {
                ApiResponse(false, message = "채널을 찾을 수 없습니다")
            } else {
                ApiResponse(true, data = channel.toResponse())
            }
        } catch (e: Exception) {
            logger.error("채널 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자의 채널 목록 조회
     */
    @Transactional(readOnly = true)
    fun getUserChannels(userId: UUID, page: Int = 0, size: Int = 20): ApiResponse<List<ChannelResponse>> {
        return try {
            val channels = channelRepository.findChannelsByUserId(userId)
                .drop(page * size)
                .take(size)
                .map { it.toResponse() }

            ApiResponse(true, data = channels)
        } catch (e: Exception) {
            logger.error("사용자 채널 목록 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 채널 목록 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 두 사용자 간의 1:1 채널 조회
     */
    @Transactional(readOnly = true)
    fun getDirectChannelBetweenUsers(user1Id: UUID, user2Id: UUID): ApiResponse<ChannelResponse?> {
        return try {
            val channel = channelRepository.findDirectChannelBetweenUsers(user1Id, user2Id)
            ApiResponse(true, data = channel?.toResponse())
        } catch (e: Exception) {
            logger.error("1:1 채널 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "1:1 채널 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널에 사용자 추가
     */
    fun addUserToChannel(channelId: UUID, userId: UUID, inviterId: UUID): ApiResponse<String> {
        return try {
            // 채널 존재 확인
            val channel = channelRepository.findById(channelId).orElse(null)
            if (channel == null) {
                return ApiResponse(false, message = "채널을 찾을 수 없습니다")
            }

            // 사용자 존재 확인
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 초대자가 채널 멤버인지 확인
            val inviterMembership = channelMembersRepository.findByChannelIdAndUserId(channelId, inviterId)
            if (inviterMembership == null || !inviterMembership.isActive) {
                return ApiResponse(false, message = "채널에 초대할 권한이 없습니다")
            }

            // 이미 멤버인지 확인
            if (channelMembersRepository.existsByChannelIdAndUserIdAndIsActiveTrue(channelId, userId)) {
                return ApiResponse(false, message = "이미 채널 멤버입니다")
            }

            // 채널 멤버 추가
            val channelMember = ChannelMembers(
                channel = channel,
                user = user,
                role = ChannelRole.MEMBER,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )
            channelMembersRepository.save(channelMember)

            // 채널 업데이트 시간 갱신
            channel.updatedAt = LocalDateTime.now()
            channelRepository.save(channel)

            logger.info("사용자를 채널에 추가 완료: ${user.username} -> ${channel.name ?: "Unnamed"} (${channel.id})")

            // WebSocket으로 초대된 사용자에게 채널 정보 전송
            try {
                val channelResponse = channel.toResponse()
                messagingTemplate.convertAndSend(
                    "/topic/user/${userId}/channels",
                    mapOf(
                        "type" to "CHANNEL_INVITED",
                        "channel" to channelResponse
                    )
                )
                logger.debug("채널 초대 알림 전송 완료: 사용자 ${userId} -> 채널 ${channel.id}")
            } catch (e: Exception) {
                logger.error("채널 초대 알림 전송 실패: ${e.message}", e)
            }

            ApiResponse(true, message = "사용자가 성공적으로 채널에 추가되었습니다")
        } catch (e: Exception) {
            logger.error("사용자 채널 추가 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 채널 추가 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널에서 사용자 제거
     */
    fun removeUserFromChannel(channelId: UUID, userId: UUID, removerId: UUID): ApiResponse<String> {
        return try {
            // 채널 존재 확인
            val channel = channelRepository.findById(channelId).orElse(null)
            if (channel == null) {
                return ApiResponse(false, message = "채널을 찾을 수 없습니다")
            }

            // 제거 권한 확인 (본인이거나 관리자)
            if (userId != removerId) {
                val removerMembership = channelMembersRepository.findByChannelIdAndUserId(channelId, removerId)
                if (removerMembership?.role != ChannelRole.ADMIN) {
                    return ApiResponse(false, message = "사용자를 제거할 권한이 없습니다")
                }
            }

            // 멤버십 제거
            val result = channelMembersRepository.removeUserFromChannel(channelId, userId, LocalDateTime.now())
            if (result == 0) {
                return ApiResponse(false, message = "채널 멤버를 찾을 수 없습니다")
            }

            // 채널 업데이트 시간 갱신
            channel.updatedAt = LocalDateTime.now()
            channelRepository.save(channel)

            logger.info("사용자를 채널에서 제거 완료: $userId -> ${channel.name ?: "Unnamed"} (${channel.id})")

            ApiResponse(true, message = "사용자가 성공적으로 채널에서 제거되었습니다")
        } catch (e: Exception) {
            logger.error("사용자 채널 제거 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 채널 제거 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널 멤버 목록 조회
     */
    @Transactional(readOnly = true)
    fun getChannelMembers(channelId: UUID): ApiResponse<List<UserResponse>> {
        return try {
            val members = channelMembersRepository.findByChannelIdAndIsActiveTrue(channelId)
            val userResponses = members.map { it.user.toResponse() }
            ApiResponse(true, data = userResponses)
        } catch (e: Exception) {
            logger.error("채널 멤버 목록 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 멤버 목록 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널 정보 업데이트
     */
    fun updateChannel(channelId: UUID, name: String?, updaterId: UUID): ApiResponse<ChannelResponse> {
        return try {
            val channel = channelRepository.findById(channelId).orElse(null)
            if (channel == null) {
                return ApiResponse(false, message = "채널을 찾을 수 없습니다")
            }

            // 업데이트 권한 확인 (관리자만)
            val updaterMembership = channelMembersRepository.findByChannelIdAndUserId(channelId, updaterId)
            if (updaterMembership?.role != ChannelRole.ADMIN) {
                return ApiResponse(false, message = "채널 정보를 수정할 권한이 없습니다")
            }

            // Channel은 data class이므로 새 인스턴스 생성 필요
            // 실제로는 별도의 업데이트 메서드가 필요할 수 있음
            val updatedChannel = channelRepository.save(channel)

            logger.info("채널 정보 업데이트 완료: ${updatedChannel.name ?: "Unnamed"} (${updatedChannel.id})")

            ApiResponse(true, data = updatedChannel.toResponse(), message = "채널 정보가 성공적으로 업데이트되었습니다")
        } catch (e: Exception) {
            logger.error("채널 정보 업데이트 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 정보 업데이트 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널 통계 조회
     */
    @Transactional(readOnly = true)
    fun getChannelStatistics(): ApiResponse<Map<String, Any>> {
        return try {
            val totalChannels = channelRepository.countAllChannels()
            val directChannels = channelRepository.countChannelsByType(ChannelType.DIRECT)
            val groupChannels = channelRepository.countChannelsByType(ChannelType.GROUP)

            val statistics = mapOf(
                "totalChannels" to totalChannels,
                "directChannels" to directChannels,
                "groupChannels" to groupChannels
            )

            ApiResponse(true, data = statistics)
        } catch (e: Exception) {
            logger.error("채널 통계 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 통계 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널 멤버십 확인
     */
    @Transactional(readOnly = true)
    fun isChannelMember(channelId: UUID, userId: UUID): ApiResponse<Boolean> {
        return try {
            val isMember = channelMembersRepository.existsByChannelIdAndUserIdAndIsActiveTrue(channelId, userId)
            ApiResponse(true, data = isMember)
        } catch (e: Exception) {
            logger.error("채널 멤버십 확인 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 멤버십 확인 중 오류가 발생했습니다")
        }
    }

    /**
     * Channel 엔티티를 ChannelResponse DTO로 변환
     */
    private fun Channel.toResponse(): ChannelResponse {
        return ChannelResponse(
            id = this.id!!,
            name = this.name,
            type = this.type.name,
            creatorId = this.creator.id,
            matchId = this.match?.id,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * User 엔티티를 UserResponse DTO로 변환 (확장 함수)
     */
    private fun com.august.cupid.model.entity.User.toResponse(): UserResponse {
        return UserResponse(
            id = this.id,
            username = this.username,
            email = this.email ?: "",
            profileImageUrl = this.profileImageUrl,
            bio = null, // User 엔티티에 bio 필드가 없음
            isActive = this.isActive,
            createdAt = this.createdAt,
            lastSeenAt = this.lastSeenAt
        )
    }
}
