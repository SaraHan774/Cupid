package com.august.cupid.util

import com.august.cupid.model.dto.TokenInfo
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.Key
import java.util.*
import java.util.Date

/**
 * JWT 토큰 유틸리티
 * 토큰 생성, 검증, 파싱 기능 제공
 */
@Component
class JwtUtil(
    @Value("\${jwt.secret}")
    private val secret: String,
    
    @Value("\${jwt.expiration}")
    private val expiration: Long,
    
    @Value("\${jwt.refresh-expiration}")
    private val refreshExpiration: Long
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    
    // JWT 서명 키 생성
    private val key: Key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    /**
     * Access Token 생성
     */
    fun generateAccessToken(userId: UUID, username: String, email: String?): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("username", username)
            .claim("email", email)
            .claim("tokenType", "ACCESS")
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * Refresh Token 생성
     */
    fun generateRefreshToken(userId: UUID): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshExpiration)

        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("tokenType", "REFRESH")
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    fun getUserIdFromToken(token: String): UUID? {
        return try {
            val claims = getClaimsFromToken(token)
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            logger.error("토큰에서 사용자 ID 추출 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 토큰에서 사용자명 추출
     */
    fun getUsernameFromToken(token: String): String? {
        return try {
            val claims = getClaimsFromToken(token)
            claims["username"] as? String
        } catch (e: Exception) {
            logger.error("토큰에서 사용자명 추출 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 토큰에서 이메일 추출
     */
    fun getEmailFromToken(token: String): String? {
        return try {
            val claims = getClaimsFromToken(token)
            claims["email"] as? String
        } catch (e: Exception) {
            logger.error("토큰에서 이메일 추출 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 토큰에서 토큰 타입 추출
     */
    fun getTokenTypeFromToken(token: String): String? {
        return try {
            val claims = getClaimsFromToken(token)
            claims["tokenType"] as? String
        } catch (e: Exception) {
            logger.error("토큰에서 토큰 타입 추출 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 토큰 유효성 검증
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token)
            !isTokenExpired(claims)
        } catch (e: Exception) {
            logger.error("토큰 유효성 검증 실패: ${e.message}", e)
            false
        }
    }

    /**
     * Access Token 유효성 검증
     */
    fun validateAccessToken(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token)
            val tokenType = claims["tokenType"] as? String
            !isTokenExpired(claims) && tokenType == "ACCESS"
        } catch (e: Exception) {
            logger.error("Access Token 유효성 검증 실패: ${e.message}", e)
            false
        }
    }

    /**
     * Refresh Token 유효성 검증
     */
    fun validateRefreshToken(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token)
            val tokenType = claims["tokenType"] as? String
            !isTokenExpired(claims) && tokenType == "REFRESH"
        } catch (e: Exception) {
            logger.error("Refresh Token 유효성 검증 실패: ${e.message}", e)
            false
        }
    }

    /**
     * 토큰 만료 시간 확인
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token)
            isTokenExpired(claims)
        } catch (e: Exception) {
            logger.error("토큰 만료 시간 확인 실패: ${e.message}", e)
            true
        }
    }

    /**
     * 토큰에서 만료 시간까지 남은 시간 (밀리초)
     */
    fun getExpirationTime(token: String): Long? {
        return try {
            val claims = getClaimsFromToken(token)
            claims.expiration.time - System.currentTimeMillis()
        } catch (e: Exception) {
            logger.error("토큰 만료 시간 계산 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 토큰 정보 추출
     */
    fun getTokenInfo(token: String): TokenInfo? {
        return try {
            val claims = getClaimsFromToken(token)
            TokenInfo(
                userId = UUID.fromString(claims.subject),
                username = claims["username"] as? String ?: "",
                email = claims["email"] as? String,
                issuedAt = claims.issuedAt.time,
                expiresAt = claims.expiration.time,
                tokenType = claims["tokenType"] as? String ?: ""
            )
        } catch (e: Exception) {
            logger.error("토큰 정보 추출 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 토큰에서 Claims 추출
     */
    private fun getClaimsFromToken(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }

    /**
     * Claims에서 토큰 만료 여부 확인
     */
    private fun isTokenExpired(claims: Claims): Boolean {
        return claims.expiration.before(Date())
    }

    /**
     * 토큰에서 Bearer 접두사 제거
     */
    fun removeBearerPrefix(token: String): String {
        return if (token.startsWith("Bearer ")) {
            token.substring(7)
        } else {
            token
        }
    }

    /**
     * 토큰에 Bearer 접두사 추가
     */
    fun addBearerPrefix(token: String): String {
        return if (!token.startsWith("Bearer ")) {
            "Bearer $token"
        } else {
            token
        }
    }
}
