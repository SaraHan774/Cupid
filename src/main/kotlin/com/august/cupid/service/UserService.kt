package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.User
import com.august.cupid.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 서비스
 * 사용자 관련 비즈니스 로직 처리
 */
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자 생성
     */
    fun createUser(request: CreateUserRequest): ApiResponse<UserResponse> {
        return try {
            logger.info("사용자 생성 시작: ${request.username}")
            
            // 중복 확인
            if (userRepository.existsByUsername(request.username)) {
                logger.warn("사용자명 중복: ${request.username}")
                return ApiResponse(false, message = "사용자명이 이미 존재합니다")
            }
            if (userRepository.existsByEmail(request.email)) {
                logger.warn("이메일 중복: ${request.email}")
                return ApiResponse(false, message = "이메일이 이미 존재합니다")
            }

            // 비밀번호 암호화
            logger.info("비밀번호 암호화 시작")
            val encodedPassword = passwordEncoder.encode(request.password)
            logger.info("비밀번호 암호화 완료")

            // 사용자 생성 (공식 문서 권장사항 - 검증 메서드 사용)
            logger.info("User 객체 생성 시작")
            val user = User(
                id = UUID.randomUUID(),
                username = request.username,
                email = request.email,
                passwordHash = encodedPassword,
                profileImageUrl = request.profileImageUrl,
                isActive = true,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ).validate() // 공식 문서 권장: 별도 검증 메서드 사용
            logger.info("User 객체 생성 완료: ${user.id}")

            logger.info("데이터베이스 저장 시작")
            val savedUser = userRepository.save(user)
            logger.info("사용자 생성 완료: ${savedUser.username} (${savedUser.id})")

            ApiResponse(true, data = savedUser.toResponse(), message = "사용자가 성공적으로 생성되었습니다")
        } catch (e: Exception) {
            logger.error("사용자 생성 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 생성 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자 ID로 조회
     */
    @Transactional(readOnly = true)
    fun getUserById(userId: UUID): ApiResponse<UserResponse> {
        return try {
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            } else {
                ApiResponse(true, data = user.toResponse())
            }
        } catch (e: Exception) {
            logger.error("사용자 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자명으로 조회
     */
    @Transactional(readOnly = true)
    fun getUserByUsername(username: String): ApiResponse<UserResponse> {
        return try {
            val user = userRepository.findByUsernameAndIsActiveTrue(username)
            if (user == null) {
                ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            } else {
                ApiResponse(true, data = user.toResponse())
            }
        } catch (e: Exception) {
            logger.error("사용자 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자 정보 업데이트
     */
    fun updateUser(userId: UUID, request: UpdateUserRequest): ApiResponse<UserResponse> {
        return try {
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 사용자명 중복 확인
            if (request.username != null && request.username != user.username) {
                if (userRepository.existsByUsername(request.username)) {
                    return ApiResponse(false, message = "사용자명이 이미 존재합니다")
                }
            }

            // 이메일 중복 확인
            if (request.email != null && request.email != user.email) {
                if (userRepository.existsByEmail(request.email)) {
                    return ApiResponse(false, message = "이메일이 이미 존재합니다")
                }
            }

            // 불변 data class의 copy 메서드를 사용하여 새 인스턴스 생성
            val updatedUser = user.copy(
                username = request.username ?: user.username,
                email = request.email ?: user.email,
                profileImageUrl = request.profileImageUrl ?: user.profileImageUrl,
                updatedAt = LocalDateTime.now()
            )
            
            val savedUser = userRepository.save(updatedUser)
            logger.info("사용자 정보 업데이트 완료: ${savedUser.username} (${savedUser.id})")

            ApiResponse(true, data = savedUser.toResponse(), message = "사용자 정보가 성공적으로 업데이트되었습니다")
        } catch (e: Exception) {
            logger.error("사용자 업데이트 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 업데이트 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자 비활성화
     */
    fun deactivateUser(userId: UUID): ApiResponse<String> {
        return try {
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 불변 data class의 copy 메서드를 사용하여 새 인스턴스 생성
            val deactivatedUser = user.copy(
                isActive = false,
                updatedAt = LocalDateTime.now()
            )
            userRepository.save(deactivatedUser)

            logger.info("사용자 비활성화 완료: ${user.username} (${user.id})")
            ApiResponse(true, message = "사용자가 성공적으로 비활성화되었습니다")
        } catch (e: Exception) {
            logger.error("사용자 비활성화 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 비활성화 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자 검색
     */
    @Transactional(readOnly = true)
    fun searchUsers(username: String, page: Int = 0, size: Int = 20): ApiResponse<List<UserResponse>> {
        return try {
            val users = userRepository.findByUsernameContaining(username)
                .drop(page * size)
                .take(size)
                .map { it.toResponse() }

            ApiResponse(true, data = users)
        } catch (e: Exception) {
            logger.error("사용자 검색 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 검색 중 오류가 발생했습니다")
        }
    }

    /**
     * 최근 접속 사용자 조회
     */
    @Transactional(readOnly = true)
    fun getRecentlyActiveUsers(limit: Int = 10): ApiResponse<List<UserResponse>> {
        return try {
            val users = userRepository.findRecentlyActiveUsers()
                .take(limit)
                .map { it.toResponse() }

            ApiResponse(true, data = users)
        } catch (e: Exception) {
            logger.error("최근 접속 사용자 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "최근 접속 사용자 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자 통계 조회
     */
    @Transactional(readOnly = true)
    fun getUserStatistics(): ApiResponse<Map<String, Any>> {
        return try {
            val totalUsers = userRepository.countActiveUsers()
            val newUsersToday = userRepository.countNewUsersSince(LocalDateTime.now().minusDays(1))
            val newUsersThisWeek = userRepository.countNewUsersSince(LocalDateTime.now().minusWeeks(1))
            val newUsersThisMonth = userRepository.countNewUsersSince(LocalDateTime.now().minusMonths(1))

            val statistics = mapOf(
                "totalUsers" to totalUsers,
                "newUsersToday" to newUsersToday,
                "newUsersThisWeek" to newUsersThisWeek,
                "newUsersThisMonth" to newUsersThisMonth
            )

            ApiResponse(true, data = statistics)
        } catch (e: Exception) {
            logger.error("사용자 통계 조회 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 통계 조회 중 오류가 발생했습니다")
        }
    }

    /**
     * 사용자 마지막 접속 시간 업데이트
     */
    fun updateLastSeenAt(userId: UUID): ApiResponse<String> {
        return try {
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 불변 data class의 copy 메서드를 사용하여 새 인스턴스 생성
            val updatedUser = user.copy(
                lastSeenAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            userRepository.save(updatedUser)

            ApiResponse(true, message = "마지막 접속 시간이 업데이트되었습니다")
        } catch (e: Exception) {
            logger.error("마지막 접속 시간 업데이트 실패: ${e.message}", e)
            ApiResponse(false, error = "마지막 접속 시간 업데이트 중 오류가 발생했습니다")
        }
    }

    /**
     * 프로필 이미지 정보 업데이트
     *
     * ProfileImageService에서 호출하여 사용자의 프로필 이미지 정보를 업데이트합니다.
     */
    fun updateProfileImage(
        userId: UUID,
        profileImageUrl: String?,
        profileThumbnailUrl: String?,
        profileImageBlurhash: String?,
        profileImageMetadata: Map<String, Any>?
    ): ApiResponse<UserResponse> {
        return try {
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            val updatedUser = user.copy(
                profileImageUrl = profileImageUrl,
                profileThumbnailUrl = profileThumbnailUrl,
                profileImageBlurhash = profileImageBlurhash,
                profileImageMetadata = profileImageMetadata,
                updatedAt = LocalDateTime.now()
            )

            val savedUser = userRepository.save(updatedUser)
            logger.info("프로필 이미지 정보 업데이트 완료: ${savedUser.username} (${savedUser.id})")

            ApiResponse(true, data = savedUser.toResponse(), message = "프로필 이미지가 업데이트되었습니다")
        } catch (e: Exception) {
            logger.error("프로필 이미지 업데이트 실패: ${e.message}", e)
            ApiResponse(false, error = "프로필 이미지 업데이트 중 오류가 발생했습니다")
        }
    }

    /**
     * User 엔티티를 UserResponse DTO로 변환
     */
    private fun User.toResponse(): UserResponse {
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
