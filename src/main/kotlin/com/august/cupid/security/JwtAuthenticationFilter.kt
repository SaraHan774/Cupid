package com.august.cupid.security

import com.august.cupid.util.JwtUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * JWT 인증 필터
 * 요청 헤더에서 JWT 토큰을 추출하고 인증 처리
 * 토큰 블랙리스트 검증 포함
 */
@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val tokenBlacklistService: TokenBlacklistService
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // Authorization 헤더에서 토큰 추출
            val authHeader = request.getHeader("Authorization")
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = jwtUtil.removeBearerPrefix(authHeader)
                
                // 1. 토큰 블랙리스트 확인 (로그아웃된 토큰인지 체크)
                if (tokenBlacklistService.isTokenBlacklisted(token)) {
                    logger.warn("블랙리스트된 토큰으로 인증 시도")
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.writer.write("{\"success\":false,\"error\":\"토큰이 무효화되었습니다\"}")
                    return
                }
                
                // 2. 토큰 유효성 검증
                if (jwtUtil.validateAccessToken(token)) {
                    val userId = jwtUtil.getUserIdFromToken(token)
                    val username = jwtUtil.getUsernameFromToken(token)
                    
                    if (userId != null && username != null) {
                        // 인증 객체 생성
                        val authentication = UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            emptyList()
                        )
                        
                        // 인증 세부 정보 설정
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        
                        // Security Context에 인증 정보 설정
                        SecurityContextHolder.getContext().authentication = authentication
                        
                        logger.debug("JWT 인증 성공: $username ($userId)")
                    }
                } else {
                    logger.debug("JWT 토큰 유효성 검증 실패")
                }
            }
        } catch (e: Exception) {
            logger.error("JWT 인증 필터 오류: ${e.message}", e)
        }

        filterChain.doFilter(request, response)
    }

    /**
     * 특정 경로는 필터를 건너뛰도록 설정
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        
        // 공개 경로들 (인증 불필요)
        val publicPaths = listOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/health",
            "/ws",
            "/error"
        )
        
        return publicPaths.any { path.startsWith(it) }
    }
}
