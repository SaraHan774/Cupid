# 채팅 SDK - 다음 단계 작업 계획

**작성일**: 2025년 1월 17일  
**방식**: 패키지 단위 개발

---

## 📋 현재 상태

### ✅ 완료된 패키지
- **config/** - WebSocket, Security, Firebase 설정
- **websocket/** - 연결 인터셉터, 이벤트 리스너
- **service/** - MessageService, OnlineStatusService, NotificationService
- **repository/** - 모든 데이터베이스 접근 계층

### 🔄 진행 중
- **controller/** - ChatController (WebSocket 통합 완료)

### ❌ 미구현
- **fcm/** - FCM 전송 서비스
- 테스트 코드

---

## 🎯 패키지별 작업 계획

### **Package 1: controller/ - REST API 컨트롤러** 
**목표**: HTTP REST API 엔드포인트 완성

#### Task 1-1: NotificationController 구현 (2-3시간)
```
파일: src/main/kotlin/com/august/cupid/controller/NotificationController.kt
```

**기능**:
- [ ] `POST /api/v1/notifications/fcm-token` - FCM 토큰 등록
- [ ] `DELETE /api/v1/notifications/fcm-token/{tokenId}` - 토큰 삭제
- [ ] `GET /api/v1/notifications/fcm-token` - 내 토큰 목록
- [ ] `GET /api/v1/notifications/settings` - 알림 설정 조회
- [ ] `PUT /api/v1/notifications/settings` - 알림 설정 업데이트
- [ ] `PUT /api/v1/channels/{channelId}/notifications/settings` - 채널별 설정
- [ ] `POST /api/v1/channels/{channelId}/notifications/mute` - 음소거
- [ ] `POST /api/v1/channels/{channelId}/notifications/unmute` - 음소거 해제

**인증**: JWT 기반 (현재 SecurityConfig 활용)

**의존성**:
- NotificationService (이미 구현됨)
- AuthService (JWT 처리)

---

#### Task 1-2: ChannelController 구현 (2-3시간)
```
파일: src/main/kotlin/com/august/cupid/controller/ChannelController.kt
```

**기능**:
- [ ] `GET /api/v1/channels` - 채널 목록 (페이징)
- [ ] `GET /api/v1/channels/{channelId}` - 채널 상세 정보
- [ ] `POST /api/v1/channels` - 채널 생성 (1:1 또는 그룹)
- [ ] `DELETE /api/v1/channels/{channelId}/leave` - 채널 나가기
- [ ] `GET /api/v1/channels/{channelId}/members` - 멤버 목록

**의존성**:
- ChannelService (구현 필요)
- MatchService (매칭 기반 채널 생성용)

---

#### Task 1-3: MessageController 구현 (2-3시간)
```
파일: src/main/kotlin/com/august/cupid/controller/MessageController.kt
```

**기능**:
- [ ] `GET /api/v1/channels/{channelId}/messages` - 메시지 목록 (페이징)
- [ ] `POST /api/v1/channels/{channelId}/messages` - HTTP로 메시지 전송 (WebSocket 대체)
- [ ] `PUT /api/v1/messages/{messageId}` - 메시지 수정
- [ ] `DELETE /api/v1/messages/{messageId}` - 메시지 삭제
- [ ] `POST /api/v1/messages/{messageId}/read` - 읽음 표시
- [ ] `GET /api/v1/channels/{channelId}/unread-count` - 읽지 않은 메시지 수

**의존성**:
- MessageService (이미 구현됨)

---

### **Package 2: service/ - 비즈니스 로직** 
**목표**: 미구현 서비스 완성

#### Task 2-1: ChannelService 구현 (3-4시간)
```
파일: src/main/kotlin/com/august/cupid/service/ChannelService.kt
```

**기능**:
- [ ] 1:1 채널 생성 (매칭 기반)
- [ ] 그룹 채널 생성 (Phase 2, 지금은 기본 구조)
- [ ] 채널 목록 조회 (페이징)
- [ ] 채널 정보 조회
- [ ] 채널 나가기 (Config 모드 적용)
- [ ] 멤버 추가/제거

**의존성**:
- ChannelRepository ✅
- ChannelMembersRepository ✅
- MatchService (매칭 확인)

---

#### Task 2-2: MatchService 보완 (1-2시간)
```
파일: src/main/kotlin/com/august/cupid/service/MatchService.kt (확인 필요)
```

**기능**:
- [ ] 매칭 생성
- [ ] 매칭 상태 확인 (active, expired, cancelled)
- [ ] 매칭 만료 처리 (스케줄러)
- [ ] 채널 연동 (매칭 기반 채널 생성)

**의존성**:
- MatchRepository ✅

---

### **Package 3: fcm/ - FCM 전송 서비스**
**목표**: FCM Silent Push 전송 로직 완성

#### Task 3-1: FcmDeliveryService 구현 (3-4시간)
```
파일: src/main/kotlin/com/august/cupid/fcm/FcmDeliveryService.kt
```

**기능**:
- [ ] Silent Push 메시지 전송
  - iOS: apns-content-available, apns-mutable-content
  - Android: priority: high
- [ ] 암호화된 메시지 전송 (E2E 암호화 유지)
- [ ] 배치 전송 (다중 수신자)
- [ ] 전송 실패 처리 (토큰 비활성화)

**구조**:
```kotlin
data class SilentPushMessage(
    val userId: UUID,
    val type: String, // "new_message", "match"
    val channelId: UUID?,
    val senderId: UUID?,
    val encryptedContent: String,
    val metadata: Map<String, String>
)
```

**의존성**:
- FirebaseMessaging ✅
- FcmTokenRepository ✅

---

#### Task 3-2: FcmToken 관리 개선 (1시간)
```
파일: src/main/kotlin/com/august/cupid/service/NotificationService.kt (수정)
```

**개선 사항**:
- [ ] Redis 캐싱 추가 (토큰 조회 성능)
- [ ] 토큰 중복 확인 로직 개선
- [ ] 토큰 만료 자동 정리 (스케줄러)

---

### **Package 4: test/ - 테스트 코드**
**목표**: 핵심 기능 테스트 작성

#### Task 4-1: WebSocket 통합 테스트 (2-3시간)
```
파일: src/test/kotlin/com/august/cupid/websocket/WebSocketIntegrationTest.kt
```

**테스트 시나리오**:
- [ ] WebSocket 연결 성공
- [ ] 메시지 전송/수신
- [ ] 온라인 상태 확인
- [ ] 하트비트 처리
- [ ] 자동 재연결

**도구**:
- WebSocketTestClient (Spring Framework)

---

#### Task 4-2: MessageService 테스트 (2시간)
```
파일: src/test/kotlin/com/august/cupid/service/MessageServiceTest.kt
```

**테스트 시나리오**:
- [ ] 메시지 전송
- [ ] 메시지 수정
- [ ] 메시지 삭제
- [ ] 읽음 표시
- [ ] 읽지 않은 메시지 수

**도구**:
- @DataMongoTest
- MockMongoDB

---

#### Task 4-3: NotificationService 테스트 (2시간)
```
파일: src/test/kotlin/com/august/cupid/service/NotificationServiceTest.kt
```

**테스트 시나리오**:
- [ ] FCM 토큰 등록
- [ ] 알림 전송 (Mock Firebase)
- [ ] 알림 설정 업데이트
- [ ] 방해금지 모드

**도구**:
- Mockito for FirebaseMessaging

---

### **Package 5: util/ - 유틸리티**
**목표**: 공통 기능 추가

#### Task 5-1: RedisKeyManager 추가 (1시간)
```
파일: src/main/kotlin/com/august/cupid/util/RedisKeyManager.kt
```

**기능**:
- [ ] Redis 키 생성 헬퍼
- [ ] TTL 관리
- [ ] 패턴 매칭

**예시**:
```kotlin
object RedisKeyManager {
    fun userOnline(userId: String) = "user:online:$userId"
    fun fcmToken(userId: String) = "user:fcm_token:$userId"
    fun unreadCount(userId: String, channelId: String) = "unread:$userId:$channelId"
}
```

---

#### Task 5-2: WebSocketPrincipal 설정 (1-2시간)
```
파일: src/main/kotlin/com/august/cupid/websocket/WebSocketPrincipal.kt
```

**목적**: ChatController의 extractUserId 개선

```kotlin
class WebSocketPrincipal(val userId: UUID) : Principal {
    override fun getName(): String = userId.toString()
}
```

---

## 📊 작업 우선순위

### 🔴 High Priority (이번 주)
1. **NotificationController** (REST API 제공)
2. **FcmDeliveryService** (실제 FCM 전송)
3. **ChannelController** (채널 관리 API)

### 🟡 Medium Priority (다음 주)
4. **ChannelService** (비즈니스 로직)
5. **MatchService 보완** (매칭 처리)
6. **Redis 캐싱** (성능 개선)

### 🟢 Low Priority (추후)
7. **통합 테스트** (품질 보장)
8. **유틸리티 함수** (코드 정리)

---

## 🔄 개발 워크플로우

### 패키지 단위 작업

1. **패키지 선택**
   - 우선순위에 따라 패키지 선택
   
2. **파일 생성**
   - 패키지 내 새 파일 생성 또는 기존 파일 수정

3. **의존성 확인**
   - 다른 패키지와의 의존성 파악
   - 이미 구현된 Repository/Service 활용

4. **구현**
   - 코어 기능부터 구현
   - 점진적으로 기능 추가

5. **통합**
   - 기존 패키지와 통합 테스트
   - 빌드 확인

6. **검증**
   - 로컬 테스트
   - API 테스트

7. **커밋**
   - 패키지 단위로 커밋
   - 명확한 커밋 메시지

---

## 📝 패키지별 커밋 예시

```bash
# Package 1: controller
git commit -m "feat(controller): NotificationController 구현

- FCM 토큰 등록/삭제 API
- 알림 설정 CRUD API
- 채널별 알림 설정 API"

# Package 2: service  
git commit -m "feat(service): ChannelService 구현

- 1:1 채널 생성
- 채널 목록 조회
- 채널 나가기 기능"

# Package 3: fcm
git commit -m "feat(fcm): FcmDeliveryService 구현

- Silent Push 메시지 전송
- iOS/Android 설정
- 배치 전송 처리"
```

---

## 🎯 다음 세션 계획

### 다음 작업: NotificationController 구현
**예상 시간**: 2-3시간
**파일**: `src/main/kotlin/com/august/cupid/controller/NotificationController.kt`
**의존성**: NotificationService ✅ (이미 구현됨)

**구현할 API**:
1. POST /api/v1/notifications/fcm-token
2. GET /api/v1/notifications/settings
3. PUT /api/v1/notifications/settings

**시작 명령**:
```kotlin
// 이 파일을 새로 생성하고 다음부터 구현
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    // 구현 시작
}
```

---

## 📚 참고 문서

- `documents/specifications/chat-sdk-spec.md` - 전체 기능 명세
- `documents/specifications/notification-system-spec.md` - FCM 구현 가이드
- `documents/specifications/database-schema.md` - DB 스키마

