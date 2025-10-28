package com.august.cupid.security

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.Refill
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate Limiting 서비스
 * Redis를 사용하여 분산 환경에서 Rate Limit 관리
 * 
 * Rate Limit 정책:
 * - 로그인 API: 5회/분
 * - 회원가입 API: 3회/시간
 * - 메시지 전송 API: 50회/분
 * - WebSocket 연결: 5개/사용자
 * - 기타 API: 100회/분
 * 
 * 테스트 환경에서는 매우 관대한 제한 적용
 */
@Service
class RateLimitService(
    @Value("\${spring.redis.host:localhost}")
    private val redisHost: String,
    
    @Value("\${spring.redis.port:6379}")
    private val redisPort: Int,
    
    private val environment: Environment
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    
    private lateinit var redisClient: RedisClient
    private lateinit var connection: StatefulRedisConnection<ByteArray, ByteArray>
    private lateinit var proxyManager: LettuceBasedProxyManager<ByteArray>

    // 테스트 환경 여부 확인
    private val isTestProfile: Boolean
        get() = environment.activeProfiles.contains("test")

    companion object {
        // 운영 환경 Rate Limit 설정
        private const val LOGIN_REQUESTS = 5L
        private const val LOGIN_WINDOW_MINUTES = 1L
        private const val REGISTER_REQUESTS = 3L
        private const val REGISTER_WINDOW_MINUTES = 60L
        private const val MESSAGE_REQUESTS = 50L
        private const val MESSAGE_WINDOW_MINUTES = 1L
        private const val GENERAL_REQUESTS = 100L
        private const val GENERAL_WINDOW_MINUTES = 1L
        private const val WEBSOCKET_REQUESTS = 5L
        private const val WEBSOCKET_WINDOW_MINUTES = 1L
        
        // 테스트 환경 Rate Limit 설정 (매우 관대)
        private const val TEST_LOGIN_REQUESTS = 1000L
        private const val TEST_REGISTER_REQUESTS = 1000L
        private const val TEST_MESSAGE_REQUESTS = 1000L
        private const val TEST_GENERAL_REQUESTS = 1000L
        private const val TEST_WEBSOCKET_REQUESTS = 1000L
        private const val TEST_WINDOW_MINUTES = 1L
    }

    /**
     * Redis 연결 및 ProxyManager 초기화
     */
    @PostConstruct
    fun init() {
        try {
            redisClient = RedisClient.create("redis://$redisHost:$redisPort")
            connection = redisClient.connect(ByteArrayCodec.INSTANCE)
            proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy { _, _ -> Duration.ofMinutes(61L).toNanos() }
                .build()
            
            val profileInfo = if (isTestProfile) "TEST PROFILE - 관대한 제한 적용" else "운영 환경 - 표준 제한 적용"
            logger.info("Rate Limit Service initialized with Redis: $redisHost:$redisPort - $profileInfo")
        } catch (e: Exception) {
            logger.error("Failed to initialize Rate Limit Service", e)
            throw e
        }
    }

    /**
     * 연결 종료
     */
    @PreDestroy
    fun destroy() {
        try {
            connection.close()
            redisClient.shutdown()
            logger.info("Rate Limit Service shut down")
        } catch (e: Exception) {
            logger.error("Error shutting down Rate Limit Service", e)
        }
    }

    /**
     * URI에 맞는 Rate Limit 버킷 조회 또는 생성
     */
    fun getBucket(key: String, uri: String): Bucket {
        val bucketKey = "rate_limit:$key:$uri".toByteArray()
        
        val bandwidth = when {
            uri.contains("/auth/login") -> getLoginBandwidth()
            uri.contains("/auth/register") -> getRegisterBandwidth()
            uri.contains("/messages") && uri.contains("/send") -> getMessageBandwidth()
            uri.contains("/ws") -> getWebSocketBandwidth()
            else -> getGeneralBandwidth()
        }
        
        val configuration = BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build()
        
        return proxyManager.builder().build(bucketKey, configuration)
    }

    /**
     * 로그인 API Rate Limit
     * 운영 환경: 5회/분
     * 테스트 환경: 1000회/분
     */
    private fun getLoginBandwidth(): Bandwidth {
        return if (isTestProfile) {
            Bandwidth.classic(
                TEST_LOGIN_REQUESTS,
                Refill.intervally(TEST_LOGIN_REQUESTS, Duration.ofMinutes(TEST_WINDOW_MINUTES))
            )
        } else {
            Bandwidth.classic(
                LOGIN_REQUESTS,
                Refill.intervally(LOGIN_REQUESTS, Duration.ofMinutes(LOGIN_WINDOW_MINUTES))
            )
        }
    }

    /**
     * 회원가입 API Rate Limit
     * 운영 환경: 3회/시간
     * 테스트 환경: 1000회/분
     */
    private fun getRegisterBandwidth(): Bandwidth {
        return if (isTestProfile) {
            Bandwidth.classic(
                TEST_REGISTER_REQUESTS,
                Refill.intervally(TEST_REGISTER_REQUESTS, Duration.ofMinutes(TEST_WINDOW_MINUTES))
            )
        } else {
            Bandwidth.classic(
                REGISTER_REQUESTS,
                Refill.intervally(REGISTER_REQUESTS, Duration.ofMinutes(REGISTER_WINDOW_MINUTES))
            )
        }
    }

    /**
     * 메시지 전송 API Rate Limit
     * 운영 환경: 50회/분
     * 테스트 환경: 1000회/분
     */
    private fun getMessageBandwidth(): Bandwidth {
        return if (isTestProfile) {
            Bandwidth.classic(
                TEST_MESSAGE_REQUESTS,
                Refill.intervally(TEST_MESSAGE_REQUESTS, Duration.ofMinutes(TEST_WINDOW_MINUTES))
            )
        } else {
            Bandwidth.classic(
                MESSAGE_REQUESTS,
                Refill.intervally(MESSAGE_REQUESTS, Duration.ofMinutes(MESSAGE_WINDOW_MINUTES))
            )
        }
    }

    /**
     * WebSocket 연결 Rate Limit
     * 운영 환경: 5개/분
     * 테스트 환경: 1000개/분
     */
    private fun getWebSocketBandwidth(): Bandwidth {
        return if (isTestProfile) {
            Bandwidth.classic(
                TEST_WEBSOCKET_REQUESTS,
                Refill.intervally(TEST_WEBSOCKET_REQUESTS, Duration.ofMinutes(TEST_WINDOW_MINUTES))
            )
        } else {
            Bandwidth.classic(
                WEBSOCKET_REQUESTS,
                Refill.intervally(WEBSOCKET_REQUESTS, Duration.ofMinutes(WEBSOCKET_WINDOW_MINUTES))
            )
        }
    }

    /**
     * 일반 API Rate Limit
     * 운영 환경: 100회/분
     * 테스트 환경: 1000회/분
     */
    private fun getGeneralBandwidth(): Bandwidth {
        return if (isTestProfile) {
            Bandwidth.classic(
                TEST_GENERAL_REQUESTS,
                Refill.intervally(TEST_GENERAL_REQUESTS, Duration.ofMinutes(TEST_WINDOW_MINUTES))
            )
        } else {
            Bandwidth.classic(
                GENERAL_REQUESTS,
                Refill.intervally(GENERAL_REQUESTS, Duration.ofMinutes(GENERAL_WINDOW_MINUTES))
            )
        }
    }
}

