package com.august.cupid.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.ResourceUtils
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Firebase Admin SDK 설정
 * FCM (Firebase Cloud Messaging)을 위한 초기화
 * 
 * 지원하는 credentials 소스:
 * 1. 환경 변수 FIREBASE_CREDENTIALS_JSON (Secrets Manager에서 주입)
 * 2. 파일 경로 (firebase.credentials.path)
 *    - classpath: 리소스 경로
 *    - file: 파일 시스템 경로
 *    - http/https: URL 경로
 */
@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${firebase.credentials.path:classpath:firebase-service-account.json}")
    private lateinit var credentialsPath: String

    @Value("\${firebase.credentials-json:}")
    private var credentialsJson: String = ""

    @Bean
    fun firebaseApp(): FirebaseApp {
        return try {
            // Firebase App이 이미 초기화되어 있는지 확인
            if (FirebaseApp.getApps().isEmpty()) {
                logger.info("Firebase App 초기화 시작")
                
                val credentials: GoogleCredentials
                val projectId: String?
                
                // 우선순위 1: 환경 변수에서 JSON 문자열 직접 읽기 (Secrets Manager 사용 시)
                if (credentialsJson.isNotBlank()) {
                    logger.info("Firebase credentials를 환경 변수에서 읽기")
                    val jsonBytes = credentialsJson.toByteArray(StandardCharsets.UTF_8)
                    credentials = GoogleCredentials.fromStream(ByteArrayInputStream(jsonBytes))
                    projectId = com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(jsonBytes)
                        .get("project_id")?.asText()
                } else {
                    // 우선순위 2: 파일 경로에서 읽기
                    logger.info("Firebase credentials를 파일 경로에서 읽기: $credentialsPath")
                    val credentialsStream = ResourceUtils.getURL(credentialsPath).openStream()
                    val serviceAccountJson = credentialsStream.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    
                    // JSON 파싱하여 project_id 추출
                    projectId = com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(serviceAccountJson)
                        .get("project_id")?.asText()
                    
                    // Credentials 생성 (stream을 다시 생성)
                    credentials = GoogleCredentials.fromStream(
                        ResourceUtils.getURL(credentialsPath).openStream()
                    )
                }
                
                logger.info("Firebase Project ID: $projectId")
                
                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build()
                
                val app = FirebaseApp.initializeApp(options)
                logger.info("Firebase App 초기화 완료: ${app.name}")
                app
            } else {
                logger.info("Firebase App이 이미 초기화되어 있음")
                FirebaseApp.getInstance()
            }
        } catch (e: Exception) {
            logger.error("Firebase App 초기화 실패: ${e.message}", e)
            throw RuntimeException("Firebase 초기화 실패", e)
        }
    }

    @Bean
    fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging {
        return try {
            FirebaseMessaging.getInstance(firebaseApp).apply {
                logger.info("FirebaseMessaging Bean 생성 완료")
            }
        } catch (e: Exception) {
            logger.error("FirebaseMessaging Bean 생성 실패: ${e.message}", e)
            throw RuntimeException("FirebaseMessaging 생성 실패", e)
        }
    }
}
