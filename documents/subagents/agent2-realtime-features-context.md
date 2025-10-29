# Agent 2: Real-time Features Specialist 🚀

**역할**: WebSocket 및 실시간 기능 전문가  
**담당 작업**: Task 2 - 실시간 상태 기능 구현 (타이핑 인디케이터, 읽음 표시)

---

## 📋 프로젝트 개요

**프로젝트명**: Cupid - 레즈비언 소개팅 앱 채팅 SDK  
**기술 스택**: Kotlin + Spring Boot 3.5.7  
**실시간 통신**: WebSocket (STOMP over SockJS)  
**캐시/상태**: Redis  
**메시지 저장**: MongoDB  
**현재 단계**: Phase 1 MVP 완성을 위한 기능 보완

---

## ✅ 현재 구현 상태

### 완료된 기능
- ✅ WebSocket 연결 설정 완료 (STOMP over SockJS)
- ✅ 메시지 전송/수신 WebSocket 구현
- ✅ `OnlineStatusService` 존재
- ✅ `MessageReads` 엔티티 존재
- ✅ Redis 설정 완료
- ✅ 메시지 브로드캐스트 구현

### 미구현 기능
- ❌ 타이핑 인디케이터 미구현
- ❌ 읽음 표시(Read Receipt) API 미구현
- ❌ 타이핑 상태 브로드캐스트 미구현
- ❌ 읽음 상태 브로드캐스트 미구현

---

## 🔑 핵심 엔티티 및 구조

### WebSocket Configuration
```kotlin
package com.august.cupid.config

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val connectionInterceptor: ConnectionInterceptor,
    private val stompChannelInterceptor: StompChannelInterceptor
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // SimpleBroker 활성화 (메모리 기반 브로커)
        registry.enableSimpleBroker("/topic", "/queue")
        // 클라이언트가 보낸 메시지를 처리할 destination prefix
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .addInterceptors(connectionInterceptor)
            .setAllowedOriginPatterns("*")
            .withSockJS()
    }
}
```

### Message Entity (MongoDB)
```kotlin
@Document(collection = "messages")
data class Message(
    @Id
    val id: UUID = UUID.randomUUID(),
    
    @Field("channel_id")
    val channelId: UUID,
    
    @Field("sender_id")
    val senderId: UUID,
    
    @Field("encrypted_content")
    val encryptedContent: String,
    
    @Field("message_type")
    val messageType: MessageType = MessageType.TEXT,
    
    @Field("status")
    val status: MessageStatus = MessageStatus.SENT,
    
    @Field("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    // ... 기타 필드
)
```

### MessageReads Entity (MongoDB)
```kotlin
@Document(collection = "message_reads")
@CompoundIndexes(
    CompoundIndex(name = "message_user_unique", def = "{'message_id': 1, 'user_id': 1}", unique = true)
)
data class MessageReads(
    @Id
    val id: UUID = UUID.randomUUID(),
    
    @Field("message_id")
    val messageId: UUID,
    
    @Field("channel_id")
    val channelId: UUID,
    
    @Field("user_id")
    val userId: UUID,
    
    @Field("read_at")
    val readAt: LocalDateTime = LocalDateTime.now()
)
```

### OnlineStatusService (현재 구현)
```kotlin
@Service
class OnlineStatusService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    companion object {
        private const val ONLINE_USER_KEY_PREFIX = "user:online:"
        private const val ONLINE_USER_TTL_MINUTES = 5L
    }
    
    fun isUserOnline(userId: String): Boolean {
        val key = "$ONLINE_USER_KEY_PREFIX$userId"
        return redisTemplate.hasKey(key)
    }
    
    fun setUserOnline(userId: String) {
        val key = "$ONLINE_USER_KEY_PREFIX$userId"
        redisTemplate.opsForValue().set(key, "1", ONLINE_USER_TTL_MINUTES, TimeUnit.MINUTES)
    }
}
```

---

## 📦 의존성 (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
```

---

## 🗄️ Redis 키 구조

### 현재 사용 중인 키 패턴
```kotlin
// 온라인 상태
"user:online:{userId}" // TTL: 5분

// 타이핑 인디케이터 (구현 필요)
"typing:{channelId}:{userId}" // TTL: 10초

// 읽지 않은 메시지 수 (구현 필요)
"unread:{userId}:{channelId}" // 카운터
```

---

## 🎯 구현해야 할 작업

### Task 2.1: 타이핑 인디케이터 (2시간) ⭐ 오늘의 첫 번째 작업

**요구사항**:
- [ ] `TypingIndicatorService` 생성
- [ ] WebSocket 엔드포인트: `/app/typing/start`, `/app/typing/stop`
- [ ] Redis 키: `typing:{channelId}:{userId}` (TTL: 10초)
- [ ] 구독 토픽: `/topic/channel.{channelId}.typing`
- [ ] 클라이언트에서 입력 시작/종료 시 WebSocket 메시지 전송
- [ ] 자동 만료 로직 (10초 TTL)

**구현 예시**:
```kotlin
@MessageMapping("/typing/start")
fun handleTypingStart(
    @Payload request: TypingRequest,
    headerAccessor: SimpMessageHeaderAccessor
) {
    val userId = getUserId(headerAccessor)
    typingIndicatorService.setTyping(request.channelId, userId)
    
    // 채널 멤버에게 브로드캐스트
    messagingTemplate.convertAndSend(
        "/topic/channel.${request.channelId}.typing",
        TypingEvent(userId, request.channelId, isTyping = true)
    )
}
```

### Task 2.2: 읽음 표시 (Read Receipt) (2시간) ⭐ 오늘의 두 번째 작업

**요구사항**:
- [ ] `POST /api/v1/channels/{channelId}/messages/{messageId}/read` API 구현
- [ ] `MessageReads` 엔티티 저장 (MongoDB)
- [ ] WebSocket으로 읽음 상태 브로드캐스트
- [ ] 발신자에게 읽음 알림 전송
- [ ] 배치 읽음 처리 (여러 메시지를 한 번에 읽음 표시)

**구현 예시**:
```kotlin
@PostMapping("/channels/{channelId}/messages/{messageId}/read")
fun markAsRead(
    @PathVariable channelId: UUID,
    @PathVariable messageId: UUID,
    @AuthenticationPrincipal userId: UUID
): ApiResponse<ReadReceiptResponse> {
    messageService.markAsRead(messageId, userId, channelId)
    
    // 발신자에게 읽음 알림
    val message = messageService.getMessage(messageId)
    messagingTemplate.convertAndSendToUser(
        message.senderId.toString(),
        "/queue/read-receipts",
        ReadReceiptEvent(messageId, userId, channelId)
    )
}
```

---

## 📝 기존 코드 패턴

### WebSocket Message Handler
```kotlin
@Controller
class ChatController(
    private val messagingTemplate: SimpMessagingTemplate,
    private val messageService: MessageService
) {
    
    @MessageMapping("/send")
    fun handleMessage(
        @Payload request: SendMessageRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        val userId = extractUserId(headerAccessor)
        val savedMessage = messageService.sendMessage(request, userId)
        
        // 채널 topic으로 브로드캐스트
        messagingTemplate.convertAndSend(
            "/topic/channel/${request.channelId}",
            savedMessage
        )
        
        // User destination으로 브로드캐스트
        messagingTemplate.convertAndSendToUser(
            recipientId.toString(),
            "/queue/messages",
            savedMessage
        )
    }
}
```

### Redis Template 사용
```kotlin
@Service
class SomeService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    fun setValue(key: String, value: String, ttlMinutes: Long) {
        redisTemplate.opsForValue().set(
            key, 
            value, 
            ttlMinutes, 
            TimeUnit.MINUTES
        )
    }
}
```

---

## 🔧 설정 파일 (application.yml)

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0

  data:
    mongodb:
      uri: mongodb://localhost:27017/chatsdk
```

---

## 📚 참고 문서

1. **스펙 문서**: `documents/specifications/chat-sdk-spec.md` 섹션 1.4
2. **데이터베이스 스키마**: `documents/specifications/database-schema.md` 섹션 3.3
3. **작업 목록**: `documents/tasks/today-tasks.md` - Task 2

---

## 💡 구현 가이드

### 타이핑 인디케이터 구현 순서
1. `TypingIndicatorService.kt` 생성 및 Redis 통합
2. `TypingRequest`, `TypingEvent` DTO 생성
3. `ChatController`에 `/app/typing/start`, `/app/typing/stop` 핸들러 추가
4. Redis에 타이핑 상태 저장 (TTL 10초)
5. WebSocket으로 브로드캐스트 (`/topic/channel.{channelId}.typing`)
6. 자동 만료 처리

### 읽음 표시 구현 순서
1. `MessageReadService.kt` 생성
2. `POST /api/v1/channels/{channelId}/messages/{messageId}/read` API 구현
3. `MessageReads` 엔티티 저장 (MongoDB)
4. WebSocket으로 읽음 이벤트 브로드캐스트 (발신자에게)
5. 배치 읽음 처리 지원 (선택사항)
6. Redis 캐시 업데이트 (unread count)

---

## 🎯 MEGA PROMPT (시작 시 사용)

```
You are implementing a typing indicator feature for a chat SDK using Kotlin/Spring Boot backend with WebSocket (STOMP).

Context:
- Backend: Kotlin + Spring Boot + WebSocket (STOMP over SockJS)
- Database: Redis for temporary state (TTL: 10 seconds)
- Existing: WebSocket connection already working for messages
- Goal: Show "Alice is typing..." when user types

Requirements:
1. Create TypingIndicatorService with Redis integration
2. WebSocket endpoints: /app/typing/start and /app/typing/stop
3. Auto-expire after 10 seconds (Redis TTL)
4. Broadcast to channel members via /topic/channel.{channelId}.typing
5. Handle edge cases: user stops typing, disconnection, multiple typers

Please provide:
1. TypingIndicatorService.kt with Redis operations
2. TypingController.kt for WebSocket message handling
3. TypingRequest and TypingEvent data classes
4. Redis key structure and operations
5. Client-side integration example (JavaScript/TypeScript)
6. Test scenarios

Use these existing patterns from the codebase:
- MessageController pattern for WebSocket handling
- RedisTemplate for Redis operations
- STOMP message broadcasting via SimpMessagingTemplate
```

