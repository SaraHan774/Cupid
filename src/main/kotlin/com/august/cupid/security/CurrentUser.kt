package com.august.cupid.security

import org.springframework.security.core.annotation.AuthenticationPrincipal

/**
 * 현재 인증된 사용자의 ID를 주입하는 어노테이션
 *
 * 사용법:
 * ```
 * @GetMapping("/profile")
 * fun getProfile(@CurrentUser userId: UUID): ResponseEntity<...>
 * ```
 *
 * 인증되지 않은 요청이나 유효하지 않은 토큰의 경우 UnauthorizedException이 발생합니다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@AuthenticationPrincipal
annotation class CurrentUser
