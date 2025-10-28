package com.august.cupid.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI (Swagger) 문서 설정
 *
 * 접속 URL:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - API Docs JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(apiInfo())
            .servers(listOf(
                Server().url("http://localhost:8080").description("로컬 개발 서버"),
                Server().url("https://api.cupid.com").description("프로덕션 서버")
            ))
            .components(
                Components()
                    .addSecuritySchemes("bearerAuth", securityScheme())
            )
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
    }

    private fun apiInfo(): Info {
        return Info()
            .title("Cupid Chat API")
            .description("""
                ## Cupid 채팅 애플리케이션 REST API 문서

                ### 주요 기능
                - 사용자 인증 및 권한 관리 (JWT)
                - 실시간 메시징 (WebSocket/STOMP)
                - 매칭 시스템
                - 채널 관리
                - 푸시 알림 (Firebase)
                - E2E 암호화 (Signal Protocol)

                ### 인증 방법
                1. `/api/v1/auth/register` 또는 `/api/v1/auth/login` 으로 JWT 토큰 발급
                2. 우측 상단 "Authorize" 버튼 클릭
                3. `Bearer {token}` 형식으로 입력
                4. "Authorize" 클릭 후 API 테스트

                ### WebSocket 연결
                - 엔드포인트: `ws://localhost:8080/ws?userId={userId}`
                - 프로토콜: STOMP over SockJS
                - 테스트 페이지: http://localhost:8080/websocket-test.html
            """.trimIndent())
            .version("v1.0.0")
            .contact(
                Contact()
                    .name("Cupid Team")
                    .email("dev@cupid.com")
                    .url("https://cupid.com")
            )
            .license(
                License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")
            )
    }

    private fun securityScheme(): SecurityScheme {
        return SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .`in`(SecurityScheme.In.HEADER)
            .name("Authorization")
            .description("JWT 토큰을 입력하세요. 'Bearer ' 접두사는 자동으로 추가됩니다.")
    }
}
