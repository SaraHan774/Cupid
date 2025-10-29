package com.august.cupid.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

/**
 * Spring MVC 설정
 *
 * 기능:
 * - 정적 파일 서빙 (프로필 이미지 등)
 * - CORS 설정
 */
@Configuration
class WebMvcConfig(
    @Value("\${storage.type:local}") private val storageType: String,
    @Value("\${storage.local.base-path:uploads}") private val localBasePath: String
) : WebMvcConfigurer {

    /**
     * 정적 리소스 핸들러 설정
     *
     * 로컬 스토리지 모드일 때 uploads 디렉토리의 파일을 /uploads/** 경로로 서빙
     */
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (storageType == "local") {
            val absolutePath = Paths.get(localBasePath).toAbsolutePath().toString()

            registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:$absolutePath/")
                .setCachePeriod(3600) // 1시간 캐시
        }
    }
}
