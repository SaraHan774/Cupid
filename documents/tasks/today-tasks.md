# 채팅 SDK 개발 - 보완 작업 목록

**날짜**: 2025년 1월 19일  
**목표**: 스펙 문서 기반 미구현 기능 식별 및 우선순위별 작업 목록 작성

---

## 📋 스펙 대비 미구현 기능 분석

스펙 문서(`chat-sdk-spec.md`, `database-schema.md`)를 확인한 결과, 다음과 같은 기능들이 보완이 필요합니다.

**💡 MEGA PROMPTs 포함**: 각 작업에는 AI 어시스턴트(ChatGPT, Claude, Cursor 등)를 활용할 수 있는 상세 프로ンプ트가 포함되어 있습니다. 각 섹션의 `<details>` 태그를 펼치면 확인할 수 있습니다.

---

## 🎯 오늘의 핵심 목표 (권장 우선순위)

### 즉시 구현 권장 (4-6시간)

다음 두 기능은 채팅 앱의 기본 기능이며 구현이 비교적 간단합니다:

1. **타이핑 인디케이터** (2시간) - 사용자 경험 향상
2. **읽음 표시** (2시간) - 기본 채팅 기능

### 다음 우선순위 (3-4시간)

3. **프로필 이미지 업로드** (2-3시간) - 사용자 참여도에 중요
4. **매칭 해제 처리** (2-3시간) - 비즈니스 로직 필수

---

## 🔴 Phase 1 (MVP) - 우선 구현 필요

### 1. E2E 암호화 (Signal Protocol) 구현

**현재 상태**: 
- ✅ `UserKeys` 엔티티 존재 (키 저장 구조)
- ✅ `Message.encryptedContent` 필드 존재
- ❌ 실제 Signal Protocol 통합 서비스 미구현
- ❌ 키 생성/교환/암호화 로직 미구현

**작업 내용**:
1. **SignalProtocolService 생성** (3-4시간)
   - [ ] libsignal-java 라이브러리 통합
   - [ ] 키 쌍 생성 메서드
   - [ ] X3DH 키 교환 로직
   - [ ] Double Ratchet 메시지 암호화/복호화
   - [ ] 키 백업/복구 기능 (선택사항)

2. **UserKeys 관리 API** (2시간)
   - [ ] `POST /api/v1/users/keys` - 공개키 등록
   - [ ] `GET /api/v1/users/{userId}/keys` - 공개키 조회
   - [ ] `PUT /api/v1/users/keys` - 키 갱신

3. **메시지 암호화 통합** (2시간)
   - [ ] MessageService에서 자동 암호화 적용
   - [ ] 클라이언트 요청 시 암호화된 내용만 저장
   - [ ] 복호화는 클라이언트 측에서 처리 (서버는 암호화된 데이터만 저장)

**참고 문서**: `chat-sdk-spec.md` 섹션 1.0

---

### 2. 실시간 상태 기능 구현

**현재 상태**:
- ✅ `OnlineStatusService` 존재
- ✅ `MessageReads` 엔티티 존재
- ❌ 타이핑 인디케이터 미구현
- ❌ 읽음 표시(Read Receipt) API 미구현

**작업 내용**:

#### 2.1 타이핑 인디케이터 (2시간) ⭐ 오늘의 첫 번째 작업

이 기능은 하트비트 신호와 같습니다 - Alice가 타이핑하면 Bob이 실시간으로 "typing..." 상태를 봅니다.

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

<details>
<summary><b>📝 MEGA PROMPT for Typing Indicator</b></summary>

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
</details>

#### 2.2 읽음 표시 (Read Receipt) (2시간) ⭐ 오늘의 두 번째 작업

WhatsApp의 파란 체크마크처럼 "전달 확인" 기능입니다.

- [ ] `POST /api/v1/channels/{channelId}/messages/{messageId}/read` API 구현
- [ ] `MessageReads` 엔티티 저장
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
        message.senderId,
        "/queue/read-receipts",
        ReadReceiptEvent(messageId, userId, channelId)
    )
}
```

<details>
<summary><b>📝 MEGA PROMPT for Read Receipts</b></summary>

```
You are implementing read receipts (read indicators) for a chat SDK.

Context:
- Backend: Kotlin + Spring Boot
- Database: MongoDB for message_reads collection
- WebSocket: STOMP for real-time updates
- Existing: MessageReads entity exists but API not implemented

Requirements:
1. REST API: POST /api/v1/channels/{channelId}/messages/{messageId}/read
2. Store read status in MongoDB message_reads collection
3. Broadcast read event via WebSocket to message sender
4. Update unread count in Redis cache
5. Batch read receipts for performance (mark multiple messages as read)

Please provide:
1. MessageReadService.kt with business logic
2. REST endpoint in MessageController
3. WebSocket notification logic
4. MongoDB operations for message_reads
5. Redis cache update for unread counts
6. Client integration example
7. Performance optimization for marking multiple messages

Consider:
- What if user reads 100 messages at once?
- How to handle offline users?
- Privacy settings (option to disable read receipts)
```
</details>

**참고 문서**: `database-schema.md` 섹션 3.3, 시나리오 5-6

---

### 3. 사용자 프로필 관리 ⭐ 다음 우선순위

**현재 상태**:
- ✅ `User` 엔티티에 프로필 이미지 필드 존재 (`profileImageUrl`, `profileThumbnailUrl`, `profileImageBlurhash`, `profileImageMetadata`)
- ❌ 프로필 사진 업로드 API 미구현
- ❌ 이미지 최적화 서비스 미구현

**작업 내용** (4-5시간):

이 기능은 사용자 참여도에 중요합니다. Netflix가 연결 상태에 따라 비디오 품질을 조정하는 것처럼, 동일한 이미지의 여러 "품질 레벨"을 생성합니다.

#### 3.1 이미지 업로드 API (2시간)
- [ ] `POST /api/v1/users/profile-image` - 프로필 사진 업로드
- [ ] 파일 검증 (크기, 형식)
- [ ] MultipartFile 처리
- [ ] 기본 이미지 저장 (S3 또는 로컬)

#### 3.2 이미지 최적화 서비스 (2-3시간)
- [ ] `ImageOptimizationService` 생성
- [ ] 다중 해상도 생성 (Original: 800x800, Large: 400x400, Medium: 200x200, Small: 100x100)
- [ ] WebP 포맷 변환 + JPEG 폴백
- [ ] BlurHash 생성 (서버 측 또는 클라이언트 제공받기)
- [ ] 메타데이터 생성 및 저장
- [ ] CDN 업로드 (S3 + CloudFront)

<details>
<summary><b>📝 MEGA PROMPT for Profile Image System</b></summary>

```
You are implementing a complete profile image management system with optimization.

Context:
- Backend: Kotlin + Spring Boot
- Storage: AWS S3 (or local for dev)
- Database: PostgreSQL with profile image fields already created
- Goal: Multi-resolution images + WebP conversion + BlurHash

Requirements:
1. REST API: POST /api/v1/users/profile-image
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
3. S3Service.kt for cloud storage
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
</details>

**참고 문서**: `chat-sdk-spec.md` 섹션 1.1, `database-schema.md` 시나리오 1, 15

---

### 4. 매칭 해제 시 채팅방 처리 ⭐ 비즈니스 로직 필수

**현재 상태**:
- ✅ `Match` 엔티티 존재 (status: ACTIVE, EXPIRED, CANCELLED 등)
- ✅ `Channel.match` 관계 존재
- ❌ 매칭 해제 시 채팅방 처리 로직 미구현

**작업 내용** (2-3시간):

"관계 상태 관리자" 같은 기능입니다 - 매칭이 만료되면 채팅방에 어떻게 처리할지 결정해야 합니다.

- [ ] `MatchService`에 매칭 해제 메서드 추가
- [ ] Config 모드에 따른 채널 처리:
  - Mode 1: 완전 삭제 (채널 및 멤버 삭제)
  - Mode 2: 읽기 전용으로 전환 (채널 metadata에 플래그 설정)
  - Mode 3: 일정 기간 후 자동 삭제 (스케줄러 작업)
- [ ] 매칭 만료 체크 스케줄러 (주기적 실행)

<details>
<summary><b>📝 MEGA PROMPT for Match Expiration</b></summary>

```
You are implementing match expiration handling for a dating app chat system.

Context:
- Dating app where matches expire after 24 hours
- Existing: Match entity with status and expires_at fields
- Need: Automated handling when matches expire

Requirements:
1. MatchExpirationService with configurable modes:
   - DELETE: Complete removal of channel and messages
   - READ_ONLY: Convert to read-only (no new messages)
   - ARCHIVE: Move to archive after period
2. Scheduled job to check expired matches
3. Config-based mode selection
4. Cleanup of associated data

Please provide:
1. MatchExpirationService.kt with different handling modes
2. ScheduledTask for periodic checking (@Scheduled)
3. Configuration enum and properties
4. Database operations for each mode
5. WebSocket notifications to users
6. Soft delete vs hard delete strategy
7. Test cases for each mode

Consider:
- Running every 5 minutes vs hourly?
- Batch processing for performance
- What if users are mid-conversation?
- Timezone considerations
```
</details>

**참고 문서**: `chat-sdk-spec.md` 섹션 2.1, `database-schema.md` 시나리오 10

---

### 5. 채널 삭제 모드 구현

**현재 상태**:
- ✅ 채널 나가기 기능 존재 (ChannelService.leaveChannel)
- ❌ Config 기반 삭제 모드 미구현
- ❌ 1:1 채널 전체 삭제 모드 미구현

**작업 내용** (1-2시간):
- [ ] `ChannelDeleteMode` Enum 생성 (INDIVIDUAL, DELETE_ALL)
- [ ] `ChannelService.leaveChannel` 로직 확장
- [ ] DIRECT 채널에서 DELETE_ALL 모드인 경우 상대방도 채널에서 제거
- [ ] Config 설정 추가 (application.yml 또는 환경변수)

**참고 문서**: `chat-sdk-spec.md` 섹션 1.2

---

## 🟡 Phase 2 - 확장 기능

### 6. 메시지 수정/삭제

**현재 상태**:
- ✅ `Message.editHistory` 필드 존재
- ✅ `Message.status` 필드에 DELETED 값 존재
- ❌ 메시지 수정 API 미구현
- ❌ 메시지 삭제 API 미구현

**작업 내용** (3-4시간):

#### 6.1 메시지 수정 (2시간)
- [ ] `PUT /api/v1/channels/{channelId}/messages/{messageId}` API
- [ ] 생성 시간부터 10분 이내인지 검증 (Config 설정 가능)
- [ ] `editHistory`에 기존 내용 추가
- [ ] `encryptedContent` 업데이트
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
    require(message.senderId == userId) { "본인의 메시지만 수정할 수 있습니다" }
    
    // 시간 제한 확인 (10분)
    val timeLimit = Duration.ofMinutes(10) // Config에서 가져오기
    require(ChronoUnit.MINUTES.between(message.createdAt, LocalDateTime.now()) <= timeLimit.toMinutes()) {
        "메시지는 ${timeLimit.toMinutes()}분 이내에만 수정할 수 있습니다"
    }
    
    return messageService.editMessage(messageId, request.encryptedContent, channelId)
}
```

#### 6.2 메시지 삭제 (1-2시간)
- [ ] `DELETE /api/v1/channels/{channelId}/messages/{messageId}` API
- [ ] 소유자 확인
- [ ] `status`를 DELETED로 변경
- [ ] `deletedAt` 타임스탬프 설정
- [ ] `encryptedContent`를 빈 값 또는 "deleted"로 대체
- [ ] WebSocket으로 삭제 알림 브로드캐스트

**참고 문서**: `chat-sdk-spec.md` 섹션 1.3, `database-schema.md` 시나리오 12

---

### 7. 그룹 채팅 최대 인원 제한

**현재 상태**:
- ✅ 그룹 채널 생성 기능 존재
- ✅ `targetUserIds`로 멤버 초대 기능 존재
- ❌ 최대 인원 제한 로직 미구현

**작업 내용** (1시간):
- [ ] `ChannelService.createChannel`에서 그룹 채널 인원 체크
- [ ] 기본값: 3명 (Config 설정 가능)
- [ ] 최대 인원 초과 시 에러 반환

**구현 예시**:
```kotlin
if (request.type == ChannelType.GROUP) {
    val maxMembers = configService.getMaxGroupSize() // 기본값: 3
    val totalMembers = (targetUserIds?.size ?: 0) + 1 // 생성자 포함
    require(totalMembers <= maxMembers) {
        "그룹 채팅은 최대 ${maxMembers}명까지 가능합니다"
    }
}
```

**참고 문서**: `chat-sdk-spec.md` 섹션 1.2

---

### 8. 알림 고급 기능

**현재 상태**:
- ✅ `UserNotificationSettings` 엔티티 존재
- ✅ `ChannelNotificationSettings` 엔티티 존재
- ✅ `FcmToken` 엔티티 및 기본 알림 기능 존재
- ❌ 알림 설정 API 미구현
- ❌ 방해금지 모드 로직 미구현

**작업 내용** (4-5시간):

#### 8.1 전역 알림 설정 API (2시간)
- [ ] `GET /api/v1/notifications/settings` - 설정 조회
- [ ] `PUT /api/v1/notifications/settings` - 설정 업데이트
- [ ] 방해금지 모드 로직 (시간대, 요일 확인)

#### 8.2 채널별 알림 설정 API (2-3시간)
- [ ] `GET /api/v1/channels/{channelId}/notifications/settings` - 채널 설정 조회
- [ ] `PUT /api/v1/channels/{channelId}/notifications/settings` - 채널 설정 업데이트
- [ ] `POST /api/v1/channels/{channelId}/notifications/mute` - 음소거 (1시간/24시간)
- [ ] `POST /api/v1/channels/{channelId}/notifications/unmute` - 음소거 해제

**참고 문서**: `notification-system-spec.md` 섹션 5-7

---

### 9. 메시지 자동 삭제 (보관 기간)

**현재 상태**:
- ✅ MongoDB TTL 인덱스 지원 가능
- ❌ Config 기반 보관 기간 설정 미구현
- ❌ 메시지 자동 삭제 스케줄러 미구현

**작업 내용** (2-3시간):
- [ ] Config에 `messageRetentionPeriod` 설정 추가
- [ ] MongoDB TTL 인덱스 동적 생성 (보관 기간 설정된 경우)
- [ ] 또는 스케줄러로 주기적 삭제 (Application Level)
- [ ] 채널별로 다른 보관 기간 설정 가능하도록 확장 (선택사항)

**참고 문서**: `chat-sdk-spec.md` 섹션 7. SDK Config, `database-schema.md` 섹션 4.1, 시나리오 14

---

## 🟢 Phase 3 - 고급 기능 (향후 구현)

### 10. 검색 기능
- [ ] 채널 내 메시지 검색 (클라이언트 측만 가능 - E2E 암호화)
- [ ] 채널 검색 (채널명, 참여자)

### 11. 스팸 방지
- [ ] 메시지 전송 빈도 제한 (Rate Limit 확장)
- [ ] 의심 행동 감지 및 자동 차단

### 12. 관리자 기능
- [ ] 채팅방 모니터링 대시보드
- [ ] 신고된 메시지 검토 시스템
- [ ] 사용자 강제 차단
- [ ] 통계 및 분석 도구

---

## 📊 우선순위별 작업 계획

### 즉시 구현 필요 (Phase 1 MVP 완성을 위해)
1. **타이핑 인디케이터** (2시간) - 사용자 경험 향상 ⭐ 오늘
2. **읽음 표시** (2시간) - 기본 채팅 기능 ⭐ 오늘
3. **매칭 해제 처리** (2-3시간) - 비즈니스 로직 필수 ⭐ 오늘/내일

### 단기 구현 (1-2주 내)
4. **프로필 이미지 업로드/최적화** (4-5시간) ⭐ 다음 우선순위
5. **메시지 수정/삭제** (3-4시간)
6. **그룹 채팅 인원 제한** (1시간)
7. **알림 고급 기능** (4-5시간)
8. **채널 삭제 모드** (1-2시간)

### 중기 구현 (1개월 내)
9. **E2E 암호화 완전 구현** (7-8시간) - 보안 필수
10. **메시지 자동 삭제** (2-3시간)

---

## 🛠️ 기술 스택 및 의존성

### 추가 필요 라이브러리

1. **Signal Protocol**
   ```kotlin
   // build.gradle.kts
   implementation("org.whispersystems:signal-protocol-java:2.8.1")
   ```

2. **이미지 처리** (선택)
   ```kotlin
   // Thumbnailator 또는 Java ImageIO 사용
   // 또는 Cloudinary, Imgix 같은 외부 서비스 활용
   ```

3. **BlurHash** (선택)
   ```kotlin
   // 클라이언트 측에서 생성 권장 (서버 부하 감소)
   // 또는 Java/Kotlin 라이브러리 사용
   ```

---

## 📝 체크리스트

### Phase 1 MVP 완료 기준
- [ ] 타이핑 인디케이터 구현 완료
- [ ] 읽음 표시 구현 완료
- [ ] 매칭 해제 처리 로직 구현 완료
- [ ] 기본 프로필 이미지 업로드 (최적화는 선택)
- [ ] 채널 삭제 모드 기본 구현

### Phase 2 완료 기준
- [ ] 메시지 수정/삭제 구현 완료
- [ ] 그룹 채팅 인원 제한 구현 완료
- [ ] 알림 고급 기능 구현 완료
- [ ] 프로필 이미지 최적화 구현 완료

### Phase 3 완료 기준
- [ ] E2E 암호화 완전 구현
- [ ] 메시지 자동 삭제 구현 완료
- [ ] 검색 기능 구현 (클라이언트 측)
- [ ] 관리자 도구 기본 구현

---

## 🔗 참고 문서

1. **chat-sdk-spec.md** - 전체 기능 명세
   - 섹션 1: 핵심 기능
   - 섹션 2: 소개팅 앱 특화 기능
   - 섹션 8: Phase별 개발 계획

2. **database-schema.md** - 데이터베이스 스키마
   - 시나리오 1: 회원가입 및 Signal 키 생성
   - 시나리오 5: 읽음 표시
   - 시나리오 6: 타이핑 인디케이터
   - 시나리오 10: 매칭 만료 후 채팅방 처리
   - 시나리오 12: 메시지 수정

3. **notification-system-spec.md** - 알림 시스템 상세
   - 섹션 5: 알림 설정
   - 섹션 7: API 명세

---

## 💡 AI 어시스턴트 활용 팁

이 문서의 MEGA PROMPTs는 다음과 같이 활용하세요:

1. **ChatGPT/Claude**: 각 MEGA PROMPT를 복사하여 전체 컨텍스트와 함께 전달
2. **Cursor AI**: 프로젝트 컨텍스트와 함께 MEGA PROMPT 사용
3. **단계별 구현**: 큰 작업은 여러 개의 작은 MEGA PROMPT로 나누어 진행

**예시 워크플로우**:
```
1. today-tasks.md에서 오늘 작업 선택
2. 해당 작업의 MEGA PROMPT 복사
3. ChatGPT/Claude/Cursor에 전달
4. 생성된 코드 검토 및 통합
5. 테스트 작성 및 실행
```

---

## 메모

- 현재 WebSocket 메시지 전송/수신 기능은 구현되어 있음
- FCM 알림 기본 기능은 구현되어 있음
- Rate Limiting은 구현 완료
- 대부분의 엔티티와 데이터 구조는 준비되어 있음
- 주요 부족 부분: 비즈니스 로직 서비스 및 API 엔드포인트
- MEGA PROMPTs는 실제 구현 시 AI 어시스턴트와 함께 사용하여 개발 속도를 향상시킬 수 있음
