package com.august.cupid.security

import com.august.cupid.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * JWT 토큰 블랙리스트 관리 서비스
 * 로그아웃 및 비밀번호 변경 시 토큰을 블랙리스트에 추가하여 무효화
 */
@Service
class TokenBlacklistService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val jwtUtil: JwtUtil
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Redis 키 패턴
        private const val TOKEN_BLACKLIST_KEY_PREFIX = "token:blacklist:"
    }

    /**
     * 토큰을 블랙리스트에 추가
     * 토큰 만료 시간까지 유효하도록 TTL 설정
     * 
     * @param token JWT 토큰
     */
    fun addTokenToBlacklist(token: String) {
        try {
            // 토큰에서 만료 시간 추출
            val expirationTime = jwtUtil.getExpirationTime(token)
            if (expirationTime == null || expirationTime <= 0) {
                logger.warn("토큰 블랙리스트 추가 실패: 만료 시간을 계산할 수 없습니다")
                return
            }

            val key = "$TOKEN_BLACKLIST_KEY_PREFIX$token"
            redisTemplate.opsForValue().set(key, "blacklisted", expirationTime, TimeUnit.MILLISECONDS)
            
            logger.info("토큰이 블랙리스트에 추가되었습니다. 만료까지: ${expirationTime}ms")
        } catch (e: Exception) {
            logger.error("토큰 블랙리스트 추가 실패: ${e.message}", e)
        }
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     * 
     * @param token JWT 토큰
     * @return 블랙리스트 여부
     */
    fun isTokenBlacklisted(token: String): Boolean {
        return try {
            val key = "$TOKEN_BLACKLIST_KEY_PREFIX$token"
            val value = redisTemplate.opsForValue().get(key)
            value != null
        } catch (e: Exception) {
            logger.error("토큰 블랙리스트 확인 실패: ${e.message}", e)
            // 오류 발생 시 안전을 위해 true 반환 (토큰 차단)
            true
        }
    }

    /**
     * 사용자의 모든 토큰을 블랙리스트에 추가
     * 비밀번호 변경 시 모든 세션 무효화용
     * 
     * @param userId 사용자 ID
     */
    fun invalidateAllUserTokens(userId: String) {
        try {
            // 사용자 ID를 키로 사용하여 토큰 버전 관리
            val versionKey = "token:version:$userId"
            redisTemplate.opsForValue().increment(versionKey, 1)
            
            // 토큰 버전에 TTL 설정 (기본 토큰 만료 시간인 24시간)
            redisTemplate.expire(versionKey, 24, TimeUnit.HOURS)
            
            logger.info("All tokens for user $userId have been invalidated")
        } catch (e: Exception) {
            logger.error("Failed to invalidate user tokens: ${e.message}", e)
        }
    }

    /**
     * 토큰 버전 확인 (비밀번호 변경 등으로 인한 토큰 무효화 체크용)
     * 
     * @param userId 사용자 ID
     * @return 현재 토큰 버전
     */
    fun getTokenVersion(userId: String): Long {
        return try {
            val versionKey = "token:version:$userId"
            val version = redisTemplate.opsForValue().get(versionKey)
            version?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            logger.error("토큰 버전 조회 실패: ${e.message}", e)
            0
        }
    }
}

