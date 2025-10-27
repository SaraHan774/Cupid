package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.AuthService
import com.august.cupid.service.UserService
import com.august.cupid.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 인증 컨트롤러
 * 로그인, 토큰 관리, 비밀번호 변경 등 인증 관련 API
 */
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
