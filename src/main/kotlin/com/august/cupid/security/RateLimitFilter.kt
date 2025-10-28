package com.august.cupid.security

import io.github.bucket4j.Bucket
import io.github.bucket4j.ConsumptionProbe
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * API Rate Limiting 필터
 * 각 요청의 Rate Limit을 체크하고 제한을 초과하면 429 Too Many Requests 응답
 * 
 * Rate Limit 정책:
 * - 인증 API (로그인, 회원가입): IP 주소 기반
 * - 기타 API: 사용자 ID 기반
 * 
 * 테스트 환경에서는 필터를 건너뛰어 테스트가 안정적으로 실행되도록 함
 */
@Component
class RateLimitFilter(
    private val rateLimitService: RateLimitService,
    private val environment: Environment
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(javaClass)
    
    // 테스트 환경 여부 확인
    private val isTestProfile: Boolean
        get() = environment.activeProfiles.contains("test")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 테스트 환경에서는 Rate Limit 필터를 건너뜀
        if (isTestProfile) {
            filterChain.doFilter(request, response)
            return
        }
        
        // Health check 엔드포인트는 Rate Limit 제외
        if (request.requestURI == "/api/v1/health") {
            filterChain.doFilter(request, response)
            return
        }
        
        // Swagger UI 및 OpenAPI 문서는 Rate Limit 제외
        if (request.requestURI.startsWith("/swagger-ui") || 
            request.requestURI.startsWith("/v3/api-docs") ||
            request.requestURI == "/swagger-ui.html") {
            filterChain.doFilter(request, response)
            return
        }

        try {
            // Rate Limit 키 결정 (IP 또는 User ID)
            val key = getRateLimitKey(request)
            
            // Bucket 조회 또는 생성
            val bucket = rateLimitService.getBucket(key, request.requestURI)
            
            // Rate Limit 체크
            val probe = bucket.tryConsumeAndReturnRemaining(1)
            
            if (probe.isConsumed) {
                // 요청 허용
                response.setHeader("X-RateLimit-Limit", "${bucket.availableTokens + 1}")
                response.setHeader("X-RateLimit-Remaining", "${probe.remainingTokens}")
                response.setHeader("X-RateLimit-Reset", "${System.currentTimeMillis() + probe.nanosToWaitForRefill / 1_000_000}")
                
                filterChain.doFilter(request, response)
            } else {
                // Rate Limit 초과
                logger.warn("Rate limit exceeded for key: $key, URI: ${request.requestURI}")
                
                response.status = 429
                response.contentType = "application/json;charset=UTF-8"
                response.characterEncoding = "UTF-8"
                response.writer.write("""
                    {
                        "success": false,
                        "error": "요청 횟수가 초과되었습니다. 잠시 후 다시 시도해주세요.",
                        "retryAfter": ${probe.nanosToWaitForRefill / 1_000_000_000}
                    }
                """.trimIndent())
            }
        } catch (e: Exception) {
            logger.error("Rate limit check failed", e)
            // Rate limit 체크 실패 시 요청 허용 (서비스 안정성 우선)
            filterChain.doFilter(request, response)
        }
    }

    /**
     * Rate Limit 키를 결정
     * - 인증 API는 IP 주소
     * - 인증된 사용자는 User ID
     * - 그 외는 IP 주소
     */
    private fun getRateLimitKey(request: HttpServletRequest): String {
        val uri = request.requestURI
        
        // 인증 API는 IP 주소 기반
        if (uri.startsWith("/api/v1/auth/login") || uri.startsWith("/api/v1/auth/register")) {
            return "ip:${getClientIpAddress(request)}"
        }
        
        // 인증된 사용자는 User ID 기반
        val authentication: Authentication? = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication.isAuthenticated && authentication.name != "anonymousUser") {
            return "user:${authentication.name}"
        }
        
        // 그 외는 IP 주소 기반
        return "ip:${getClientIpAddress(request)}"
    }

    /**
     * 클라이언트의 실제 IP 주소 추출
     * 프록시나 로드 밸런서를 거쳐 올 경우를 고려
     */
    private fun getClientIpAddress(request: HttpServletRequest): String {
        var ipAddress = request.getHeader("X-Forwarded-For")
        
        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("Proxy-Client-IP")
        }
        
        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP")
        }
        
        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP")
        }
        
        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR")
        }
        
        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.remoteAddr
        }
        
        // X-Forwarded-For에는 여러 IP가 있을 수 있음 (첫 번째가 원본 클라이언트)
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim()
        }
        
        return ipAddress ?: "unknown"
    }
}

