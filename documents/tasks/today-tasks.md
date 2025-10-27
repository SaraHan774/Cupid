# 채팅 SDK 개발 - 오늘의 태스크 (3시간)

**날짜**: 2025년 10월 26일  
**목표**: Phase 1 개발 준비 완료 및 기본 구조 셋업 + 알림 시스템 이해

---

## 🎯 오늘의 핵심 목표

Phase 1 개발을 시작하기 위한 **기반 구축** + **알림 시스템 기획 이해**
- 백엔드 프로젝트 셋업 (Kotlin + Spring Boot)
- 기본 프로젝트 구조 생성
- 필수 의존성 설정 (FCM 포함)
- 간단한 Health Check API 구현
- 알림 시스템 아키텍처 이해

---

## 📚 사전 학습 (개발 전 필수)

### 알림 시스템 이해하기 (30분)
- [ ] **`notification-system-spec.md`** 읽기
  - 특히 섹션 3 (E2E 암호화와 알림) 필독
  - 섹션 4 (기술 아키텍처) 이해
  - 백그라운드 알림 시나리오 파악
- [ ] Silent Push + 복호화 방식 이해
- [ ] iOS 30초 제약 숙지

**왜 필요한가?**
- E2E 암호화와 푸시 알림을 함께 구현해야 함
- 일반적인 푸시 알림 방식과 완전히 다름
- 백엔드 설계 시 이 구조를 고려해야 함

---

## ⏰ 시간별 태스크 (3시간)

### 1시간차 (00:00 - 01:00): 백엔드 프로젝트 셋업

**목표**: Spring Boot 프로젝트 초기 설정 완료

#### Task 1-1: 프로젝트 생성 (15분)
- [ ] Spring Initializr로 프로젝트 생성
  - Kotlin
  - Spring Boot 3.2.x
  - Gradle (Kotlin DSL)
  - Java 17+
- [ ] IDE에서 프로젝트 열기
- [ ] 정상 빌드 확인

**사용 도구**: 
- Cursor로 프로젝트 생성 및 설정
- ChatGPT에 "Spring Boot 3.2 Kotlin Gradle 설정 예시" 질문

**체크포인트**: `./gradlew build` 성공

---

#### Task 1-2: 필수 의존성 추가 (20분)
- [ ] build.gradle.kts 수정
  - Spring Web
  - Spring WebSocket
  - Spring Data JPA
  - PostgreSQL Driver
  - Spring Data MongoDB
  - Redis (Lettuce)
  - JWT (jjwt)
  - Kotlin Coroutines
  - **Firebase Admin SDK (FCM용)** ⭐ 새로 추가
  - Signal Protocol (libsignal-server)

**사용 도구**:
- Cursor로 build.gradle.kts 편집
- ChatGPT: "Spring Boot Kotlin WebSocket + FCM 의존성 추천"

**참고 코드**:
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.postgresql:postgresql")
    
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // Firebase Admin SDK (FCM)
    implementation("com.google.firebase:firebase-admin:9.2.0")
    
    // Signal Protocol
    implementation("org.signal:libsignal-server:0.1.0")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

---

#### Task 1-3: 기본 프로젝트 구조 생성 (25분)
- [ ] 패키지 구조 생성
  ```
  com.yourcompany.chatsdk/
  ├── config/
  ├── controller/
  ├── service/
  │   └── notification/        ⭐ 알림 서비스
  ├── repository/
  ├── model/
  │   ├── entity/
  │   │   ├── notification/    ⭐ 알림 엔티티
  │   │   └── ...
  │   └── dto/
  │       ├── notification/    ⭐ 알림 DTO
  │       └── ...
  ├── websocket/
  ├── security/
  └── fcm/                     ⭐ FCM 관련 코드
  ```
- [ ] Application.kt 기본 설정
- [ ] application.yml 기본 설정 (포트, 데이터베이스, FCM 등)

**사용 도구**:
- Cursor: "Chat SDK + 알림 시스템을 위한 Spring Boot 프로젝트 구조 생성해줘"
- Cursor의 폴더/파일 자동 생성 기능 활용

**application.yml 예시**:
```yaml
server:
  port: 8080

spring:
  application:
    name: chat-sdk-server
    
  datasource:
    url: jdbc:postgresql://localhost:5432/chatsdk
    username: postgres
    password: postgres
    
  data:
    mongodb:
      uri: mongodb://localhost:27017/chatsdk
      
  redis:
    host: localhost
    port: 6379

# Firebase 설정
firebase:
  credentials-path: classpath:firebase-service-account.json
```

---

### 2시간차 (01:00 - 02:00): 기본 API 및 WebSocket 구조

**목표**: Health Check API와 WebSocket 기본 설정 완료

#### Task 2-1: Health Check API 구현 (15분)
- [ ] HealthController.kt 생성
- [ ] GET /health 엔드포인트 구현
- [ ] 서버 상태 정보 반환 (버전, 타임스탬프, FCM 상태)

**사용 도구**:
- Cursor: "Spring Boot Kotlin Health Check API 만들어줘 (FCM 상태 포함)"

**코드 예시**:
```kotlin
@RestController
@RequestMapping("/api/v1")
class HealthController(
    private val firebaseMessaging: FirebaseMessaging
) {
    
    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "timestamp" to System.currentTimeMillis(),
            "version" to "1.0.0",
            "services" to mapOf(
                "fcm" to checkFCMStatus()
            )
        )
    }
    
    private fun checkFCMStatus(): String {
        return try {
            // FCM 초기화 확인
            if (firebaseMessaging != null) "UP" else "DOWN"
        } catch (e: Exception) {
            "DOWN"
        }
    }
}
```

**체크포인트**: `curl http://localhost:8080/api/v1/health` 성공

---

#### Task 2-2: WebSocket 기본 설정 (30분)
- [ ] WebSocketConfig.kt 생성
- [ ] WebSocket 엔드포인트 설정 (/ws)
- [ ] 기본 핸들러 생성
- [ ] 연결/해제 로그 출력
- [ ] 연결 상태를 Redis에 저장 (온라인 상태 추적용)

**사용 도구**:
- ChatGPT: "Spring Boot Kotlin WebSocket STOMP + Redis 연결 상태 추적"
- Cursor로 코드 작성

**코드 예시**:
```kotlin
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val redisTemplate: RedisTemplate<String, String>
) : WebSocketMessageBrokerConfigurer {
    
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
    }
    
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .addInterceptors(ConnectionInterceptor(redisTemplate))
            .withSockJS()
    }
}

// 연결 상태 추적
class ConnectionInterceptor(
    private val redisTemplate: RedisTemplate<String, String>
) : HandshakeInterceptor {
    
    override fun beforeHandshake(...): Boolean {
        val userId = extractUserId(request)
        // Redis에 온라인 상태 저장 (5분 TTL)
        redisTemplate.opsForValue()
            .set("user:online:$userId", "true", 5, TimeUnit.MINUTES)
        return true
    }
}
```

**핵심 개념**:
- WebSocket 연결 시 Redis에 `user:online:{userId}` 저장
- FCM은 이 상태를 확인해서 오프라인일 때만 전송
- 5분 TTL로 자동 만료 (하트비트로 갱신)

---

#### Task 2-3: 간단한 메시지 에코 테스트 (15분)
- [ ] ChatController.kt 생성
- [ ] 메시지 수신하면 그대로 반환하는 에코 기능
- [ ] 테스트용 HTML 페이지 생성 (선택사항)

**사용 도구**:
- Cursor: "WebSocket 에코 테스트 컨트롤러 만들어줘"
- ChatGPT: "WebSocket 테스트용 HTML 클라이언트 코드"

---

### 3시간차 (02:00 - 03:00): 알림 관련 데이터 모델

**목표**: 알림 시스템을 위한 기본 데이터 모델 정의

#### Task 3-1: 기본 Entity 모델 생성 (30분)
- [ ] User.kt (PostgreSQL)
- [ ] Channel.kt (PostgreSQL)
- [ ] Message.kt (MongoDB)
- [ ] **UserNotificationSettings.kt** ⭐ 새로 추가
- [ ] **ChannelNotificationSettings.kt** ⭐ 새로 추가
- [ ] **FcmToken.kt** ⭐ 새로 추가

**사용 도구**:
- Cursor: "database-schema.md를 보고 알림 관련 Entity 만들어줘"
- 명세서 참조

**UserNotificationSettings.kt 예시**:
```kotlin
@Entity
@Table(name = "user_notification_settings")
data class UserNotificationSettings(
    @Id
    val userId: UUID,
    
    val enabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showPreview: Boolean = true,
    
    // 방해금지 모드
    val dndEnabled: Boolean = false,
    val dndStartTime: LocalTime = LocalTime.of(22, 0),
    val dndEndTime: LocalTime = LocalTime.of(8, 0),
    
    @Type(JsonType::class)
    val dndDays: List<Int> = listOf(1,2,3,4,5,6,7),
    
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

**FcmToken.kt 예시**:
```kotlin
@Entity
@Table(name = "fcm_tokens")
data class FcmToken(
    @Id
    val id: UUID = UUID.randomUUID(),
    
    @Column(nullable = false)
    val userId: UUID,
    
    @Column(nullable = false, unique = true, length = 500)
    val token: String,
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val deviceType: DeviceType, // IOS, ANDROID
    
    val deviceName: String? = null,
    val appVersion: String? = null,
    
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),
    var isActive: Boolean = true
)

enum class DeviceType {
    IOS, ANDROID
}
```

---

#### Task 3-2: DTO 모델 생성 (15분)
- [ ] 기본 Request/Response DTO
- [ ] MessageDto.kt
- [ ] ChannelDto.kt
- [ ] **NotificationSettingsDto.kt** ⭐ 새로 추가
- [ ] **FcmTokenDto.kt** ⭐ 새로 추가

**사용 도구**:
- Cursor: "Entity를 보고 DTO 만들어줘"

**NotificationSettingsDto.kt 예시**:
```kotlin
data class UpdateNotificationSettingsRequest(
    val enabled: Boolean?,
    val soundEnabled: Boolean?,
    val vibrationEnabled: Boolean?,
    val showPreview: Boolean?,
    val dndEnabled: Boolean?,
    val dndStartTime: String?, // "22:00"
    val dndEndTime: String?,   // "08:00"
    val dndDays: List<Int>?
)

data class NotificationSettingsResponse(
    val userId: String,
    val enabled: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val showPreview: Boolean,
    val dndEnabled: Boolean,
    val dndStartTime: String,
    val dndEndTime: String,
    val dndDays: List<Int>,
    val createdAt: String,
    val updatedAt: String
)
```

---

#### Task 3-3: API 문서 및 계획 정리 (15분)
- [ ] README.md 업데이트
- [ ] API.md 파일 생성
- [ ] 구현된 엔드포인트 문서화
  - GET /api/v1/health
  - WS /ws
- [ ] **알림 API 목록 추가 (미구현 표시)**
  - GET /api/v1/notifications/settings
  - PUT /api/v1/notifications/settings
  - POST /api/v1/notifications/fcm-token
- [ ] Phase 1 구현 계획 명시

**사용 도구**:
- Claude(나): "오늘 구현한 내용 + 알림 시스템 계획을 API 문서로 정리해줘"
- Cursor로 마크다운 파일 작성

---

## 📝 각 시간 종료 시 체크리스트

### 1시간 후 체크
- [ ] 프로젝트가 정상적으로 빌드되는가?
- [ ] 모든 의존성이 정상적으로 로드되는가? (Firebase Admin SDK 포함)
- [ ] application.yml이 올바르게 설정되었는가?
- [ ] 알림 시스템 구조를 이해했는가?

### 2시간 후 체크
- [ ] Health Check API가 정상 작동하는가?
- [ ] WebSocket 연결이 성공하는가?
- [ ] Redis에 온라인 상태가 저장되는가?
- [ ] 에코 메시지가 정상적으로 동작하는가?

### 3시간 후 체크
- [ ] 기본 Entity 모델이 생성되었는가? (알림 테이블 포함)
- [ ] DTO가 정의되었는가?
- [ ] API 문서가 작성되었는가?
- [ ] Phase 1 구현 계획이 명확한가?

---

## 🛠️ AI 도구 활용 전략

### Cursor 사용
- **주 용도**: 실제 코드 작성, 파일 생성
- **팁**: 
  - `.cursorrules` 파일에 프로젝트 컨벤션 정의
  - 명세서를 컨텍스트로 제공 (특히 notification-system-spec.md)
  - "명세서 기반으로 XXX 만들어줘" 형태로 요청

### ChatGPT 사용
- **주 용도**: 빠른 정보 검색, 예제 코드
- **언제**: 
  - 막힐 때 즉시 질문
  - 의존성 버전 확인
  - FCM 관련 질문 (많이 검색됨)
  - 에러 메시지 해결

### Claude (나)
- **주 용도**: 
  - 전체적인 방향 검토
  - 복잡한 설계 논의 (특히 E2E + 알림)
  - 코드 리뷰
- **언제**:
  - 시작 전 계획 확인
  - 막힐 때 전체 맥락 공유
  - 3시간 후 리뷰
  - 알림 시스템 관련 질문

---

## 🚨 예상 이슈 및 대응

### 이슈 1: 의존성 버전 충돌
**증상**: 빌드 실패, 의존성 해결 불가  
**대응**: ChatGPT에 에러 로그 붙여넣고 해결책 받기

### 이슈 2: WebSocket 연결 실패
**증상**: 브라우저에서 연결 안 됨  
**대응**: 
1. CORS 설정 확인
2. 엔드포인트 경로 확인
3. ChatGPT: "Spring WebSocket CORS 설정"

### 이슈 3: 데이터베이스 연결 실패
**증상**: 서버 시작 시 에러  
**대응**:
1. Docker로 PostgreSQL, MongoDB, Redis 실행
2. application.yml 설정 확인

### 이슈 4: Firebase Admin SDK 초기화 실패 ⭐ 새로 추가
**증상**: FCM 관련 에러  
**대응**:
1. Firebase Console에서 서비스 계정 키 다운로드
2. `firebase-service-account.json` 파일 경로 확인
3. ChatGPT: "Firebase Admin SDK Kotlin 초기화 에러"

---

## 📦 사전 준비사항

### 로컬 환경 준비
```bash
# Docker로 필요한 서비스 실행
docker run -d --name postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:15
docker run -d --name mongodb -p 27017:27017 mongo:7
docker run -d --name redis -p 6379:6379 redis:7
```

### IDE 설정
- [ ] Kotlin 플러그인 설치
- [ ] Cursor 설정 확인
- [ ] 명세서 파일 열어두기 (특히 notification-system-spec.md)

### Firebase 설정 (나중에 필요)
- [ ] Firebase Console에서 프로젝트 생성
- [ ] 서비스 계정 키 다운로드
- [ ] `src/main/resources/firebase-service-account.json`에 저장

---

## 🎯 3시간 후 예상 결과

### 완성된 것들
1. ✅ Spring Boot 프로젝트 기본 구조 (알림 시스템 포함)
2. ✅ Health Check API (FCM 상태 체크 포함)
3. ✅ WebSocket 기본 설정 + Redis 연결 상태 추적
4. ✅ 기본 Entity 모델 (User, Channel, Message + 알림 3개)
5. ✅ 기본 DTO (알림 DTO 포함)
6. ✅ API 문서 시작 (알림 API 계획 포함)
7. ✅ 알림 시스템 아키텍처 이해

### Git Commit 내역 (예상)
```
1. feat: 초기 프로젝트 셋업 및 의존성 추가 (FCM 포함)
2. feat: Health Check API 구현 (FCM 상태 체크)
3. feat: WebSocket 기본 설정 + Redis 연결 상태 추적
4. feat: 기본 Entity 및 DTO 모델 추가 (알림 관련 포함)
5. docs: README 및 API 문서 작성 (알림 시스템 계획)
```

---

## 💡 내일 할 일 미리보기

### Phase 1 우선순위 (알림 반영)
1. **인증 및 기본 API** (2-3일)
   - JWT 인증 구현
   - User Repository 및 Service
   - 회원가입/로그인 API
   - FCM 토큰 등록 API ⭐

2. **Signal Protocol 통합** (3-4일)
   - 키 생성 및 관리
   - 메시지 암호화/복호화

3. **WebSocket 실시간 메시징** (3-4일)
   - 메시지 전송/수신
   - Redis Pub/Sub
   - 읽음 상태 처리

4. **알림 시스템 (FCM)** (3-4일) ⭐ 새로 추가
   - FCM 토큰 관리 API
   - 알림 설정 API (전역, 채널별)
   - Silent Push 전송 로직
   - 온라인 상태 확인 로직

5. **채널 관리** (2-3일)
   - 1:1 채팅 생성
   - 채널 목록 조회

**총 예상 기간**: 2-3주

---

## 📖 학습 자료

### 꼭 읽어야 할 문서들
1. **notification-system-spec.md** (필독!)
   - E2E 암호화와 알림의 절충안
   - Silent Push 동작 방식
   - iOS/Android 구현 차이

2. **database-schema.md**
   - 알림 테이블 구조
   - Redis 키 설계

3. **chat-sdk-spec.md**
   - 전체 기능 명세
   - Phase별 계획

### 참고할 기술 문서
- [Firebase Admin SDK - Kotlin](https://firebase.google.com/docs/admin/setup)
- [Signal Protocol](https://signal.org/docs/)
- [Spring WebSocket](https://docs.spring.io/spring-framework/reference/web/websocket.html)

---

## 메모

- 오늘은 **기반 구축 + 알림 시스템 이해**에 집중
- 알림 시스템은 일반적인 방식과 다름 (E2E 암호화 때문)
- Silent Push + 백그라운드 복호화 개념 숙지 필수
- 완벽하게 만들려고 하지 말고 **빠르게 동작하는 것** 우선
- 막히면 즉시 AI에게 질문
- 3시간 후 Claude에게 코드 리뷰 + 알림 아키텍처 검증 요청할 것

---

## 🔑 핵심 포인트

### E2E 암호화 + 푸시 알림 = 특별한 설계 필요
```
일반 앱: 서버가 메시지 내용을 알고 FCM에 직접 전송
우리 앱: 서버는 암호화된 내용만 알고, 
       클라이언트가 백그라운드에서 복호화 후 알림 생성
```

### 온라인 상태 추적이 핵심
```
WebSocket 연결됨 → Redis에 저장 (5분 TTL)
메시지 전송 시:
  1. Redis에서 온라인 상태 확인
  2. 온라인이면 WebSocket으로만 전송
  3. 오프라인이면 FCM도 전송
```

이 구조를 이해하면 나머지 구현이 명확해져요!