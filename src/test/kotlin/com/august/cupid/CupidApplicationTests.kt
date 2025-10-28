package com.august.cupid

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "spring.data.mongodb.database=testdb_app",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.timeout=2000ms",
    "spring.data.redis.jedis.pool.max-active=8",
    "spring.data.redis.jedis.pool.max-idle=8",
    "spring.data.redis.jedis.pool.min-idle=0",
    "spring.data.redis.jedis.pool.max-wait=-1ms",
    "spring.datasource.url=jdbc:h2:mem:testdb_app;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
    "firebase.credentials-path=classpath:firebase-test.json"
])
class CupidApplicationTests {

    @Test
    fun contextLoads() {
    }

}