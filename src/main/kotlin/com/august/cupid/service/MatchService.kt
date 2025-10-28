package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.Match
import com.august.cupid.model.entity.MatchStatus
import com.august.cupid.model.entity.ChannelMembers
import com.august.cupid.model.entity.ChannelRole
import com.august.cupid.repository.MatchRepository
import com.august.cupid.repository.UserRepository
import com.august.cupid.repository.ChannelRepository
import com.august.cupid.repository.ChannelMembersRepository
import com.august.cupid.repository.UserBlocksRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 매칭 서비스
 * 소개팅 앱의 매칭 관련 비즈니스 로직 처리
 */
@Service
@Transactional
class MatchService(
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val channelRepository: ChannelRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userBlocksRepository: UserBlocksRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 매칭 생성
     */
    fun createMatch(request: CreateMatchRequest): ApiResponse<MatchResponse> {
        return try {
            // 사용자 존재 확인
            val user1 = userRepository.findById(request.user1Id).orElse(null)
            val user2 = userRepository.findById(request.user2Id).orElse(null)
            
            if (user1 == null || user2 == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 자기 자신과 매칭 불가
            if (request.user1Id == request.user2Id) {
                return ApiResponse(false, message = "자기 자신과는 매칭할 수 없습니다")
            }

            // 차단 관계 확인
            if (userBlocksRepository.existsBlockBetweenUsers(request.user1Id, request.user2Id)) {
                return ApiResponse(false, message = "차단된 사용자와는 매칭할 수 없습니다")
            }

            // 이미 활성 매칭이 있는지 확인
            if (matchRepository.existsActiveMatchBetweenUsers(request.user1Id, request.user2Id)) {
                return ApiResponse(false, message = "이미 활성 매칭이 존재합니다")
            }

            // 매칭 생성
            val match = Match(
                user1 = user1,
                user2 = user2,
                status = MatchStatus.ACTIVE,
                matchedAt = LocalDateTime.now(),
                expiresAt = request.expiresAt
            )

            val savedMatch = matchRepository.save(match)

            logger.info("매칭 생성 완료: ${user1.username} <-> ${user2.username} (${savedMatch.id})")

            ApiResponse(true, data = savedMatch.toResponse(), message = "매칭이 성공적으로 생성되었습니다")
        } catch (e: Exception) {
            logger.error("매칭 생성 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 생성 중 오류가 발생했습니다")
        }
    }

    /**
     * 매칭 ID로 조회
     */
    @Transactional(readOnly = true)
    fun getMatchById(matchId: UUID): ApiResponse<MatchResponse> {
        return try {
            val match = matchRepository.findById(matchId).orElse(null)
            if (match == null) {
                ApiResponse(false, message = "매칭을 찾을 수 없습니다")
            } else {
                ApiResponse(true, data = match.toResponse())
            }
        } catch (e: Exception) {
            logger.error("매칭 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자의 매칭 목록 조회
     */
    @Transactional(readOnly = true)
    fun getUserMatches(userId: UUID, page: Int = 0, size: Int = 20): ApiResponse<List<MatchResponse>> {
        return try {
            val matches = matchRepository.findActiveMatchesByUserId(userId, MatchStatus.ACTIVE)
                .drop(page * size)
                .take(size)
                .map { it.toResponse() }

            ApiResponse(true, data = matches)
        } catch (e: Exception) {
            logger.error("사용자 매칭 목록 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 매칭 목록 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 두 사용자 간의 매칭 조회
     */
    @Transactional(readOnly = true)
    fun getMatchBetweenUsers(user1Id: UUID, user2Id: UUID): ApiResponse<MatchResponse?> {
        return try {
            val match = matchRepository.findMatchBetweenUsers(user1Id, user2Id)
            ApiResponse(true, data = match?.toResponse())
        } catch (e: Exception) {
            logger.error("사용자 간 매칭 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 간 매칭 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 매칭 종료
     */
    fun endMatch(matchId: UUID, userId: UUID): ApiResponse<String> {
        return try {
            val match = matchRepository.findById(matchId).orElse(null)
            if (match == null) {
                return ApiResponse(false, message = "매칭을 찾을 수 없습니다")
            }

            // 매칭 참여자 확인
            if (match.user1.id != userId && match.user2.id != userId) {
                return ApiResponse(false, message = "매칭을 종료할 권한이 없습니다")
            }

            // 이미 종료된 매칭
            if (match.status != MatchStatus.ACTIVE) {
                return ApiResponse(false, message = "이미 종료된 매칭입니다")
            }

            // 매칭 상태 업데이트
            matchRepository.updateMatchStatus(matchId, MatchStatus.ENDED)

            logger.info("매칭 종료 완료: ${match.id}")

            ApiResponse(true, message = "매칭이 성공적으로 종료되었습니다")
        } catch (e: Exception) {
            logger.error("매칭 종료 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 종료 중 오류가 발생했습니다")
        }
    }

    /**
     * 매칭 거부
     */
    fun rejectMatch(matchId: UUID, userId: UUID): ApiResponse<String> {
        return try {
            val match = matchRepository.findById(matchId).orElse(null)
            if (match == null) {
                return ApiResponse(false, message = "매칭을 찾을 수 없습니다")
            }

            // 매칭 참여자 확인
            if (match.user1.id != userId && match.user2.id != userId) {
                return ApiResponse(false, message = "매칭을 거부할 권한이 없습니다")
            }

            // 이미 처리된 매칭
            if (match.status != MatchStatus.ACTIVE) {
                return ApiResponse(false, message = "이미 처리된 매칭입니다")
            }

            // 매칭 상태 업데이트
            matchRepository.updateMatchStatus(matchId, MatchStatus.REJECTED)

            logger.info("매칭 거부 완료: ${match.id}")

            ApiResponse(true, message = "매칭이 성공적으로 거부되었습니다")
        } catch (e: Exception) {
            logger.error("매칭 거부 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 거부 중 오류가 발생했습니다")
        }
    }

    /**
     * 매칭 수락 및 채널 생성
     */
    fun acceptMatch(matchId: UUID, userId: UUID): ApiResponse<ChannelResponse> {
        return try {
            val match = matchRepository.findById(matchId).orElse(null)
            if (match == null) {
                return ApiResponse(false, message = "매칭을 찾을 수 없습니다")
            }

            // 매칭 참여자 확인
            if (match.user1.id != userId && match.user2.id != userId) {
                return ApiResponse(false, message = "매칭을 수락할 권한이 없습니다")
            }

            // 이미 처리된 매칭
            if (match.status != MatchStatus.ACTIVE) {
                return ApiResponse(false, message = "이미 처리된 매칭입니다")
            }

            // 매칭 상태 업데이트
            matchRepository.updateMatchStatus(matchId, MatchStatus.ACCEPTED)

            // 1:1 채널 생성
            val channelRequest = CreateChannelRequest(
                type = "DIRECT",
                matchId = matchId
            )

            val channelResult = createChannelForMatch(channelRequest, match)
            if (!channelResult.success) {
                // 채널 생성 실패 시 매칭 상태 롤백
                matchRepository.updateMatchStatus(matchId, MatchStatus.ACTIVE)
                return ApiResponse(false, message = "채널 생성에 실패했습니다")
            }

            logger.info("매칭 수락 및 채널 생성 완료: ${match.id} -> 채널 ${channelResult.data?.id}")

            ApiResponse(true, data = channelResult.data, message = "매칭이 수락되고 채널이 생성되었습니다")
        } catch (e: Exception) {
            logger.error("매칭 수락 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 수락 중 오류가 발생했습니다")
        }
    }

    /**
     * 매칭을 위한 채널 생성 (내부 메서드)
     */
    private fun createChannelForMatch(request: CreateChannelRequest, match: Match): ApiResponse<ChannelResponse> {
        return try {
            val channel = com.august.cupid.model.entity.Channel(
                name = null, // 1:1 채널은 이름 없음
                type = com.august.cupid.model.entity.ChannelType.valueOf(request.type.uppercase()),
                creator = match.user1,
                match = match
            )

            val savedChannel = channelRepository.save(channel)

            // 두 사용자를 모두 채널 멤버로 추가
            val member1 = ChannelMembers(
                channel = savedChannel,
                user = match.user1,
                role = com.august.cupid.model.entity.ChannelRole.MEMBER,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )

            val member2 = ChannelMembers(
                channel = savedChannel,
                user = match.user2,
                role = com.august.cupid.model.entity.ChannelRole.MEMBER,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )

            channelMembersRepository.save(member1)
            channelMembersRepository.save(member2)

            ApiResponse(true, data = savedChannel.toResponse())
        } catch (e: Exception) {
            logger.error("매칭 채널 생성 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 채널 생성 중 오류가 발생했습니다")
        }
    }

    /**
     * 만료된 매칭들 정리
     */
    fun cleanupExpiredMatches(): ApiResponse<Int> {
        return try {
            val expiredCount = matchRepository.expireMatches(LocalDateTime.now())
            logger.info("만료된 매칭 정리 완료: $expiredCount 개")
            ApiResponse(true, data = expiredCount, message = "$expiredCount 개의 만료된 매칭이 정리되었습니다")
        } catch (e: Exception) {
            logger.error("만료된 매칭 정리 실패: ${e.message}", e)
            ApiResponse(false, error = "만료된 매칭 정리 중 오류가 발생했습니다")
        }
    }

    /**
     * 매칭 통계 조회
     */
    @Transactional(readOnly = true)
    fun getMatchStatistics(): ApiResponse<Map<String, Any>> {
        return try {
            val totalMatches = matchRepository.countMatchesByStatus(MatchStatus.ACTIVE)
            val acceptedMatches = matchRepository.countMatchesByStatus(MatchStatus.ACCEPTED)
            val rejectedMatches = matchRepository.countMatchesByStatus(MatchStatus.REJECTED)
            val endedMatches = matchRepository.countMatchesByStatus(MatchStatus.ENDED)
            val expiredMatches = matchRepository.countMatchesByStatus(MatchStatus.EXPIRED)

            val todayMatches = matchRepository.countMatchesSince(LocalDateTime.now().minusDays(1))
            val weekMatches = matchRepository.countMatchesSince(LocalDateTime.now().minusWeeks(1))
            val monthMatches = matchRepository.countMatchesSince(LocalDateTime.now().minusMonths(1))

            val statistics = mapOf(
                "totalActiveMatches" to totalMatches,
                "acceptedMatches" to acceptedMatches,
                "rejectedMatches" to rejectedMatches,
                "endedMatches" to endedMatches,
                "expiredMatches" to expiredMatches,
                "todayMatches" to todayMatches,
                "weekMatches" to weekMatches,
                "monthMatches" to monthMatches
            )

            ApiResponse(true, data = statistics)
        } catch (e: Exception) {
            logger.error("매칭 통계 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "매칭 통계 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * Match 엔티티를 MatchResponse DTO로 변환
     */
    private fun Match.toResponse(): MatchResponse {
        return MatchResponse(
            id = this.id,
            user1Id = this.user1.id,
            user2Id = this.user2.id,
            status = this.status.name,
            matchedAt = this.matchedAt,
            expiresAt = this.expiresAt
        )
    }

    /**
     * Channel 엔티티를 ChannelResponse DTO로 변환 (확장 함수)
     */
    private fun com.august.cupid.model.entity.Channel.toResponse(): ChannelResponse {
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
}
