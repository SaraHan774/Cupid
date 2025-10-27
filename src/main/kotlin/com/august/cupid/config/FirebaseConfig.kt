package com.august.cupid.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.util.ResourceUtils
import java.io.FileInputStream

/**
 * Firebase Admin SDK 설정
 * FCM (Firebase Cloud Messaging)을 위한 초기화
 */
@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${firebase.credentials.path}")
    private lateinit var credentialsPath: String

    @Bean
    fun firebaseApp(): FirebaseApp {
        return try {
            // Firebase App이 이미 초기화되어 있는지 확인
            if (FirebaseApp.getApps().isEmpty()) {
                logger.info("Firebase App 초기화 시작: $credentialsPath")
                
                val credentialsStream = ResourceUtils.getURL(credentialsPath).openStream()
                val credentials = GoogleCredentials.fromStream(credentialsStream)
                
                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
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
