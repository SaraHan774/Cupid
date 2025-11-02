package com.august.cupid.security

import io.github.bucket4j.ConsumptionProbe
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Rate Limit 인터셉터
 * 
 * 컨트롤러 메서드의 @RateLimit 어노테이션을 읽어서 Rate Limit을 적용
 * Filter보다 늦게 실행되므로, 세밀한 제어가 필요한 경우 사용
 * 
 * 동작 방식:
 * 1. HandlerMethod에서 @RateLimit 어노테이션 확인
 * 2. 어노테이션이 있으면 해당 Rate Limit 적용
 * 3. 어노테이션이 없으면 Filter의 기본 Rate Limit 사용
 */
@Component
class RateLimitInterceptor(
    private val rateLimitService: RateLimitService
) : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // HandlerMethod가 아니면 스킵 (정적 리소스 등)
        if (handler !is HandlerMethod) {
            return true
        }

        // @RateLimit 어노테이션 확인
        val rateLimitAnnotation = AnnotationUtils.findAnnotation(handler.method, RateLimit::class.java)
            ?: AnnotationUtils.findAnnotation(handler.beanType, RateLimit::class.java)

        // 어노테이션이 없으면 기본 Filter 처리로 위임
        if (rateLimitAnnotation == null) {
            return true
        }

        try {
            // Rate Limit 키 결정
            val key = getRateLimitKey(request, rateLimitAnnotation.keyType)

            // 버킷 조회 또는 생성
            val bucket = rateLimitService.getBucket(
                key = key,
                requests = rateLimitAnnotation.requests,
                windowMinutes = rateLimitAnnotation.windowMinutes,
                endpoint = request.requestURI
            )

            // Rate Limit 체크
            val probe: ConsumptionProbe = bucket.tryConsumeAndReturnRemaining(1)

            if (probe.isConsumed) {
                // 요청 허용
                response.setHeader("X-RateLimit-Limit", "${rateLimitAnnotation.requests}")
                response.setHeader("X-RateLimit-Remaining", "${probe.remainingTokens}")
                response.setHeader(
                    "X-RateLimit-Reset",
                    "${System.currentTimeMillis() + probe.nanosToWaitForRefill / 1_000_000}"
                )

                logger.debug(
                    "Rate limit check passed: key=$key, remaining=${probe.remainingTokens}, endpoint=${request.requestURI}"
                )

                return true
            } else {
                // Rate Limit 초과
                val retryAfterSeconds = probe.nanosToWaitForRefill / 1_000_000_000

                logger.warn(
                    "Rate limit exceeded: key=$key, endpoint=${request.requestURI}, retryAfter=${retryAfterSeconds}s"
                )

                response.status = 429  // Too Many Requests
                response.setHeader("Retry-After", retryAfterSeconds.toString())
                response.contentType = "application/json;charset=UTF-8"
                response.characterEncoding = "UTF-8"
                response.writer.write("""
                    {
                        "success": false,
                        "error": "요청 횟수가 초과되었습니다. 잠시 후 다시 시도해주세요.",
                        "retryAfter": $retryAfterSeconds
                    }
                """.trimIndent())

                return false
            }
        } catch (e: Exception) {
            logger.error("Rate limit check failed for ${request.requestURI}", e)
            // Rate limit 체크 실패 시 요청 허용 (서비스 안정성 우선)
            return true
        }
    }

    /**
     * Rate Limit 키 결정
     */
    private fun getRateLimitKey(request: HttpServletRequest, keyType: RateLimitKeyType): String {
        return when (keyType) {
            RateLimitKeyType.USER -> {
                // 사용자 ID 기반
                val authentication: Authentication? = SecurityContextHolder.getContext().authentication
                if (authentication != null && authentication.isAuthenticated && authentication.name != "anonymousUser") {
                    "user:${authentication.name}"
                } else {
                    // 인증되지 않은 경우 IP 사용
                    "ip:${getClientIpAddress(request)}"
                }
            }
            RateLimitKeyType.IP -> {
                // IP 주소 기반
                "ip:${getClientIpAddress(request)}"
            }
        }
    }

    /**
     * 클라이언트의 실제 IP 주소 추출
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

