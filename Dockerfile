# Multi-stage build for Spring Boot application
# Stage 1: Build stage
# AWS ECS Fargate는 x86_64 아키텍처를 사용하므로 플랫폼 명시
FROM --platform=linux/amd64 gradle:8.5-jdk17-alpine AS build

# 작업 디렉토리 설정
WORKDIR /app

# Gradle wrapper 및 설정 파일 복사
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./

# Gradle wrapper 실행 권한 부여
RUN chmod +x ./gradlew

# 의존성 다운로드 (캐시 활용)
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사
COPY src ./src

# 애플리케이션 빌드 (JAR 파일 생성)
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime stage
# AWS ECS Fargate는 x86_64 아키텍처를 사용하므로 플랫폼 명시
FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine

# Health check를 위한 wget 설치 및 CA 인증서 도구 설치
RUN apk add --no-cache wget ca-certificates

# 작업 디렉토리 설정
WORKDIR /app

# AWS RDS/DocumentDB CA 인증서 복사 및 설치
COPY rds-combined-ca-bundle.pem /tmp/rds-ca-bundle.pem
COPY import-certs.sh /tmp/import-certs.sh
RUN chmod +x /tmp/import-certs.sh && \
    /tmp/import-certs.sh && \
    rm /tmp/import-certs.sh

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# Health check 설정 (Spring Boot Actuator 사용)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]

