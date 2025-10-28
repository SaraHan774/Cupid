package com.august.cupid.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * HealthController 통합 테스트
 * 서버 상태 확인 API 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "spring.data.mongodb.database=testdb_health",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.datasource.url=jdbc:h2:mem:testdb_health;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
    "firebase.credentials-path=classpath:firebase-test.json",
    "logging.level.org.springframework.data.mongodb=DEBUG"
])
class HealthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint should return UP status`() {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.time").exists())
            .andExpect(jsonPath("$.services").exists())
    }

    @Test
    fun `health endpoint should include all services`() {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.services.fcm").exists())
            .andExpect(jsonPath("$.services.postgresql").exists())
            .andExpect(jsonPath("$.services.mongodb").exists())
            .andExpect(jsonPath("$.services.redis").exists())
    }
}
