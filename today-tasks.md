# Cupid 프로젝트 패키지 리팩토링 계획

## 📋 목표
현재 단일 패키지 구조를 **도메인 기반 멀티 패키지 구조**로 리팩토링하여 향후 소개팅 앱 개발 시 확장성 확보

---

## 🎯 리팩토링 원칙

### 도메인 분리 기준
1. **auth** - 인증 및 사용자 관리 (User, 로그인, JWT)
2. **chat** - 채팅 SDK (Channel, Message, WebSocket)
3. **encryption** - E2E 암호화 (Signal Protocol, 키 관리)
4. **dating** - 소개팅 비즈니스 로직 (Match, Profile)
5. **notification** - 알림 시스템 (FCM, 푸시)
6. **common** - 공통 유틸리티 및 설정

### 의존성 규칙
```
dating → chat, auth, encryption, notification
chat → auth (User 참조만)
encryption → auth (User 참조만)
notification → auth (User 참조만)
auth → common (다른 도메인 의존 X)
```

---

## 📦 목표 패키지 구조

```
com.august.cupid/
│
├── auth/                           # 인증 & 사용자 관리
│   ├── controller/
│   │   ├── AuthController.kt
│   │   └── ProfileController.kt
│   ├── service/
│   │   ├── AuthService.kt
│   │   └── UserService.kt
│   ├── repository/
│   │   ├── UserRepository.kt
│   │   └── UserBlocksRepository.kt
│   ├── entity/
│   │   ├── User.kt
│   │   └── UserBlocks.kt
│   └── dto/
│       ├── LoginRequest.kt
│       ├── RegisterRequest.kt
│       └── UserResponse.kt
│
├── chat/                           # 채팅 SDK
│   ├── controller/
│   │   ├── ChannelController.kt
│   │   ├── MessageController.kt
│   │   └── ChatController.kt
│   ├── service/
│   │   ├── ChannelService.kt
│   │   ├── MessageService.kt
│   │   ├── ReadReceiptService.kt
│   │   ├── TypingIndicatorService.kt
│   │   └── OnlineStatusService.kt
│   ├── repository/
│   │   ├── ChannelRepository.kt
│   │   ├── ChannelMembersRepository.kt
│   │   ├── MessageRepository.kt
│   │   └── MessageReadsRepository.kt
│   ├── entity/
│   │   ├── Channel.kt
│   │   ├── ChannelMembers.kt
│   │   ├── Message.kt
│   │   └── MessageReads.kt
│   ├── dto/
│   │   ├── SendMessageRequest.kt
│   │   ├── MessageResponse.kt
│   │   └── ChannelResponse.kt
│   └── websocket/
│       ├── WebSocketConfig.kt
│       ├── WebSocketEventListener.kt
│       ├── StompChannelInterceptor.kt
│       └── ConnectionInterceptor.kt
│
├── encryption/                     # E2E 암호화 (Signal Protocol)
│   ├── controller/
│   │   ├── KeyExchangeController.kt
│   │   └── KeyBackupController.kt
│   ├── service/
│   │   ├── SignalProtocolService.kt
│   │   ├── EncryptionService.kt
│   │   ├── KeyBackupService.kt
│   │   └── EncryptionMetricsService.kt
│   ├── service/signal/
│   │   ├── DatabaseSignalProtocolStore.kt
│   │   └── [기타 Signal Protocol 구현]
│   ├── repository/
│   │   ├── UserKeysRepository.kt
│   │   ├── SignalIdentityRepository.kt
│   │   ├── SignalPreKeyRepository.kt
│   │   ├── SignalSignedPreKeyRepository.kt
│   │   ├── SignalSessionRepository.kt
│   │   └── KeyBackupRepository.kt
│   ├── entity/
│   │   ├── UserKeys.kt
│   │   ├── SignalIdentity.kt
│   │   ├── SignalPreKey.kt
│   │   ├── SignalSignedPreKey.kt
│   │   ├── SignalSession.kt
│   │   ├── KeyBackup.kt
│   │   └── KeyRotationHistory.kt
│   └── dto/
│       ├── PreKeyBundleDto.kt
│       └── KeyBackupRequest.kt
│
├── dating/                         # 소개팅 비즈니스 로직
│   ├── controller/
│   │   └── [향후 추가]
│   ├── service/
│   │   └── MatchService.kt
│   ├── repository/
│   │   ├── MatchRepository.kt
│   │   └── ReportRepository.kt
│   ├── entity/
│   │   ├── Match.kt
│   │   └── Report.kt
│   └── dto/
│       └── [향후 추가]
│
├── notification/                   # 알림 시스템
│   ├── controller/
│   │   └── NotificationController.kt
│   ├── service/
│   │   └── NotificationService.kt
│   ├── service/notification/
│   │   ├── FCMService.kt
│   │   └── EmailService.kt
│   ├── entity/notification/
│   │   ├── ChannelNotificationSettings.kt
│   │   ├── DeviceToken.kt
│   │   └── NotificationLog.kt
│   └── dto/notification/
│       └── [FCM 관련 DTO]
│
├── admin/                          # 관리자 기능
│   ├── controller/
│   │   ├── AdminDashboardController.kt
│   │   ├── AdminKeyRotationController.kt
│   │   └── SecurityAuditController.kt
│   ├── service/
│   │   └── SecurityAuditLogger.kt
│   └── entity/
│       └── SecurityAuditLog.kt
│
├── common/                         # 공통 모듈
│   ├── config/
│   │   ├── SecurityConfig.kt
│   │   ├── WebSocketConfig.kt (재배치)
│   │   ├── JpaConfig.kt
│   │   └── [기타 설정]
│   ├── exception/
│   │   ├── GlobalExceptionHandler.kt
│   │   └── [커스텀 예외들]
│   ├── security/
│   │   ├── JwtTokenProvider.kt
│   │   ├── JwtAuthenticationFilter.kt
│   │   └── CustomUserDetailsService.kt
│   ├── util/
│   │   ├── ImageOptimizationService.kt
│   │   └── StorageService.kt
│   └── dto/
│       ├── ApiResponse.kt
│       └── PagedResponse.kt
│
└── CupidApplication.kt             # 메인 애플리케이션
```

---

## 🔄 마이그레이션 단계

### Phase 1: 준비 (1일)
- [ ] Git 브랜치 생성: `feature/package-refactoring`
- [ ] 현재 코드 백업 및 커밋
- [ ] 의존성 분석 도구 실행 (IntelliJ 구조 분석)

### Phase 2: Common 모듈 먼저 분리 (0.5일)
**이동 대상:**
- [ ] `config/` → `common/config/`
- [ ] `exception/` → `common/exception/`
- [ ] `security/` → `common/security/`
- [ ] `util/` → `common/util/`
- [ ] `dto/ApiResponse.kt`, `PagedResponse.kt` → `common/dto/`

**검증:**
- [ ] 빌드 성공 확인
- [ ] Import 경로 자동 수정 확인

### Phase 3: Auth 도메인 분리 (0.5일)
**이동 대상:**
- [ ] `AuthController`, `ProfileController` → `auth/controller/`
- [ ] `AuthService`, `UserService` → `auth/service/`
- [ ] `User.kt`, `UserBlocks.kt` → `auth/entity/`
- [ ] `UserRepository`, `UserBlocksRepository` → `auth/repository/`
- [ ] 인증 관련 DTO → `auth/dto/`

**검증:**
- [ ] 로그인/회원가입 테스트
- [ ] 빌드 성공 확인

### Phase 4: Encryption 도메인 분리 (1일)
**이동 대상:**
- [ ] `KeyExchangeController`, `KeyBackupController` → `encryption/controller/`
- [ ] `SignalProtocolService`, `EncryptionService`, `KeyBackupService` → `encryption/service/`
- [ ] `service/signal/` 전체 → `encryption/service/signal/`
- [ ] Signal 관련 엔티티 → `encryption/entity/`
- [ ] Signal 관련 Repository → `encryption/repository/`
- [ ] 암호화 관련 DTO → `encryption/dto/`

**검증:**
- [ ] 키 생성 테스트
- [ ] 세션 초기화 테스트
- [ ] 메시지 암호화/복호화 테스트

### Phase 5: Chat 도메인 분리 (1일)
**이동 대상:**
- [ ] `ChannelController`, `MessageController`, `ChatController` → `chat/controller/`
- [ ] `ChannelService`, `MessageService` → `chat/service/`
- [ ] `ReadReceiptService`, `TypingIndicatorService`, `OnlineStatusService` → `chat/service/`
- [ ] `Channel`, `Message`, `ChannelMembers`, `MessageReads` → `chat/entity/`
- [ ] 채팅 관련 Repository → `chat/repository/`
- [ ] `websocket/` 전체 → `chat/websocket/`
- [ ] 채팅 관련 DTO → `chat/dto/`

**검증:**
- [ ] 채널 생성 테스트
- [ ] 메시지 송수신 테스트
- [ ] WebSocket 연결 테스트
- [ ] 그룹 채팅 테스트

### Phase 6: Notification 도메인 분리 (0.5일)
**이동 대상:**
- [ ] `NotificationController` → `notification/controller/`
- [ ] `NotificationService` → `notification/service/`
- [ ] `fcm/` 전체 → `notification/service/notification/`
- [ ] 알림 관련 엔티티 → `notification/entity/`
- [ ] 알림 관련 DTO → `notification/dto/`

**검증:**
- [ ] 푸시 알림 전송 테스트

### Phase 7: Dating 도메인 분리 (0.5일)
**이동 대상:**
- [ ] `MatchService` → `dating/service/`
- [ ] `Match.kt`, `Report.kt` → `dating/entity/`
- [ ] `MatchRepository`, `ReportRepository` → `dating/repository/`

**검증:**
- [ ] 매칭 기능 테스트

### Phase 8: Admin 도메인 분리 (0.5일)
**이동 대상:**
- [ ] Admin 관련 Controller → `admin/controller/`
- [ ] `SecurityAuditLogger` → `admin/service/`
- [ ] `SecurityAuditLog` → `admin/entity/`

**검증:**
- [ ] 관리자 대시보드 테스트

### Phase 9: 최종 검증 및 정리 (1일)
- [ ] 전체 빌드 테스트
- [ ] 전체 기능 테스트 (test-client 활용)
  - [ ] 로그인/회원가입
  - [ ] 키 생성
  - [ ] 세션 초기화
  - [ ] 1:1 채팅
  - [ ] 그룹 채팅
  - [ ] 실시간 메시지 송수신
  - [ ] 알림
- [ ] 사용하지 않는 import 제거
- [ ] 코드 스타일 통일
- [ ] 문서 업데이트 (README.md)

### Phase 10: 배포 및 모니터링 (0.5일)
- [ ] 테스트 서버 배포
- [ ] 실제 환경 테스트
- [ ] 로그 모니터링
- [ ] 메인 브랜치 병합 (Merge)

---

## ⚠️ 주의사항

### Import 경로 변경
모든 파일에서 import 경로가 자동으로 변경됩니다:
```kotlin
// Before
import com.august.cupid.service.MessageService

// After
import com.august.cupid.chat.service.MessageService
```

### 순환 의존성 방지
- `auth` 도메인은 다른 도메인을 의존하면 안 됨
- `chat` 도메인은 `auth.entity.User`만 참조 가능
- `encryption` 도메인은 `auth.entity.User`만 참조 가능

### 테스트 코드도 함께 이동
```
src/test/kotlin/com/august/cupid/
├── auth/
├── chat/
├── encryption/
└── ...
```

---

## 📊 예상 소요 시간

| Phase | 작업 | 예상 시간 |
|-------|------|----------|
| 1 | 준비 | 1일 |
| 2 | Common 분리 | 0.5일 |
| 3 | Auth 분리 | 0.5일 |
| 4 | Encryption 분리 | 1일 |
| 5 | Chat 분리 | 1일 |
| 6 | Notification 분리 | 0.5일 |
| 7 | Dating 분리 | 0.5일 |
| 8 | Admin 분리 | 0.5일 |
| 9 | 최종 검증 | 1일 |
| 10 | 배포 | 0.5일 |
| **합계** | | **약 7일** |

---

## 🎁 리팩토링 후 이점

### 1. 명확한 책임 분리
각 도메인이 독립적인 역할을 가짐

### 2. 확장성
소개팅 앱 개발 시 `dating` 도메인만 확장

### 3. 재사용성
`chat` 도메인을 다른 프로젝트에서도 사용 가능

### 4. 테스트 용이성
도메인별 단위 테스트 작성 가능

### 5. 팀 협업
도메인별로 개발자 배치 가능

### 6. 향후 멀티 모듈 전환 용이
Gradle 멀티 모듈로 쉽게 전환 가능

---

## 🚀 다음 단계 (리팩토링 후)

### Option A: Gradle 멀티 모듈로 전환
```
cupid-project/
├── cupid-common/
├── cupid-auth/
├── cupid-chat/
├── cupid-encryption/
└── cupid-app/
```

### Option B: 현재 구조 유지
패키지만 분리된 상태로 유지하고 소개팅 앱 개발 진행

---

## 📝 참고 자료

- [Spring Boot 멀티 모듈 구성](https://docs.spring.io/spring-boot/docs/current/gradle-plugin/reference/htmlsingle/)
- [패키지 구조 베스트 프랙티스](https://github.com/wikibook/clean-architecture)
- [도메인 주도 설계 (DDD)](https://martinfowler.com/bliki/DomainDrivenDesign.html)

---

## ✅ 시작 전 체크리스트

- [ ] 현재 코드 커밋 완료
- [ ] 브랜치 생성 완료
- [ ] 백업 완료
- [ ] 팀원 공유 (해당되는 경우)
- [ ] 예상 일정 확보

**준비되면 Phase 1부터 시작!**
