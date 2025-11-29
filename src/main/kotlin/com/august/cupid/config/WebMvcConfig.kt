package com.august.cupid.config

import com.august.cupid.security.CurrentUserArgumentResolver
import com.august.cupid.security.RateLimitInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebMvcConfig(
    @Value("\${storage.type:local}") private val storageType: String,
    @Value("\${storage.local.base-path:uploads}") private val localBasePath: String,
    private val rateLimitInterceptor: RateLimitInterceptor,
    private val currentUserArgumentResolver: CurrentUserArgumentResolver
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (storageType == "local") {
            val absolutePath = Paths.get(localBasePath).toAbsolutePath().toString()
            registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:$absolutePath/")
                .setCachePeriod(3600)
        }
    }

    /**
     * @CurrentUser 어노테이션 처리를 위한 ArgumentResolver 등록
     */
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentUserArgumentResolver)
    }

    /**
     * Rate Limit 인터셉터 등록
     * @RateLimit 어노테이션이 있는 메서드에 Rate Limit 적용
     */
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/v1/health",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            )
    }
}


