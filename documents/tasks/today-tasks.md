# 채팅 SDK 개발 - 오늘의 태스크

**날짜**: 2025년 1월 17일  
**목표**: WebSocket 메시지 전송 완료 및 FCM 알림 시스템 구현 시작

---

## 🎯 오늘의 핵심 목표

**Priority 1: WebSocket 메시지 브로드캐스트 완료** (2-3시간)
- MessageService와 WebSocket 통합
- 채널 멤버들에게 실시간 메시지 전송
- 온라인/오프라인 상태 체크

**Priority 2: FCM 알림 시스템 기초 구현** (2-3시간)
- FCM 토큰 관리 엔티티 및 API
- 기본 알림 전송 로직 (오프라인 사용자 대상)
- Silent Push 구조 이해

---

## ⏰ 오늘의 태스크

### Priority 1: WebSocket 메시지 브로드캐스트 (2-3시간)

**목표**: MessageService와 WebSocket 통합하여 실시간 메시지 전송 완성

#### Task 1: WebSocket 메시지 핸들러 구현 (1.5시간)

**현재 상황**:
- ✅ MessageService 완성 (메시지 CRUD, 읽음 상태 등)
- ✅ WebSocket 기본 설정 완료
- ✅ OnlineStatusService 구현됨
- ❌ WebSocket으로 메시지 브로드캐스트 미구현

**구현 내용**:
1. **ChatController 확장**
   - [ ] `/app/send` 메시지 매핑 추가
   - [ ] MessageService와 통합
   - [ ] 채널 멤버 조회
   - [ ] 온라인/오프라인 상태 확인
   - [ ] 온라인 사용자에게 WebSocket 전송
   - [ ] 오프라인 사용자 처리 (다음 Task)

2. **메시지 전송 플로우**:
   ```kotlin
   @MessageMapping("/send")
   fun handleMessage(
       @Payload request: SendMessageWebSocketRequest,
       headerAccessor: SimpMessageHeaderAccessor
   ) {
       // 1. 사용자 ID 추출
       val userId = getUserId(headerAccessor)
       
       // 2. MessageService로 메시지 저장
       val response = messageService.sendMessage(request.toHttpRequest(), userId)
       
       // 3. 채널 멤버 조회
       val members = channelMembersRepository.findByChannelId(request.channelId)
       
       // 4. 온라인 사용자에게 브로드캐스트
       members.forEach { member ->
           if (onlineStatusService.isUserOnline(member.userId)) {
               messagingTemplate.convertAndSendToUser(
                   member.userId,
                   "/queue/messages",
                   response.data
               )
           }
       }
   }
   ```

3. **DTO 추가**:
   - `SendMessageWebSocketRequest.kt` - WebSocket 요청
   - `MessageWebSocketEvent.kt` - WebSocket 이벤트

**참고 파일**:
- `src/main/kotlin/com/august/cupid/service/MessageService.kt`
- `src/main/kotlin/com/august/cupid/controller/ChatController.kt`
- `src/main/kotlin/com/august/cupid/service/OnlineStatusService.kt`

---

#### Task 2: 메시지 수신 핸들러 구현 (30분)

**구현 내용**:
1. **클라이언트 구독 엔드포인트 설정**
   - [ ] `/user/{userId}/queue/messages` 토픽 설정
   - [ ] 클라이언트 구독 코드 확인

2. **테스트**:
   - [ ] 두 사용자로 WebSocket 연결
   - [ ] 한 사용자가 메시지 전송
   - [ ] 다른 사용자가 메시지 수신 확인

**WebSocket 테스트 HTML 수정**:
```html
// 메시지 전송 버튼 추가
// 수신 메시지 표시 영역 추가
```

---

#### Task 3: 통합 테스트 (30분)

**테스트 시나리오**:
1. [ ] 사용자 A가 채널에 메시지 전송
2. [ ] 사용자 B가 온라인 → WebSocket으로 즉시 수신
3. [ ] 사용자 B가 오프라인 → 메시지 저장, FCM 전송 (다음 Priority)
4. [ ] 읽음 상태 표시 동작 확인

---

### Priority 2: FCM 알림 시스템 기초 구현 (2-3시간)

#### Task 4: FCM 엔티티 및 Repository 구현 (1시간)

**구현 내용**:
1. **FcmToken 엔티티 생성**
   - [ ] `FcmToken.kt` - 토큰 저장
   - [ ] `FcmTokenRepository.kt` - 쿼리 메서드
   - [ ] DB 마이그레이션 스크립트

2. **FcmToken 엔티티 구조**:
   ```kotlin
   @Entity
   @Table(name = "fcm_tokens")
   data class FcmToken(
       @Id
       @GeneratedValue(strategy = GenerationType.UUID)
       val id: UUID,
       
       @Column(nullable = false)
       val userId: UUID,
       
       @Column(nullable = false, unique = true, length = 500)
       val token: String,
       
       @Enumerated(EnumType.STRING)
       val deviceType: DeviceType, // IOS, ANDROID
       
       val deviceName: String?,
       val appVersion: String?,
       
       val createdAt: LocalDateTime,
       var lastUsedAt: LocalDateTime,
       var isActive: Boolean = true
   )
   ```

3. **Repository 메서드**:
   - `findByUserId()` - 사용자 토큰 조회
   - `findByUserIdAndIsActive()` - 활성 토큰만 조회
   - `deactivateByUserId()` - 사용자 토큰 비활성화

**참고**: `documents/specifications/notification-system-spec.md` 섹션 6

---

#### Task 5: FCM 토큰 관리 API 구현 (1시간)

**구현 내용**:
1. **FcmTokenController 생성**
   - [ ] `POST /api/v1/notifications/fcm-token` - 토큰 등록
   - [ ] `DELETE /api/v1/notifications/fcm-token/{tokenId}` - 토큰 삭제
   - [ ] `GET /api/v1/notifications/fcm-token` - 내 토큰 목록

2. **API 명세**:
   ```kotlin
   @PostMapping("/fcm-token")
   fun registerFcmToken(
       @RequestBody request: RegisterFcmTokenRequest,
       @AuthenticationPrincipal userId: UUID
   ): ApiResponse<String> {
       // 1. 기존 토큰 확인
       // 2. 새 토큰 저장 또는 업데이트
       // 3. Redis 캐시 업데이트
   }
   ```

3. **DTO**:
   - `RegisterFcmTokenRequest.kt`
   - `FcmTokenResponse.kt`

---

#### Task 6: FCM 전송 로직 구현 (1.5시간)

**구현 내용**:
1. **FcmService 생성**
   - [ ] Firebase Admin SDK 초기화 확인
   - [ ] 기본 메시지 전송 메서드
   - [ ] Silent Push 메시지 전송

2. **메시지 전송 플로우**:
   ```kotlin
   fun sendMessageNotification(
       userId: UUID,
       channelId: UUID,
       senderId: UUID,
       encryptedContent: String
   ) {
       // 1. FCM 토큰 조회 (Redis 캐시 우선)
       val tokens = getFcmTokens(userId)
       
       // 2. Silent Push 메시지 구성
       val message = Message.builder()
           .setToken(token)
           .putData("type", "new_message")
           .putData("channel_id", channelId.toString())
           .putData("sender_id", senderId.toString())
           .putData("encrypted_content", encryptedContent)
           .setAndroidConfig(AndroidConfig.builder()
               .setPriority(AndroidConfig.Priority.HIGH)
               .build())
           .setApnsConfig(ApnsConfig.builder()
               .putHeader("apns-priority", "10")
               .setAps(Aps.builder()
                   .setContentAvailable(true)
                   .setMutableContent(true)
                   .build())
               .build())
           .build()
       
       // 3. 전송
       firebaseMessaging.send(message)
   }
   ```

3. **MessageService 통합**:
   - [ ] 메시지 전송 후 오프라인 사용자 확인
   - [ ] 오프라인 사용자에게 FCM 전송
   - [ ] Redis 온라인 상태 확인

**참고**: `documents/specifications/notification-system-spec.md` 섹션 3-4

---

## 📝 체크포인트

### Priority 1 완료 체크
- [ ] WebSocket으로 메시지 전송 가능
- [ ] 온라인 사용자에게 메시지 수신 확인
- [ ] 채널 멤버 자동 브로드캐스트
- [ ] 읽음 상태 정상 작동

### Priority 2 완료 체크
- [ ] FCM 토큰 등록/삭제 API 작동
- [ ] 오프라인 사용자에게 FCM 전송
- [ ] Firebase Admin SDK 정상 작동
- [ ] Silent Push 메시지 확인

---

## 🛠️ 구현 팁

### 1. WebSocket 메시지 브로드캐스트
- `SimpMessagingTemplate.convertAndSendToUser()` 사용
- 사용자별 개인 큐: `/user/{userId}/queue/messages`
- 채널별 공개 토픽: `/topic/channel.{channelId}.messages`

### 2. FCM 구현
- **핵심**: Silent Push로 암호화된 내용만 전송
- 클라이언트가 백그라운드에서 복호화
- iOS 30초 제약 준수 (receiver service extension)

### 3. 온라인 상태 체크
- Redis 키: `user:online:{userId}`
- TTL: 5분 (하트비트로 갱신)
- WebSocket 연결 시 저장, 해제 시 삭제

---

## 📖 참고 문서

1. **notification-system-spec.md** - FCM 구현 상세 가이드
   - 섹션 3: E2E 암호화와 알림
   - 섹션 4: 기술 아키텍처
   - 섹션 8: 구현 가이드

2. **chat-sdk-spec.md** - 전체 기능 명세
   - 섹션 1.3: 메시지 송수신
   - 섹션 3.2: 알림

3. **database-schema.md** - DB 스키마
   - fcm_tokens 테이블 구조

---

## 🚨 예상 이슈

### 이슈 1: WebSocket 메시지 전송 실패
**증상**: 메시지는 저장되지만 다른 사용자에게 전달 안 됨  
**대응**: 
1. SimpMessagingTemplate 빈 확인
2. 사용자 구독 토픽 확인
3. Principal 설정 확인 (ConnectionInterceptor)

### 이슈 2: FCM 토큰 등록 실패
**증상**: Firebase Admin SDK 초기화 오류  
**대응**:
1. firebase-service-account.json 경로 확인
2. FirebaseConfig 초기화 순서 확인
3. 로그 확인

### 이슈 3: Silent Push 동작 안 함
**증상**: iOS에서 알림이 표시되지만 클릭 시 앱이 깨지지 않음  
**대응**:
1. Notification Service Extension 구현 확인
2. Background Modes 활성화 확인
3. App Group 설정 확인

---

## 💡 내일 할 일

1. **알림 설정 API 구현**
   - 전역 알림 설정 (켜기/끄기, 소리, 진동)
   - 채널별 알림 설정

2. **백그라운드 알림 테스트**
   - iOS Notification Service Extension 구현
   - Android FirebaseMessagingService 구현

3. **통합 테스트**
   - E2E 메시지 전송 플로우
   - 온라인/오프라인 전환 테스트

---

## 🔑 핵심 포인트

### WebSocket 메시지 플로우
```
사용자 A 메시지 전송
  ↓
MessageService.save()
  ↓
채널 멤버 조회
  ↓
온라인? → WebSocket 전송
오프라인? → FCM 전송 (다음 Priority)
```

### FCM Silent Push 플로우
```
서버 → FCM → OS가 앱 깨움
  ↓
Notification Service Extension 실행
  ↓
Signal Protocol 복호화
  ↓
로컬 알림 생성 (복호화된 내용)
  ↓
사용자에게 표시
```

---

## 메모

- 현재 메시지 전송은 HTTP API만 있음
- WebSocket 브로드캐스트 추가 필요
- FCM은 오프라인 사용자 대상만 사용
- 온라인 상태는 Redis로 즉시 확인 가능
