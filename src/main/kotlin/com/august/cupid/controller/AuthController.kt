package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.AuthService
import com.august.cupid.service.UserService
import com.august.cupid.util.JwtUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 인증 컨트롤러
 * 로그인, 토큰 관리, 비밀번호 변경 등 인증 관련 API
 */
@Tag(name = "Authentication", description = "인증 API - 로그인/회원가입/토큰 관리 및 프로필 관리")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val jwtUtil: JwtUtil
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인
     */
    @Operation(summary = "로그인", description = "사용자 로그인 및 JWT 토큰 발급")
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        logger.info("로그인 시도: ${request.username}")
        
        val result = authService.login(request)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 회원가입
     */
    @Operation(summary = "회원가입", description = "새 사용자 등록")
    @PostMapping("/register")
    fun register(@RequestBody request: CreateUserRequest): ResponseEntity<ApiResponse<UserResponse>> {
        logger.info("회원가입 시도: ${request.username}")
        
        val result = userService.createUser(request)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 토큰 갱신
     */
    @Operation(summary = "토큰 갱신", description = "Access Token을 Refresh Token으로 갱신")
    @PostMapping("/refresh")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ResponseEntity<ApiResponse<RefreshTokenResponse>> {
        logger.info("토큰 갱신 시도")
        
        val result = authService.refreshToken(request)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 토큰 검증
     */
    @Operation(summary = "토큰 검증", description = "JWT 토큰의 유효성을 검증합니다")
    @PostMapping("/validate")
    fun validateToken(@RequestHeader("Authorization") authHeader: String): ResponseEntity<ApiResponse<TokenInfo>> {
        val token = jwtUtil.removeBearerPrefix(authHeader)
        logger.info("토큰 검증 시도")
        
        val result = authService.validateToken(token)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 현재 사용자 정보 조회
     */
    @Operation(summary = "현재 사용자 조회", description = "로그인한 사용자의 정보를 조회합니다")
    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication): ResponseEntity<ApiResponse<UserResponse>> {
        val userId = UUID.fromString(authentication.name)
        logger.info("현재 사용자 정보 조회: $userId")
        
        val result = userService.getUserById(userId)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 비밀번호 변경
     */
    @Operation(summary = "비밀번호 변경", description = "사용자 비밀번호를 변경합니다")
    @PostMapping("/change-password")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<String>> {
        val userId = UUID.fromString(authentication.name)
        logger.info("비밀번호 변경 시도: $userId")
        
        val result = authService.changePassword(userId, request)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 로그아웃
     */
    @Operation(summary = "로그아웃", description = "사용자 로그아웃 처리")
    @PostMapping("/logout")
    fun logout(authentication: Authentication): ResponseEntity<ApiResponse<String>> {
        val userId = UUID.fromString(authentication.name)
        logger.info("로그아웃: $userId")
        
        val result = authService.logout(userId)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 사용자 정보 업데이트
     */
    @Operation(summary = "프로필 업데이트", description = "사용자 프로필 정보를 업데이트합니다")
    @PutMapping("/profile")
    fun updateProfile(
        @RequestBody request: UpdateUserRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val userId = UUID.fromString(authentication.name)
        logger.info("프로필 업데이트 시도: $userId")
        
        val result = userService.updateUser(userId, request)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * 마지막 접속 시간 업데이트
     */
    @Operation(summary = "접속 시간 업데이트", description = "사용자의 마지막 접속 시간을 업데이트합니다")
    @PostMapping("/ping")
    fun ping(authentication: Authentication): ResponseEntity<ApiResponse<String>> {
        val userId = UUID.fromString(authentication.name)
        
        val result = userService.updateLastSeenAt(userId)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }
}
