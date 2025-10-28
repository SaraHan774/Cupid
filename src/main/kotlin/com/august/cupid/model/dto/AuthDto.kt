package com.august.cupid.model.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.*

/**
 * 로그인 요청 DTO
 */
data class LoginRequest(
    @field:NotBlank(message = "사용자명은 필수입니다")
    @field:Size(min = 3, max = 50, message = "사용자명은 3-50자 사이여야 합니다")
    val username: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다")
    val password: String
)

/**
 * 로그인 응답 DTO
 */
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserResponse
)

/**
 * 토큰 갱신 요청 DTO
 */
data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh Token은 필수입니다")
    val refreshToken: String
)

/**
 * 토큰 갱신 응답 DTO
 */
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long
)

/**
 * 비밀번호 변경 요청 DTO
 */
data class ChangePasswordRequest(
    @field:NotBlank(message = "현재 비밀번호는 필수입니다")
    val currentPassword: String,
    
    @field:NotBlank(message = "새 비밀번호는 필수입니다")
    @field:Size(min = 8, max = 100, message = "비밀번호는 8-100자 사이여야 합니다")
    val newPassword: String
)

/**
 * 비밀번호 재설정 요청 DTO
 */
data class ResetPasswordRequest(
    val email: String
)

/**
 * 비밀번호 재설정 확인 DTO
 */
data class ConfirmResetPasswordRequest(
    val token: String,
    val newPassword: String
)

/**
 * JWT 토큰 정보 DTO
 */
data class TokenInfo(
    val userId: UUID,
    val username: String,
    val email: String?,
    val issuedAt: Long,
    val expiresAt: Long,
    val tokenType: String
)
