package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.User
import com.august.cupid.repository.UserRepository
import com.august.cupid.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 인증 서비스
 * 로그인, 토큰 관리, 비밀번호 변경 등 인증 관련 비즈니스 로직 처리
 */
@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인
     */
    fun login(request: LoginRequest): ApiResponse<LoginResponse> {
        return try {
            // 사용자 조회
            val user = userRepository.findByUsernameAndIsActiveTrue(request.username)
            if (user == null) {
                return ApiResponse(false, message = "사용자명 또는 비밀번호가 올바르지 않습니다")
            }

            // 비밀번호 확인
            if (!passwordEncoder.matches(request.password, user.passwordHash)) {
                return ApiResponse(false, message = "사용자명 또는 비밀번호가 올바르지 않습니다")
            }

            // 토큰 생성
            val accessToken = jwtUtil.generateAccessToken(user.id, user.username, user.email)
            val refreshToken = jwtUtil.generateRefreshToken(user.id)
            val expiresIn = jwtUtil.getExpirationTime(accessToken) ?: 86400000L

            // 마지막 접속 시간 업데이트
            userRepository.save(user.copy(lastSeenAt = LocalDateTime.now()))

            logger.info("로그인 성공: ${user.username} (${user.id})")

            val loginResponse = LoginResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
                user = user.toResponse()
            )

            ApiResponse(true, data = loginResponse, message = "로그인이 성공적으로 완료되었습니다")
        } catch (e: Exception) {
            logger.error("로그인 실패: ${e.message}", e)
            ApiResponse(false, error = "로그인 중 오류가 발생했습니다")
        }
    }

    /**
     * 토큰 갱신
     */
    fun refreshToken(request: RefreshTokenRequest): ApiResponse<RefreshTokenResponse> {
        return try {
            // Refresh Token 유효성 검증
            if (!jwtUtil.validateRefreshToken(request.refreshToken)) {
                return ApiResponse(false, message = "유효하지 않은 Refresh Token입니다")
            }

            // 사용자 ID 추출
            val userId = jwtUtil.getUserIdFromToken(request.refreshToken)
            if (userId == null) {
                return ApiResponse(false, message = "토큰에서 사용자 정보를 추출할 수 없습니다")
            }

            // 사용자 존재 확인
            val user = userRepository.findById(userId).orElse(null)
            if (user == null || !user.isActive) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 새 토큰 생성
            val newAccessToken = jwtUtil.generateAccessToken(user.id, user.username, user.email)
            val newRefreshToken = jwtUtil.generateRefreshToken(user.id)
            val expiresIn = jwtUtil.getExpirationTime(newAccessToken) ?: 86400000L

            logger.info("토큰 갱신 성공: ${user.username} (${user.id})")

            val refreshResponse = RefreshTokenResponse(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresIn = expiresIn
            )

            ApiResponse(true, data = refreshResponse, message = "토큰이 성공적으로 갱신되었습니다")
        } catch (e: Exception) {
            logger.error("토큰 갱신 실패: ${e.message}", e)
            ApiResponse(false, error = "토큰 갱신 중 오류가 발생했습니다")
        }
    }

    /**
     * 토큰 검증
     */
    @Transactional(readOnly = true)
    fun validateToken(token: String): ApiResponse<TokenInfo> {
        return try {
            // 토큰 유효성 검증
            if (!jwtUtil.validateAccessToken(token)) {
                return ApiResponse(false, message = "유효하지 않은 토큰입니다")
            }

            // 토큰 정보 추출
            val tokenInfo = jwtUtil.getTokenInfo(token)
            if (tokenInfo == null) {
                return ApiResponse(false, message = "토큰 정보를 추출할 수 없습니다")
            }

            // 사용자 존재 확인
            val user = userRepository.findById(tokenInfo.userId).orElse(null)
            if (user == null || !user.isActive) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            ApiResponse(true, data = tokenInfo)
        } catch (e: Exception) {
            logger.error("토큰 검증 실패: ${e.message}", e)
            ApiResponse(false, error = "토큰 검증 중 오류가 발생했습니다")
        }
    }

    /**
     * 비밀번호 변경
     */
    fun changePassword(userId: UUID, request: ChangePasswordRequest): ApiResponse<String> {
        return try {
            // 사용자 조회
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            // 현재 비밀번호 확인
            if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
                return ApiResponse(false, message = "현재 비밀번호가 올바르지 않습니다")
            }

            // 새 비밀번호 암호화
            val newPasswordHash = passwordEncoder.encode(request.newPassword)

            // 비밀번호 업데이트
            val updatedUser = user.copy(passwordHash = newPasswordHash)
            userRepository.save(updatedUser)

            logger.info("비밀번호 변경 완료: ${user.username} (${user.id})")

            ApiResponse(true, message = "비밀번호가 성공적으로 변경되었습니다")
        } catch (e: Exception) {
            logger.error("비밀번호 변경 실패: ${e.message}", e)
            ApiResponse(false, error = "비밀번호 변경 중 오류가 발생했습니다")
        }
    }

    /**
     * 로그아웃 (클라이언트 측에서 토큰 삭제)
     */
    fun logout(userId: UUID): ApiResponse<String> {
        return try {
            // 사용자 존재 확인
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            logger.info("로그아웃 완료: ${user.username} (${user.id})")

            ApiResponse(true, message = "로그아웃이 성공적으로 완료되었습니다")
        } catch (e: Exception) {
            logger.error("로그아웃 실패: ${e.message}", e)
            ApiResponse(false, error = "로그아웃 중 오류가 발생했습니다")
        }
    }

    /**
     * 토큰에서 사용자 정보 추출
     */
    @Transactional(readOnly = true)
    fun getUserFromToken(token: String): ApiResponse<UserResponse> {
        return try {
            // 토큰 검증
            val tokenValidation = validateToken(token)
            if (!tokenValidation.success) {
                return ApiResponse(false, message = tokenValidation.message)
            }

            val tokenInfo = tokenValidation.data!!
            val user = userRepository.findById(tokenInfo.userId).orElse(null)
            if (user == null) {
                return ApiResponse(false, message = "사용자를 찾을 수 없습니다")
            }

            ApiResponse(true, data = user.toResponse())
        } catch (e: Exception) {
            logger.error("토큰에서 사용자 정보 추출 실패: ${e.message}", e)
            ApiResponse(false, error = "사용자 정보 추출 중 오류가 발생했습니다")
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
