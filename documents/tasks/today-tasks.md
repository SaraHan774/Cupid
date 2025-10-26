# 채팅 SDK 개발 - 오늘의 태스크 (3시간)

**날짜**: 2025년 10월 26일  
**목표**: Phase 1 개발 준비 완료 및 기본 구조 셋업

---

## 🎯 오늘의 핵심 목표

Phase 1 개발을 시작하기 위한 **기반 구축**
- 백엔드 프로젝트 셋업 (Kotlin + Spring Boot)
- 기본 프로젝트 구조 생성
- 필수 의존성 설정
- 간단한 Health Check API 구현

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

**사용 도구**:
- Cursor로 build.gradle.kts 편집
- ChatGPT: "Spring Boot Kotlin WebSocket 의존성 추천"

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
  ├── repository/
  ├── model/
  │   ├── entity/
  │   └── dto/
  ├── websocket/
  └── security/
  ```
- [ ] Application.kt 기본 설정
- [ ] application.yml 기본 설정 (포트, 데이터베이스 등)

**사용 도구**:
- Cursor: "Chat SDK를 위한 Spring Boot 프로젝트 구조 생성해줘"
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
```

---

### 2시간차 (01:00 - 02:00): 기본 API 및 WebSocket 구조

**목표**: Health Check API와 WebSocket 기본 설정 완료

#### Task 2-1: Health Check API 구현 (15분)
- [ ] HealthController.kt 생성
- [ ] GET /health 엔드포인트 구현
- [ ] 서버 상태 정보 반환 (버전, 타임스탬프)

**사용 도구**:
- Cursor: "Spring Boot Kotlin Health Check API 만들어줘"

**코드 예시**:
```kotlin
@RestController
@RequestMapping("/api/v1")
class HealthController {
    
    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "timestamp" to System.currentTimeMillis(),
            "version" to "1.0.0"
        )
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

**사용 도구**:
- ChatGPT: "Spring Boot Kotlin WebSocket STOMP 설정 예시"
- Cursor로 코드 작성

**코드 예시**:
```kotlin
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
    }
    
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .withSockJS()
    }
}
```

---

#### Task 2-3: 간단한 메시지 에코 테스트 (15분)
- [ ] ChatController.kt 생성
- [ ] 메시지 수신하면 그대로 반환하는 에코 기능
- [ ] 테스트용 HTML 페이지 생성 (선택사항)

**사용 도구**:
- Cursor: "WebSocket 에코 테스트 컨트롤러 만들어줘"
- ChatGPT: "WebSocket 테스트용 HTML 클라이언트 코드"

---

### 3시간차 (02:00 - 03:00): 데이터 모델 및 문서화

**목표**: 기본 데이터 모델 정의 및 문서 작성

#### Task 3-1: 기본 Entity 모델 생성 (30분)
- [ ] User.kt (PostgreSQL)
- [ ] Channel.kt (PostgreSQL)
- [ ] Message.kt (MongoDB)

**사용 도구**:
- Cursor: "명세서를 보고 User, Channel, Message Entity 만들어줘"
- 명세서 참조

**User.kt 예시**:
```kotlin
@Entity
@Table(name = "users")
data class User(
    @Id
    val id: String = UUID.randomUUID().toString(),
    
    @Column(nullable = false, unique = true)
    val username: String,
    
    @Column(nullable = false)
    val passwordHash: String,
    
    @Column(nullable = false)
    var publicKey: String? = null,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    var lastSeenAt: LocalDateTime? = null
)
```

---

#### Task 3-2: DTO 모델 생성 (15분)
- [ ] 기본 Request/Response DTO
- [ ] MessageDto.kt
- [ ] ChannelDto.kt

**사용 도구**:
- Cursor: "Entity를 보고 DTO 만들어줘"

---

#### Task 3-3: API 문서 시작 (15분)
- [ ] README.md 업데이트
- [ ] API.md 파일 생성
- [ ] 구현된 엔드포인트 문서화
  - GET /api/v1/health
  - WS /ws

**사용 도구**:
- Claude(나): "오늘 구현한 내용을 API 문서로 정리해줘"
- Cursor로 마ーク다운 파일 작성

---

## 📝 각 시간 종료 시 체크리스트

### 1시간 후 체크
- [ ] 프로젝트가 정상적으로 빌드되는가?
- [ ] 모든 의존성이 정상적으로 로드되는가?
- [ ] application.yml이 올바르게 설정되었는가?

### 2시간 후 체크
- [ ] Health Check API가 정상 작동하는가?
- [ ] WebSocket 연결이 성공하는가?
- [ ] 에코 메시지가 정상적으로 동작하는가?

### 3시간 후 체크
- [ ] 기본 Entity 모델이 생성되었는가?
- [ ] DTO가 정의되었는가?
- [ ] API 문서가 작성되었는가?

---

## 🛠️ AI 도구 활용 전략

### Cursor 사용
- **주 용도**: 실제 코드 작성, 파일 생성
- **팁**: 
  - `.cursorrules` 파일에 프로젝트 컨벤션 정의
  - 명세서를 컨텍스트로 제공
  - "명세서 기반으로 XXX 만들어줘" 형태로 요청

### ChatGPT 사용
- **주 용도**: 빠른 정보 검색, 예제 코드
- **언제**: 
  - 막힐 때 즉시 질문
  - 의존성 버전 확인
  - 에러 메시지 해결

### Claude (나)
- **주 용도**: 
  - 전체적인 방향 검토
  - 복잡한 설계 논의
  - 코드 리뷰
- **언제**:
  - 시작 전 계획 확인
  - 막힐 때 전체 맥락 공유
  - 3시간 후 리뷰

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
- [ ] 명세서 파일 열어두기

---

## 🎯 3시간 후 예상 결과

### 완성된 것들
1. ✅ Spring Boot 프로젝트 기본 구조
2. ✅ Health Check API
3. ✅ WebSocket 기본 설정 및 에코 테스트
4. ✅ 기본 Entity 모델 (User, Channel, Message)
5. ✅ 기본 DTO
6. ✅ API 문서 시작

### Git Commit 내역 (예상)
```
1. feat: 초기 프로젝트 셋업 및 의존성 추가
2. feat: Health Check API 구현
3. feat: WebSocket 기본 설정 및 에코 테스트
4. feat: 기본 Entity 및 DTO 모델 추가
5. docs: README 및 API 문서 작성
```

---

## 💡 내일 할 일 미리보기

1. JWT 인증 구현
2. User Repository 및 Service
3. 회원가입/로그인 API
4. Signal Protocol 라이브러리 통합 시작

---

## 메모

- 오늘은 **기반 구축**에 집중
- 완벽하게 만들려고 하지 말고 **빠르게 동작하는 것** 우선
- 막히면 즉시 AI에게 질문
- 3시간 후 Claude에게 코드 리뷰 요청할 것
