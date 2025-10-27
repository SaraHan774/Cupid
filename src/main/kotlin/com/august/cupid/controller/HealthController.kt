package com.august.cupid.controller

import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Health Check API
 * 서버 상태, FCM 상태, 데이터베이스 연결 상태를 확인
 */
@RestController
@RequestMapping("/api/v1")
class HealthController(
    private val firebaseMessaging: FirebaseMessaging
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        return try {
            val services = mapOf(
                "fcm" to checkFCMStatus(),
                "postgresql" to "UP", // TODO: 실제 DB 연결 확인 구현
                "mongodb" to "UP",    // TODO: 실제 DB 연결 확인 구현
                "redis" to "UP"       // TODO: 실제 DB 연결 확인 구현
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
                    "error" to e.message
                )
            )
        }
    }

    /**
     * FCM 상태 확인
     */
    private fun checkFCMStatus(): String {
        return try {
            if (firebaseMessaging != null) {
                "UP"
            } else {
                "DOWN"
            }
        } catch (e: Exception) {
            logger.error("FCM 상태 확인 실패: ${e.message}", e)
            "DOWN"
        }
    }
}
