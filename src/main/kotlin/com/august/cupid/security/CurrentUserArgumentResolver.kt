package com.august.cupid.security

import com.august.cupid.exception.UnauthorizedException
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.*

/**
 * @CurrentUser 어노테이션을 처리하는 ArgumentResolver
 *
 * SecurityContext에서 인증된 사용자의 UUID를 추출하여 컨트롤러 메서드에 주입합니다.
 * 인증되지 않은 요청이나 유효하지 않은 인증 정보인 경우 UnauthorizedException을 발생시킵니다.
 */
@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentUser::class.java) &&
                parameter.parameterType == UUID::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("인증 정보가 없습니다")

        val principal = authentication.principal
            ?: throw UnauthorizedException("인증 정보를 찾을 수 없습니다")

        return when (principal) {
            is UUID -> principal
            is String -> {
                try {
                    UUID.fromString(principal)
                } catch (e: IllegalArgumentException) {
                    throw UnauthorizedException("유효하지 않은 인증 정보입니다")
                }
            }
            else -> throw UnauthorizedException("지원하지 않는 인증 타입입니다")
        }
    }
}
