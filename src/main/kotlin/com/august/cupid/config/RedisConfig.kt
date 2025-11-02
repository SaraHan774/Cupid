package com.august.cupid.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import java.time.Duration

/**
 * Redis 설정
 *
 * 실시간 기능을 위한 Redis 연결 및 설정:
 * - 타이핑 인디케이터 (TTL 기반 상태 관리)
 * - 온라인 상태 (Presence)
 * - 읽지 않은 메시지 수 캐싱
 * - 세션 관리
 *
 * Connection Pooling:
 * - Lettuce 클라이언트 사용 (비동기, Netty 기반)
 * - 연결 풀링으로 성능 최적화
 * - 자동 재연결 및 장애 복구
 *
 * Architecture Decisions:
 * - Lettuce vs Jedis: Lettuce 선택
 *   - 비동기 및 논블로킹 I/O
 *   - 더 나은 성능과 낮은 지연시간
 *   - Spring Boot 기본 선택
 * - Serialization: String 직렬화
 *   - 단순하고 디버깅 용이
 *   - 타입 안정성 확보
 *   - 크로스 플랫폼 호환성
 *
 * Performance Tuning:
 * - Connection Pool 크기: 8 (기본값)
 * - Timeout: 2초 (빠른 실패)
 * - 재시도 로직: Lettuce 자동 처리
 *
 * Monitoring:
 * - 연결 상태 로깅
 * - 풀 통계 추적 가능
 */
@Configuration
class RedisConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${spring.redis.host:localhost}")
    private lateinit var redisHost: String

    @Value("\${spring.redis.port:6379}")
    private var redisPort: Int = 6379

    @Value("\${spring.redis.password:}")
    private var redisPassword: String = ""

    @Value("\${spring.redis.timeout:2000ms}")
    private lateinit var timeout: String

    @Value("\${spring.redis.lettuce.pool.max-active:8}")
    private var maxActive: Int = 8

    @Value("\${spring.redis.lettuce.pool.max-idle:8}")
    private var maxIdle: Int = 8

    @Value("\${spring.redis.lettuce.pool.min-idle:0}")
    private var minIdle: Int = 0

    /**
     * Redis 연결 팩토리 설정
     *
     * Lettuce Connection Factory:
     * - 비동기 및 논블로킹 I/O
     * - Netty 기반 고성능
     * - 자동 재연결
     * - 연결 풀링 지원
     *
     * Configuration:
     * - 독립 실행형 모드 (Standalone)
     * - 추후 Sentinel/Cluster 지원 가능
     */
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        logger.info("Redis 연결 팩토리 초기화: host={}, port={}", redisHost, redisPort)

        // Redis 서버 설정
        val serverConfig = RedisStandaloneConfiguration(redisHost, redisPort)

        // 비밀번호 설정 (있는 경우)
        if (redisPassword.isNotEmpty()) {
            serverConfig.setPassword(redisPassword)
            logger.info("Redis 비밀번호 설정됨")
        }

        // Lettuce 클라이언트 연결 풀 설정
        // Spring Boot의 자동 설정을 사용하므로 poolConfig는 선택적
        // application.yml의 spring.redis.lettuce.pool 설정이 자동으로 적용됨
        val poolConfig = LettucePoolingClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(parseTimeout(timeout)))
            .build()

        val connectionFactory = LettuceConnectionFactory(serverConfig, poolConfig)

        logger.info(
            "Redis 연결 풀 설정 완료: maxActive={}, maxIdle={}, minIdle={}, timeout={}ms",
            maxActive, maxIdle, minIdle, parseTimeout(timeout)
        )

        return connectionFactory
    }

    /**
     * RedisTemplate 설정
     *
     * Serialization Strategy:
     * - Key: String 직렬화
     * - Value: String 직렬화
     * - Hash Key: String 직렬화
     * - Hash Value: String 직렬화
     *
     * Why String Serialization?
     * - 단순하고 명확한 데이터 구조
     * - 디버깅 및 모니터링 용이
     * - Redis CLI로 직접 확인 가능
     * - 타입 변환 명시적 처리
     *
     * Alternative: JSON Serialization
     * - 복잡한 객체 저장 시 고려
     * - 현재는 단순한 문자열 값만 사용
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()

        // 연결 팩토리 설정
        template.connectionFactory = connectionFactory

        // 직렬화 설정
        val stringSerializer = StringRedisSerializer()

        template.keySerializer = stringSerializer
        template.valueSerializer = stringSerializer
        template.hashKeySerializer = stringSerializer
        template.hashValueSerializer = stringSerializer

        // 트랜잭션 지원 활성화 (선택적)
        template.setEnableTransactionSupport(false)

        template.afterPropertiesSet()

        logger.info("RedisTemplate 초기화 완료: String 직렬화 사용")

        return template
    }

    /**
     * 타임아웃 문자열 파싱
     *
     * Formats:
     * - "2000ms" -> 2000
     * - "2s" -> 2000
     * - "2000" -> 2000 (기본 ms)
     */
    private fun parseTimeout(timeoutStr: String): Long {
        return try {
            when {
                timeoutStr.endsWith("ms") -> {
                    timeoutStr.removeSuffix("ms").toLong()
                }
                timeoutStr.endsWith("s") -> {
                    timeoutStr.removeSuffix("s").toLong() * 1000
                }
                else -> {
                    timeoutStr.toLong()
                }
            }
        } catch (e: Exception) {
            logger.warn("타임아웃 파싱 실패, 기본값 2000ms 사용: {}", timeoutStr, e)
            2000L
        }
    }

    /**
     * Redis 연결 상태 체크 Bean (선택적)
     *
     * Use Cases:
     * - Health check 엔드포인트
     * - 모니터링 시스템 연동
     * - 시작 시 연결 확인
     */
    @Bean
    fun redisHealthCheck(redisTemplate: RedisTemplate<String, String>): RedisHealthChecker {
        return RedisHealthChecker(redisTemplate)
    }
}

/**
 * Redis 헬스 체커
 *
 * 연결 상태 확인 및 모니터링
 */
class RedisHealthChecker(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Redis 연결 상태 확인
     *
     * @return 연결 가능 여부
     */
    fun isHealthy(): Boolean {
        return try {
            // PING 명령 실행
            val connection = redisTemplate.connectionFactory?.connection
            val pong = connection?.ping()
            connection?.close()

            val isHealthy = pong != null && pong.isNotEmpty()

            if (isHealthy) {
                logger.debug("Redis 연결 정상: PONG 수신")
            } else {
                logger.warn("Redis 연결 실패: PONG 미수신")
            }

            isHealthy
        } catch (e: Exception) {
            logger.error("Redis 헬스 체크 실패", e)
            false
        }
    }

    /**
     * Redis 연결 통계 조회
     */
    fun getConnectionStats(): Map<String, Any> {
        return try {
            val info = mutableMapOf<String, Any>()

            val connection = redisTemplate.connectionFactory?.connection
            val dbSize = connection?.dbSize() ?: 0L
            connection?.close()

            info["dbSize"] = dbSize
            info["healthy"] = isHealthy()
            info["timestamp"] = System.currentTimeMillis()

            info
        } catch (e: Exception) {
            logger.error("Redis 통계 조회 실패", e)
            mapOf(
                "healthy" to false,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }
}
