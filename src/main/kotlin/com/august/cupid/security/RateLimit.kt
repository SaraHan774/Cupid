package com.august.cupid.security

/**
 * Rate Limit 어노테이션
 * 
 * 컨트롤러 메서드에 Rate Limit을 적용하기 위한 어노테이션
 * 
 * 사용 예시:
 * ```
 * @RateLimit(requests = 5, windowMinutes = 1)
 * @PostMapping("/keys/generate")
 * fun generateKeys(...) { ... }
 * ```
 * 
 * Rate Limit 정책:
 * - Key generation: 5 per minute (비용이 큰 작업)
 * - Encryption/Decryption: 100 per minute
 * - Key rotation: 1 per hour
 * - Key bundle retrieval: 10 per minute
 * - Session operations: 100 per hour
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
    /**
     * 허용할 요청 수
     */
    val requests: Long = 100,
    
    /**
     * 시간 윈도우 (분 단위)
     */
    val windowMinutes: Long = 1,
    
    /**
     * Rate Limit 키 타입
     * - USER: 사용자 ID 기반 (기본값)
     * - IP: IP 주소 기반
     */
    val keyType: RateLimitKeyType = RateLimitKeyType.USER
)

/**
 * Rate Limit 키 타입
 */
enum class RateLimitKeyType {
    /**
     * 사용자 ID 기반 Rate Limit
     * 인증된 사용자에게 적용
     */
    USER,
    
    /**
     * IP 주소 기반 Rate Limit
     * 인증되지 않은 사용자나 특정 엔드포인트에 적용
     */
    IP
}

