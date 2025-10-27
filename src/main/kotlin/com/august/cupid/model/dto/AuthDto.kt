package com.august.cupid.model.dto

import java.time.LocalDateTime
import java.util.*

/**
 * 로그인 요청 DTO
 */
data class LoginRequest(
    val username: String,
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
    val currentPassword: String,
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
