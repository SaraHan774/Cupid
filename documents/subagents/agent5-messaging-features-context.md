# Agent 5: Messaging Features Expert 💬

**역할**: 메시지 관리 기능 전문가  
**담당 작업**: Task 6 - 메시지 수정/삭제

---

## 📋 프로젝트 개요

**프로젝트명**: Cupid - 레즈비언 소개팅 앱 채팅 SDK  
**기술 스택**: Kotlin + Spring Boot 3.5.7  
**메시지 저장소**: MongoDB  
**실시간 통신**: WebSocket (STOMP)  
**현재 단계**: Phase 1 MVP 완성을 위한 기능 보완

---

## ✅ 현재 구현 상태

### 완료된 기능
- ✅ `Message` 엔티티 존재:
  - `editHistory` 필드 존재 (미사용)
  - `status` 필드에 DELETED 값 존재 (미사용)
  - `deletedAt` 필드 존재
- ✅ 기본 메시지 전송/수신 구현 완료
- ✅ WebSocket 브로드캐스트 구현 완료

### 미구현 기능
- ❌ 메시지 수정 API 미구현
- ❌ 메시지 삭제 API 미구현
- ❌ 수정 시간 제한 검증 미구현
- ❌ Edit History 기록 미구현
- ❌ 수정/삭제 이벤트 브로드캐스트 미구현

---

## 🔑 핵심 엔티티 및 구조

### Message Entity (MongoDB)
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

    @Field("encrypted_content")
    val encryptedContent: String,

    @Field("message_type")
    val messageType: MessageType = MessageType.TEXT,

    @Field("status")
    val status: MessageStatus = MessageStatus.SENT,

    @Field("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Field("updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Field("deleted_at")
    val deletedAt: LocalDateTime? = null,

    // 메시지 수정 이력 (E2E 암호화)
    @Field("edit_history")
    val editHistory: List<EditHistory> = emptyList(),

    @Field("metadata")
    val metadata: Map<String, Any>? = null
)

enum class MessageStatus {
    SENT,      // 전송됨
    DELIVERED, // 전달됨
    DELETED    // 삭제됨
}

data class EditHistory(
    val encryptedContent: String,
    val editedAt: LocalDateTime
)
```

---

## 📦 의존성 (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

---

## 🗄️ 데이터베이스 스키마

### messages 컬렉션 (MongoDB)
```javascript
{
  _id: UUID,
  channel_id: UUID,
  sender_id: UUID,
  encrypted_content: String,
  message_type: "TEXT" | "IMAGE" | "FILE",
  status: "SENT" | "DELIVERED" | "DELETED",
  created_at: ISODate,
  updated_at: ISODate,
  deleted_at: ISODate | null,
  edit_history: [
    {
      encrypted_content: String,
      edited_at: ISODate
    }
  ],
  metadata: Object
}

// 인덱스
db.messages.createIndex({ channel_id: 1, created_at: -1 })
db.messages.createIndex({ sender_id: 1 })
db.messages.createIndex({ status: 1 })
```

---

## 🎯 구현해야 할 작업

### Task 6.1: 메시지 수정 (2시간)

**요구사항**:
- [ ] `PUT /api/v1/channels/{channelId}/messages/{messageId}` API
- [ ] 생성 시간부터 10분 이내인지 검증 (Config 설정 가능)
- [ ] 소유자 확인 (발신자만 수정 가능)
- [ ] `editHistory`에 기존 내용 추가
- [ ] `encryptedContent` 업데이트
- [ ] `updatedAt` 타임스탬프 업데이트
- [ ] WebSocket으로 수정 알림 브로드캐스트

**구현 예시**:
```kotlin
@PutMapping("/channels/{channelId}/messages/{messageId}")
fun editMessage(
    @PathVariable channelId: UUID,
    @PathVariable messageId: UUID,
    @RequestBody request: EditMessageRequest,
    @AuthenticationPrincipal userId: UUID
): ApiResponse<MessageResponse> {
    val message = messageService.getMessage(messageId)
    
    // 소유자 확인
    require(message.senderId == userId) { 
        "본인의 메시지만 수정할 수 있습니다" 
    }
    
    // 시간 제한 확인 (10분)
    val timeLimit = Duration.ofMinutes(10) // Config에서 가져오기
    val minutesSinceCreation = ChronoUnit.MINUTES.between(
        message.createdAt, 
        LocalDateTime.now()
    )
    require(minutesSinceCreation <= timeLimit.toMinutes()) {
        "메시지는 ${timeLimit.toMinutes()}분 이내에만 수정할 수 있습니다"
    }
    
    return messageService.editMessage(messageId, request.encryptedContent, channelId)
}
```

### Task 6.2: 메시지 삭제 (1-2시간)

**요구사항**:
- [ ] `DELETE /api/v1/channels/{channelId}/messages/{messageId}` API
- [ ] 소유자 확인 (발신자만 삭제 가능)
- [ ] `status`를 DELETED로 변경
- [ ] `deletedAt` 타임스탬프 설정
- [ ] `encryptedContent`를 빈 값 또는 "deleted"로 대체
- [ ] WebSocket으로 삭제 알림 브로드캐스트

**구현 예시**:
```kotlin
@DeleteMapping("/channels/{channelId}/messages/{messageId}")
fun deleteMessage(
    @PathVariable channelId: UUID,
    @PathVariable messageId: UUID,
    @AuthenticationPrincipal userId: UUID
): ApiResponse<Unit> {
    val message = messageService.getMessage(messageId)
    
    // 소유자 확인
    require(message.senderId == userId) { 
        "본인의 메시지만 삭제할 수 있습니다" 
    }
    
    return messageService.deleteMessage(messageId, channelId)
}
```

---

## 📝 기존 코드 패턴

### MessageService (현재 구현)
```kotlin
@Service
class MessageService(
    private val messageRepository: MessageRepository
) {
    fun sendMessage(request: SendMessageRequest, userId: UUID): ApiResponse<Message> {
        val message = Message(
            channelId = request.channelId,
            senderId = userId,
            encryptedContent = request.encryptedContent,
            messageType = request.messageType
        )
        
        val saved = messageRepository.save(message)
        return ApiResponse.success(saved)
    }
    
    fun getMessage(messageId: UUID): Message {
        return messageRepository.findById(messageId)
            .orElseThrow { NotFoundException("메시지를 찾을 수 없습니다") }
    }
}
```

### WebSocket 브로드캐스트 패턴
```kotlin
@Controller
class ChatController(
    private val messagingTemplate: SimpMessagingTemplate
) {
    fun broadcastMessageUpdate(channelId: UUID, message: Message) {
        // 채널 topic으로 브로드캐스트
        messagingTemplate.convertAndSend(
            "/topic/channel/$channelId",
            MessageUpdateEvent(message.id, message)
        )
        
        // 개별 사용자에게도 전송
        messagingTemplate.convertAndSendToUser(
            recipientId.toString(),
            "/queue/message-updates",
            message
        )
    }
}
```

---

## 🔧 설정 파일 (application.yml)

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/chatsdk

# 메시지 설정 (추가 필요)
message:
  edit-time-limit-minutes: 10  # 메시지 수정 시간 제한 (분)
  allow-delete: true  # 메시지 삭제 허용 여부
```

---

## 📚 참고 문서

1. **스펙 문서**: `documents/specifications/chat-sdk-spec.md` 섹션 1.3
2. **데이터베이스 스키마**: `documents/specifications/database-schema.md` 시나리오 12
3. **작업 목록**: `documents/tasks/today-tasks.md` - Task 6

---

## 💡 구현 가이드

### 메시지 수정 구현 순서
1. `EditMessageRequest` DTO 생성
2. `PUT /api/v1/channels/{channelId}/messages/{messageId}` API 구현
3. 소유자 확인 로직
4. 시간 제한 검증 로직 (10분)
5. Edit History 추가 로직
6. MongoDB 업데이트 (findById + save)
7. WebSocket 브로드캐스트
8. 테스트 케이스 작성

### 메시지 삭제 구현 순서
1. `DELETE /api/v1/channels/{channelId}/messages/{messageId}` API 구현
2. 소유자 확인 로직
3. Soft Delete 구현 (status = DELETED, deletedAt 설정)
4. encryptedContent 대체 (보안상 삭제 표시)
5. MongoDB 업데이트
6. WebSocket 브로드캐스트
7. 테스트 케이스 작성

### 고려사항
- **Soft Delete**: 물리적 삭제 대신 상태 변경으로 처리 (데이터 보존)
- **Edit History**: 이전 내용을 암호화된 형태로 보관
- **WebSocket 이벤트**: 클라이언트가 UI를 즉시 업데이트할 수 있도록
- **동시성**: 낙관적 락 또는 버전 관리 고려

---

## 🎯 MEGA PROMPT (시작 시 사용)

```
You are a messaging features expert focusing on message management.

Current state:
- Basic message sending works
- Message.editHistory field exists but not used
- Message.status has DELETED value but not implemented
- MongoDB for message storage
- WebSocket (STOMP) for real-time updates

Requirements:
- Message editing within 10 minutes (configurable)
- Soft delete with "deleted message" display
- Edit history tracking
- WebSocket notifications for edits/deletes

Please provide:
1. Message edit API (PUT /api/v1/channels/{channelId}/messages/{messageId})
2. Message delete API (DELETE endpoint)
3. Time limit validation (10 minutes configurable)
4. Edit history management
5. Soft delete implementation
6. WebSocket broadcasting for updates
7. Error handling for unauthorized/expired edits
8. MongoDB update operations
9. Test cases

Consider:
- What if user edits message multiple times?
- How to handle edit conflicts?
- Privacy: Should deleted content be visible to admin?
- Performance: Batch updates for bulk operations
```

