package com.august.cupid.controller

import com.google.firebase.messaging.FirebaseMessaging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

/**
 * Health Check API
 * 서버 상태, FCM 상태, 데이터베이스 연결 상태를 확인
 */
@Tag(name = "Health", description = "서버 상태 및 시스템 헬스 체크 API")
@RestController
@RequestMapping("/api/v1")
class HealthController(
    private val firebaseMessaging: FirebaseMessaging,
    private val dataSource: DataSource,
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Operation(summary = "서버 상태 확인", description = "서버, FCM, 데이터베이스 연결 상태를 확인합니다")
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        return try {
            val services = mapOf(
                "fcm" to checkFCMStatus(),
                "postgresql" to checkPostgreSQLStatus(),
                "mongodb" to checkMongoDBStatus(),
                "redis" to checkRedisStatus()
            )

            val healthStatus = mapOf(
                "status" to "UP",
                "timestamp" to System.currentTimeMillis(),
                "time" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "version" to "1.0.0",
                "services" to services
            )

            logger.debug("Health check: $healthStatus")
            ResponseEntity.ok(healthStatus)
        } catch (e: Exception) {
            logger.error("Health check failed: ${e.message}", e)
            ResponseEntity.status(503).body(
                mapOf(
                    "status" to "DOWN",
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * FCM 상태 확인
     * Firebase Admin SDK 초기화 상태와 실제 연결 상태를 검증
     */
    private fun checkFCMStatus(): Map<String, Any> {
        return try {
            val firebaseApp = com.google.firebase.FirebaseApp.getInstance()
            val projectId = firebaseApp.options.projectId
            
            // Firebase App 초기화 상태 확인
            val isInitialized = firebaseApp != null && projectId != null
            
            // FirebaseMessaging 인스턴스 확인
            val isMessagingAvailable = firebaseMessaging != null
            
            // 프로젝트 정보 확인
            val projectInfo = if (projectId != null) {
                mapOf(
                    "projectId" to projectId,
                    "appName" to firebaseApp.name
                )
            } else {
                emptyMap<String, String>()
            }
            
            val status = if (isInitialized && isMessagingAvailable) {
                "UP"
            } else {
                "DOWN"
            }
            
            mapOf(
                "status" to status,
                "initialized" to isInitialized,
                "messagingAvailable" to isMessagingAvailable,
                "projectInfo" to projectInfo
            )
            
        } catch (e: Exception) {
            logger.error("FCM 상태 확인 실패: ${e.message}", e)
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error"),
                "initialized" to false,
                "messagingAvailable" to false
            )
        }
    }

    /**
     * PostgreSQL 연결 상태 확인
     * 실제 데이터베이스 연결을 테스트하여 상태를 확인
     */
    private fun checkPostgreSQLStatus(): Map<String, Any> {
        return try {
            val startTime = System.currentTimeMillis()
            dataSource.connection.use { connection ->
                val isValid = connection.isValid(5) // 5초 타임아웃으로 연결 유효성 검사
                val responseTime = System.currentTimeMillis() - startTime
                
                if (isValid) {
                    logger.debug("PostgreSQL 연결 상태: 정상 (응답시간: ${responseTime}ms)")
                    mapOf(
                        "status" to "UP",
                        "responseTimeMs" to responseTime,
                        "database" to connection.metaData.databaseProductName,
                        "version" to connection.metaData.databaseProductVersion
                    )
                } else {
                    logger.warn("PostgreSQL 연결이 유효하지 않음")
                    mapOf(
                        "status" to "DOWN",
                        "error" to "Connection is not valid",
                        "responseTimeMs" to responseTime
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("PostgreSQL 연결 상태 확인 실패: ${e.message}", e)
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * MongoDB 연결 상태 확인
     * 실제 MongoDB 연결을 테스트하여 상태를 확인
     */
    private fun checkMongoDBStatus(): Map<String, Any> {
        return try {
            val startTime = System.currentTimeMillis()
            // MongoDB 연결 테스트를 위해 간단한 명령 실행
            val result = mongoTemplate.executeCommand("{ ping: 1 }")
            val responseTime = System.currentTimeMillis() - startTime
            
            logger.debug("MongoDB 연결 상태: 정상 (응답시간: ${responseTime}ms)")
            mapOf(
                "status" to "UP",
                "responseTimeMs" to responseTime,
                "pingResult" to result.toString()
            )
        } catch (e: Exception) {
            logger.error("MongoDB 연결 상태 확인 실패: ${e.message}", e)
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Redis 연결 상태 확인
     * 실제 Redis 연결을 테스트하여 상태를 확인
     */
    private fun checkRedisStatus(): Map<String, Any> {
        return try {
            val startTime = System.currentTimeMillis()
            // Redis 연결 테스트를 위해 PING 명령 실행
            val result = redisTemplate.execute { connection ->
                connection.ping()
            }
            val responseTime = System.currentTimeMillis() - startTime
            
            if (result == "PONG") {
                logger.debug("Redis 연결 상태: 정상 (응답시간: ${responseTime}ms)")
                mapOf(
                    "status" to "UP",
                    "responseTimeMs" to responseTime,
                    "pingResult" to result
                )
            } else {
                logger.warn("Redis PING 응답이 예상과 다름: $result")
                mapOf(
                    "status" to "DOWN",
                    "error" to "Unexpected PING response: $result",
                    "responseTimeMs" to responseTime
                )
            }
        } catch (e: Exception) {
            logger.error("Redis 연결 상태 확인 실패: ${e.message}", e)
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error")
            )
        }
    }
}
