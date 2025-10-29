# Agent 1: Signal Protocol & E2E Encryption Expert 🔐

**역할**: Signal Protocol과 E2E 암호화 전문가  
**담당 작업**: Task 1 - E2E 암호화 (Signal Protocol) 구현

---

## 📋 프로젝트 개요

**프로젝트명**: Cupid - 레즈비언 소개팅 앱 채팅 SDK  
**기술 스택**: Kotlin + Spring Boot 3.5.7  
**데이터베이스**: PostgreSQL (관계형 데이터), MongoDB (메시지), Redis (캐시/실시간 상태)  
**현재 단계**: Phase 1 MVP 완성을 위한 기능 보완

---

## ✅ 현재 구현 상태

### 완료된 기능
- ✅ `UserKeys` 엔티티 존재 (키 저장 구조)
- ✅ `Message.encryptedContent` 필드 존재
- ✅ Signal Protocol 라이브러리 의존성 추가 완료 (`signal-protocol-java:2.8.1`)

### 미구현 기능
- ❌ 실제 Signal Protocol 통합 서비스 미구현
- ❌ 키 생성/교환/암호화 로직 미구현
- ❌ UserKeys 관리 API 미구현
- ❌ 메시지 암호화/복호화 통합 미구현

---

## 🔑 핵심 엔티티 및 구조

### UserKeys 엔티티 (PostgreSQL)
```kotlin
package com.august.cupid.model.entity

@Entity
@Table(name = "user_keys")
data class UserKeys(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "identity_key", nullable = false, columnDefinition = "TEXT")
    val identityKey: String,

    @Column(name = "signed_pre_key", nullable = false, columnDefinition = "TEXT")
    val signedPreKey: String,

    @Column(name = "pre_key_signature", nullable = false, columnDefinition = "TEXT")
    val preKeySignature: String,

    @Column(name = "one_time_pre_key_id")
    val oneTimePreKeyId: Int? = null,

    @Column(name = "one_time_pre_key", columnDefinition = "TEXT")
    val oneTimePreKey: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null
)
```

### Message 엔티티 (MongoDB) - 암호화 필드
```kotlin
package com.august.cupid.model.entity

@Document(collection = "messages")
data class Message(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Field("channel_id")
    val channelId: UUID,

    @Field("sender_id")
    val senderId: UUID,

    // E2E 암호화된 내용
    @Field("encrypted_content")
    val encryptedContent: String,

    @Field("message_type")
    val messageType: MessageType = MessageType.TEXT,

    // ... 기타 필드
)
```

---

## 📦 의존성 (build.gradle.kts)

```kotlin
dependencies {
    // Signal Protocol for end-to-end encryption
    implementation("org.whispersystems:signal-protocol-java:2.8.1")
    
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
}
```

---

## 🗄️ 데이터베이스 스키마

### user_keys 테이블 (PostgreSQL)
```sql
CREATE TABLE user_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    identity_key TEXT NOT NULL,
    signed_pre_key TEXT NOT NULL,
    pre_key_signature TEXT NOT NULL,
    one_time_pre_key_id INTEGER,
    one_time_pre_key TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    
    INDEX idx_user_keys_user_id (user_id),
    INDEX idx_user_keys_expires_at (expires_at)
);
```

---

## 🎯 구현해야 할 작업

### Task 1.1: SignalProtocolService 생성 (3-4시간)
- [ ] `SignalProtocolService` 클래스 생성
- [ ] libsignal-java 라이브러리 통합
- [ ] 키 쌍 생성 메서드 (Identity Key, Signed Pre Key, One-Time Pre Keys)
- [ ] X3DH 키 교환 로직 구현
- [ ] Double Ratchet 메시지 암호화/복호화
- [ ] 키 백업/복구 기능 (선택사항)

### Task 1.2: UserKeys 관리 API (2시간)
- [ ] `POST /api/v1/users/keys` - 공개키 등록 API
- [ ] `GET /api/v1/users/{userId}/keys` - 공개키 조회 API
- [ ] `PUT /api/v1/users/keys` - 키 갱신 API

### Task 1.3: 메시지 암호화 통합 (2시간)
- [ ] `MessageService`에서 자동 암호화 적용
- [ ] 클라이언트 요청 시 암호화된 내용만 저장
- [ ] 복호화는 클라이언트 측에서 처리 (서버는 암호화된 데이터만 저장)

---

## 📝 기존 코드 패턴

### Repository 패턴
```kotlin
@Repository
interface UserKeysRepository : JpaRepository<UserKeys, UUID> {
    fun findByUserId(userId: UUID): UserKeys?
    fun findByUserIdAndExpiresAtAfter(userId: UUID, now: LocalDateTime): UserKeys?
}
```

### Service 패턴
```kotlin
@Service
class SomeService(
    private val repository: SomeRepository
) {
    fun doSomething(): Result<SomeData> {
        // 비즈니스 로직
        return Result.success(data)
    }
}
```

### API Response 패턴
```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)

// 사용 예시
return ApiResponse.success(data, "성공 메시지")
return ApiResponse.error("에러 메시지")
```

---

## 🔧 설정 파일 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/chatsdk
    username: postgres
    password: postgres
  
  data:
    mongodb:
      uri: mongodb://localhost:27017/chatsdk
```

---

## 📚 참고 문서

1. **스펙 문서**: `documents/specifications/chat-sdk-spec.md` 섹션 1.0
2. **데이터베이스 스키마**: `documents/specifications/database-schema.md` - user_keys 섹션
3. **작업 목록**: `documents/tasks/today-tasks.md` - Task 1

---

## 💡 구현 가이드

### 구현 순서
1. `SignalProtocolService.kt` 생성 및 기본 구조 작성
2. 키 생성 로직 구현 (Identity Key, Pre Keys)
3. 키 저장/조회 로직 (Repository 통합)
4. X3DH 키 교환 구현
5. 메시지 암호화/복호화 구현
6. REST API 엔드포인트 구현
7. `MessageService`와 통합

### 고려사항
- 키는 Base64로 인코딩하여 저장
- One-Time Pre Keys는 만료 시간 설정 (`expiresAt`)
- 키 교환 시 보안 검증 필요
- 복호화는 클라이언트 측에서 수행 (서버는 암호화된 데이터만 관리)

---

## 🎯 MEGA PROMPT (시작 시 사용)

```
You are a Signal Protocol and E2E encryption expert for a chat SDK project.

Background:
- Backend: Kotlin/Spring Boot 3.5.7
- Current State: UserKeys entity exists, Signal Protocol library added (signal-protocol-java:2.8.1), but integration not implemented
- Goal: Implement full E2E encryption with Signal Protocol

Your expertise:
- Signal Protocol implementation
- X3DH key exchange
- Double Ratchet algorithm
- Key management and storage
- Security best practices

Task: Implement complete Signal Protocol integration including:
1. SignalProtocolService with libsignal-java integration
2. Key generation (Identity Key, Signed Pre Key, One-Time Pre Keys)
3. X3DH key exchange
4. Message encryption/decryption
5. UserKeys management REST API
6. Integration with MessageService

Please provide:
1. Complete SignalProtocolService.kt implementation
2. Key generation and management logic
3. X3DH key exchange implementation
4. Double Ratchet message encryption/decryption
5. REST API endpoints for key management
6. Integration code for MessageService
7. Error handling and validation
8. Unit tests examples

Use existing patterns:
- Repository pattern for database access
- Service layer for business logic
- ApiResponse for API responses
- Kotlin coroutines for async operations (if needed)
```

