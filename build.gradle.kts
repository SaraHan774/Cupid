plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"  // JPA 엔티티를 위한 no-arg 생성자 자동 생성
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "com.august"
version = "0.0.1-SNAPSHOT"
description = "Cupid"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    
    // Actuator & Metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // PostgreSQL Driver
    implementation("org.postgresql:postgresql")
    
    // Rate Limiting (Bucket4j)
    implementation("com.bucket4j:bucket4j-core:8.7.0")
    implementation("com.bucket4j:bucket4j-redis:8.7.0")
    
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    
    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")
    
    // Signal Protocol for end-to-end encryption
    implementation("org.whispersystems:signal-protocol-java:2.8.1")

    // Image Processing
    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("com.google.code.gson:gson:2.10.1")

    // AWS S3
    implementation("software.amazon.awssdk:s3:2.20.26")
    implementation("software.amazon.awssdk:core:2.20.26")

    // BlurHash (optional - for server-side generation)
    implementation("com.vanniktech:blurhash:0.2.0")
    
    // Apache Commons Pool2 for Redis connection pooling
    implementation("org.apache.commons:commons-pool2:2.12.0")

    // API Documentation (SpringDoc OpenAPI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("com.h2database:h2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.springframework:spring-websocket")
    testImplementation("org.springframework:spring-messaging")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Embedded MongoDB for testing
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:4.11.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ============================================
// OpenAPI Documentation Generation
// ============================================

openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs")
    outputDir.set(layout.buildDirectory.dir("openapi"))
    outputFileName.set("openapi.json")
    waitTimeInSeconds.set(60)
}

// Task to generate wiki documentation from OpenAPI spec
tasks.register("generateWikiDocs") {
    group = "documentation"
    description = "Generates GitHub Wiki Markdown from OpenAPI specification"
    dependsOn("generateOpenApiDocs")

    doLast {
        val openApiFile = layout.buildDirectory.file("openapi/openapi.json").get().asFile
        val wikiDir = file("docs/wiki")

        if (!openApiFile.exists()) {
            throw GradleException("OpenAPI spec not found at ${openApiFile.absolutePath}. Run generateOpenApiDocs first.")
        }

        // Create wiki directory structure
        wikiDir.mkdirs()
        file("docs/wiki/endpoints").mkdirs()

        println("Wiki documentation directory prepared at: ${wikiDir.absolutePath}")
        println("Run the wiki generator script: ./scripts/generate-wiki.sh")
    }
}

// Task to validate OpenAPI spec
tasks.register("validateOpenApi") {
    group = "documentation"
    description = "Validates the generated OpenAPI specification"
    dependsOn("generateOpenApiDocs")

    doLast {
        val openApiFile = layout.buildDirectory.file("openapi/openapi.json").get().asFile
        if (!openApiFile.exists()) {
            throw GradleException("OpenAPI spec not found")
        }

        val content = openApiFile.readText()
        if (!content.contains("\"openapi\"")) {
            throw GradleException("Invalid OpenAPI spec: missing openapi version")
        }
        if (!content.contains("\"paths\"")) {
            throw GradleException("Invalid OpenAPI spec: missing paths")
        }

        println("OpenAPI specification validated successfully")
    }
}
