package com.august.cupid.config

import com.august.cupid.security.JwtAuthenticationFilter
import com.august.cupid.security.RateLimitFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security 설정
 * JWT 기반 인증, Rate Limiting 및 보안 설정
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val rateLimitFilter: RateLimitFilter
) {

    /**
     * 비밀번호 인코더 Bean
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    /**
     * Security Filter Chain 설정
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 공개 경로 (인증 불필요)
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/health",
                        "/api/v1/online-status/**",  // 온라인 상태 API 공개
                        "/ws/**",
                        "/static/**",               // 정적 파일 공개
                        "/uploads/**",              // 업로드된 파일 공개 (프로필 이미지 등)
                        "/websocket-test.html",     // 테스트 페이지 공개
                        "/swagger-ui/**",           // Swagger UI
                        "/swagger-ui.html",         // Swagger UI 메인 페이지
                        "/v3/api-docs/**",          // OpenAPI 문서
                        "/error"
                    ).permitAll()
                    // WebSocket 엔드포인트
                    .requestMatchers("/ws").permitAll()
                    // 나머지 모든 요청은 인증 필요
                    .anyRequest().authenticated()
            }
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    /**
     * CORS 설정
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        
        // 허용할 Origin 설정 (개발 환경에서는 모든 Origin 허용)
        configuration.allowedOriginPatterns = listOf("*")
        
        // 허용할 HTTP 메서드
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        
        // 허용할 헤더
        configuration.allowedHeaders = listOf("*")
        
        // 인증 정보 포함 허용
        configuration.allowCredentials = true
        
        // Preflight 요청 캐시 시간
        configuration.maxAge = 3600L
        
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        
        return source
    }
}
