package com.august.cupid.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebMvcConfig(
    @Value("\${storage.type:local}") private val storageType: String,
    @Value("\${storage.local.base-path:uploads}") private val localBasePath: String
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (storageType == "local") {
            val absolutePath = Paths.get(localBasePath).toAbsolutePath().toString()
            registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:$absolutePath/")
                .setCachePeriod(3600)
        }
    }
}


