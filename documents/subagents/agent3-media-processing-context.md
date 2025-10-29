# Agent 3: Media & Image Processing Expert 📸

**역할**: 이미지 최적화 및 미디어 처리 전문가  
**담당 작업**: Task 3 - 사용자 프로필 관리 (프로필 이미지 업로드 및 최적화)

---

## 📋 프로젝트 개요

**프로젝트명**: Cupid - 레즈비언 소개팅 앱 채팅 SDK  
**기술 스택**: Kotlin + Spring Boot 3.5.7  
**스토리지**: AWS S3 (운영) / 로컬 (개발)  
**데이터베이스**: PostgreSQL  
**현재 단계**: Phase 1 MVP 완성을 위한 기능 보완

---

## ✅ 현재 구현 상태

### 완료된 기능
- ✅ `User` 엔티티에 프로필 이미지 필드 존재:
  - `profileImageUrl`
  - `profileThumbnailUrl`
  - `profileImageBlurhash`
  - `profileImageMetadata`
- ✅ 데이터베이스 스키마 준비 완료

### 미구현 기능
- ❌ 프로필 사진 업로드 API 미구현
- ❌ 이미지 최적화 서비스 미구현
- ❌ 다중 해상도 생성 미구현
- ❌ WebP 변환 미구현
- ❌ BlurHash 생성 미구현
- ❌ S3/CDN 통합 미구현

---

## 🔑 핵심 엔티티 및 구조

### User Entity (프로필 이미지 필드)
```kotlin
package com.august.cupid.model.entity

@Entity
@Table(name = "users")
data class User(
    @Id
    val id: UUID = UUID.randomUUID(),
    
    @Column(name = "username", nullable = false, unique = true, length = 50)
    val username: String,
    
    // 프로필 이미지 최적화
    @Column(name = "profile_image_url", length = 500)
    val profileImageUrl: String? = null,
    
    @Column(name = "profile_thumbnail_url", length = 500)
    val profileThumbnailUrl: String? = null,
    
    @Column(name = "profile_image_blurhash", length = 50)
    val profileImageBlurhash: String? = null,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_image_metadata", columnDefinition = "jsonb")
    val profileImageMetadata: Map<String, Any>? = null,
    
    // ... 기타 필드
)
```

---

## 📦 의존성 (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // 이미지 처리 (선택 사항 - 추가 필요)
    // Thumbnailator 또는 Java ImageIO 사용 가능
    // 또는 Cloudinary, Imgix 같은 외부 서비스 활용
}
```

**추가 필요한 라이브러리 (제안)**:
```kotlin
// Option 1: Thumbnailator
implementation("net.coobird:thumbnailator:0.4.20")

// Option 2: ImageIO (Java 표준, 추가 라이브러리 불필요)
// 이미 포함되어 있음

// Option 3: Kotlin Coroutines for parallel processing
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

// Option 4: AWS S3 (운영 환경)
implementation("com.amazonaws:aws-java-sdk-s3:1.12.470")
```

---

## 🗄️ 데이터베이스 스키마

### users 테이블 (프로필 이미지 관련 컬럼)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    
    -- 프로필 이미지 최적화
    profile_image_url VARCHAR(500),
    profile_thumbnail_url VARCHAR(500),
    profile_image_blurhash VARCHAR(50),
    profile_image_metadata JSONB,
    
    -- ... 기타 컬럼
);
```

---

## 🎯 구현해야 할 작업

### Task 3.1: 이미지 업로드 API (2시간)

**요구사항**:
- [ ] `POST /api/v1/users/profile-image` - 프로필 사진 업로드
- [ ] 파일 검증 (크기, 형식): 최대 10MB, JPEG/PNG/WebP
- [ ] MultipartFile 처리
- [ ] 기본 이미지 저장 (S3 또는 로컬)
- [ ] 에러 처리 (잘못된 형식, 크기 초과)

### Task 3.2: 이미지 최적화 서비스 (2-3시간)

**요구사항**:
- [ ] `ImageOptimizationService` 생성
- [ ] 다중 해상도 생성:
  - Original: 800x800 (프로필 상세)
  - Large: 400x400 (프로필 팝업)
  - Medium: 200x200 (채팅 헤더)
  - Small: 100x100 (채팅 목록/아바타)
- [ ] WebP 포맷 변환 + JPEG 폴백
- [ ] BlurHash 생성 (서버 측 또는 클라이언트 제공받기)
- [ ] 메타데이터 생성 및 저장 (파일 크기, 해상도, 포맷)
- [ ] CDN 업로드 (S3 + CloudFront) - 운영 환경

**성능 목표**:
- 처리 시간: < 2초
- 이미지 품질: 각 해상도별 최적화
- 압축률: 최대 효율

---

## 📝 기존 코드 패턴

### REST Controller 패턴
```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {
    @PostMapping("/{userId}/profile-image")
    fun uploadProfileImage(
        @PathVariable userId: UUID,
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal currentUserId: UUID
    ): ApiResponse<ProfileImageResponse> {
        // 권한 확인
        require(currentUserId == userId) { "본인의 프로필만 수정할 수 있습니다" }
        
        // 파일 검증
        require(file.size <= 10 * 1024 * 1024) { "파일 크기는 10MB를 초과할 수 없습니다" }
        require(file.contentType?.startsWith("image/") == true) { "이미지 파일만 업로드 가능합니다" }
        
        return userService.uploadProfileImage(userId, file)
    }
}
```

### Service 패턴
```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val imageOptimizationService: ImageOptimizationService,
    private val storageService: StorageService
) {
    fun uploadProfileImage(userId: UUID, file: MultipartFile): ApiResponse<ProfileImageResponse> {
        // 1. 이미지 최적화
        val optimizedImages = imageOptimizationService.processImage(file)
        
        // 2. 스토리지에 업로드
        val uploadedUrls = storageService.uploadImages(optimizedImages)
        
        // 3. 메타데이터 생성
        val metadata = mapOf(
            "originalSize" to file.size,
            "format" to optimizedImages.format,
            // ...
        )
        
        // 4. 사용자 정보 업데이트
        val user = userRepository.findById(userId).orElseThrow()
        // ... 업데이트 로직
        
        return ApiResponse.success(ProfileImageResponse(uploadedUrls))
    }
}
```

---

## 🔧 설정 파일 (application.yml)

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  
  datasource:
    url: jdbc:postgresql://localhost:5433/chatsdk
    username: postgres
    password: postgres

# 이미지 처리 설정 (추가 필요)
image:
  processing:
    enabled: true
    max-width: 800
    max-height: 800
    quality: 85
    formats:
      - webp
      - jpeg
    
  storage:
    type: local  # local 또는 s3
    s3:
      bucket: cupid-profile-images
      region: ap-northeast-2
      cdn-url: https://cdn.cupid.example.com
```

---

## 📚 참고 문서

1. **스펙 문서**: `documents/specifications/chat-sdk-spec.md` 섹션 1.1
2. **데이터베이스 스키마**: `documents/specifications/database-schema.md` 시나리오 1, 15
3. **작업 목록**: `documents/tasks/today-tasks.md` - Task 3

---

## 💡 구현 가이드

### 구현 순서
1. 파일 업로드 엔드포인트 구현 (기본 검증)
2. `ImageOptimizationService` 생성 (다중 해상도 생성)
3. 이미지 리사이징 및 포맷 변환
4. BlurHash 생성 (서버 측 또는 클라이언트에서 받기)
5. 스토리지 서비스 통합 (로컬/S3)
6. 메타데이터 생성 및 저장
7. 병렬 처리 최적화 (Kotlin Coroutines)

### 이미지 처리 전략
- **Original**: 원본 유지 (최대 800x800)
- **Large**: 400x400 (고품질)
- **Medium**: 200x200 (중간 품질)
- **Small**: 100x100 (저품질, 빠른 로딩)

### WebP 변환 전략
- WebP 지원 확인 후 변환
- 브라우저 호환성을 위해 JPEG 폴백 제공
- 압축률 최적화 (품질 85%)

### BlurHash 생성
- 클라이언트 측에서 생성 권장 (서버 부하 감소)
- 또는 Java/Kotlin 라이브러리 사용
- 해상도: 32x32 권장

---

## 🎯 MEGA PROMPT (시작 시 사용)

```
You are implementing a complete profile image management system with optimization.

Context:
- Backend: Kotlin + Spring Boot 3.5.7
- Storage: AWS S3 (or local for dev)
- Database: PostgreSQL with profile image fields already created
- Goal: Multi-resolution images + WebP conversion + BlurHash

Requirements:
1. REST API: POST /api/v1/users/{userId}/profile-image
2. Generate 4 resolutions:
   - Original: 800x800 (profile detail)
   - Large: 400x400 (profile popup)
   - Medium: 200x200 (chat header)
   - Small: 100x100 (chat list/avatar)
3. Convert to WebP format with JPEG fallback
4. Generate or receive BlurHash for placeholder
5. Upload to S3/CDN
6. Store metadata in PostgreSQL

Please provide:
1. ProfileImageService.kt with image processing logic
2. ImageOptimizationService.kt for multi-resolution generation
3. S3Service.kt for cloud storage (or LocalStorageService for dev)
4. REST endpoint implementation
5. Gradle dependencies needed (image processing libraries)
6. Configuration for S3/local storage
7. Error handling (invalid format, size limits)
8. Cleanup of old images

Technical details needed:
- Use Thumbnailator or ImageIO for processing
- WebP conversion strategy
- Parallel processing with Kotlin coroutines
- Progress tracking for upload

Include performance metrics:
- Target: < 2s for complete processing
- Image quality settings for each resolution
- Compression ratios
```

