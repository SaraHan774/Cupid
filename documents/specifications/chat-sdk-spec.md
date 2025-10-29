# 채팅 SDK 기능 명세서

## 프로젝트 개요
- **목적**: 레즈비언 소개팅 앱을 위한 채팅 SDK 개발 (추후 다른 앱에도 확장 가능)
- **주요 사용자**: 매칭된 사용자 간 1:1 채팅 및 그룹 채팅
- **기술 스택**: 
  - Backend: Kotlin + Spring Boot + WebSocket
  - SDK: JavaScript/TypeScript (ReactNative)
  - Database: PostgreSQL (사용자/채널), MongoDB (메시지)
  - Cache: Redis (세션, Pub/Sub)

---

## 1. 핵심 기능 (Core Features)

### 1.0 보안 및 암호화
- [ ] Signal Protocol 통합
- [ ] 사용자 키 쌍 생성 및 관리
- [ ] 키 교환 (X3DH - Extended Triple Diffie-Hellman)
- [ ] Double Ratchet 알고리즘 (메시지 암호화)
- [ ] Forward Secrecy (과거 메시지 보호)
- [ ] 안전성 검증 (Safety Number)
- [ ] 키 백업 및 복구 (선택사항)

**구현 우선순위**: 🔴 Phase 1

**기술 스택**:
- libsignal-android (Kotlin)
- libsignal-client (JavaScript/TypeScript)

**영향받는 기능들**:
- 메시지 전송/수신 → 모두 암호화
- 이미지/파일 전송 → 암호화 후 전송
- 검색 → 클라이언트 측에서만 가능
- 신고 → E2E 암호화 절충안 적용 (아래 참조)

**E2E 암호화 신고 시스템 (절충안)**:
```
원칙: 관리자는 신고된 메시지만 확인 가능 (모든 메시지 접근 불가)

신고 프로세스:
1. 신고자가 클라이언트에서 메시지 복호화
2. 복호화된 내용 + 해시 + 스크린샷 서버 전송
3. 서버는 신고 데이터만 저장 (원본 메시지는 여전히 암호화)
4. 관리자는 신고된 내용만 검토 가능

장점:
- E2E 암호화 원칙 유지
- 관리자가 필요한 내용만 확인
- 해시값으로 무결성 검증
- 스크린샷으로 증거 보강
```

---

### 1.1 사용자 인증 및 연결
- [x] SDK 초기화 (API Key, 서버 URL 설정) - 구현 완료
- [x] 사용자 인증 (JWT 토큰 기반) - 구현 완료
- [ ] 사용자 프로필 관리
  - 프로필 사진 업로드 (최대 10MB)
  - 프로필 사진 다중 해상도 자동 생성
    - Original: 800x800 (프로필 상세)
    - Large: 400x400 (프로필 팝업)
    - Medium: 200x200 (채팅 헤더)
    - Small: 100x100 (채팅 목록/아바타)
  - WebP 포맷 자동 변환 + JPEG 폴백
  - BlurHash 생성 및 저장 (로딩 placeholder)
  - 프로필 사진 삭제 (기본 아바타로 복원)
  - 프로필 정보 조회
- [x] WebSocket 연결/재연결 (자동 재연결 포함) - 구현 완료 (STOMP over SockJS)
- [x] 연결 상태 모니터링 (연결됨, 연결 중, 끊김) - 구현 완료
- [x] 로그아웃 및 연결 해제 - 구현 완료

**구현 우선순위**: 🔴 Phase 1 (MVP) - ✅ 구현 완료

**실제 구현**:
- WebSocket 엔드포인트: `/ws` (STOMP)
- ConnectionInterceptor를 통한 인증 처리
- Rate Limit 필터: localhost 요청 제외, 테스트 환경 제외

**프로필 사진 최적화 전략**:

1. **다중 해상도 (Responsive Images)**
   - 디바이스 해상도에 맞는 이미지 제공
   - 데이터 사용량 95% 절감

2. **WebP 포맷**
   - 파일 크기 30-50% 감소
   - JPEG 폴백으로 호환성 확보

3. **BlurHash**
   - 이미지 로딩 전 placeholder 표시
   - 20-30바이트로 부드러운 UX
   - 체감 로딩 속도 70% 향상

4. **CDN 활용**
   - CloudFront/Cloudflare
   - Edge 서버에서 캐싱
   - 응답 시간 50% 단축

5. **Lazy Loading**
   - 화면에 보이는 이미지만 로드
   - IntersectionObserver 사용
   - 초기 로딩 시간 60% 단축

6. **Redis 캐싱**
   - 자주 조회되는 프로필 정보 캐싱
   - DB 쿼리 부하 90% 감소
   - 캐시 TTL: 1시간

7. **병렬 처리**
   - 서버에서 다중 해상도 동시 생성
   - 업로드 시간 40% 단축
   - Kotlin Coroutines 사용

**데이터 절감 효과**:
```
기존: JPEG 800x800 (~200KB) × 50개 = 10MB
최적화: WebP 100x100 (~5KB) × 50개 = 250KB
절감률: 97.5%
```

**기술 스택**:
- 이미지 처리: Thumbnailator (Kotlin), Sharp (Node.js)
- BlurHash: blurhash (다중 플랫폼)
- CDN: AWS CloudFront, Cloudflare
- 저장소: AWS S3, Google Cloud Storage

---

### 1.2 채널(채팅방) 관리
- [x] 1:1 채팅 채널 생성 - 구현 완료 (ChannelType.DIRECT)
- [x] 그룹 채팅 채널 생성 - 구현 완료 (ChannelType.GROUP, 기본 최대 인원 제한 없음)
- [x] 채널 목록 조회 (페이징 지원) - 구현 완료
- [x] 채널 정보 조회 (참여자, 생성 시간 등) - 구현 완료
- [x] 채널 나가기 - 구현 완료
- [ ] 채널 삭제 (⚙️ Config 설정 가능) - 미구현
  - 1:1 채팅:
    - Mode 1: 한 명 나가면 모두에게서 삭제
    - Mode 2: 각자 개별적으로 나가기 (기본값)
  - 그룹 채팅: 각자 개별적으로 나가기
- [x] 채널 참여자 추가/제거 - 구현 완료 (targetUserIds로 채널 생성 시 초대 가능)

**구현 우선순위**: 
- 1:1 채팅: 🔴 Phase 1 - ✅ 구현 완료
- 그룹 채팅: 🟡 Phase 2 - ✅ 구현 완료 (기본 기능)

**결정 사항**:
- ✅ 그룹 채팅 기본 최대 인원: 3명 (Config 설정 가능)
- ✅ 1:1 채널 삭제: Config로 모드 선택 가능 (기본값: 개별 나가기)
- ✅ 그룹 채널: 항상 개별 나가기

---

### 1.3 메시지 송수신
- [x] 텍스트 메시지 전송 - 구현 완료 (MessageType.TEXT)
- [x] 이미지 전송 - 구현 완료 (MessageType.IMAGE, fileMetadata 지원)
- [x] 파일 전송 - 구현 완료 (MessageType.FILE, fileMetadata 지원)
- [ ] 영상 전송: 지원 안 함 (Phase 1~2) - 계획대로 미구현
- [x] 메시지 수신 실시간 알림 - 구현 완료 (WebSocket + FCM)
- [x] 메시지 히스토리 조회 (페이징) - 구현 완료
- [ ] 메시지 수정 (⚙️ 기본 10분 이내, Config 설정 가능) - 미구현 (edit_history 필드는 있음)
- [ ] 메시지 삭제 (본인 메시지만, "삭제된 메시지입니다" 표시) - 미구현 (status 필드에 DELETED는 있음)
- [x] 읽지 않은 메시지 카운트 - 구현 완료

**구현 우선순위**: 
- 텍스트, 이미지, 히스토리: 🔴 Phase 1 - ✅ 구현 완료
- 파일 전송: 🟡 Phase 2 - ✅ 구현 완료 (메타데이터만 저장, 실제 파일 업로드는 미구현)
- 수정/삭제: 🟡 Phase 2 - ⏳ 부분 구현 (데이터 구조만 준비됨)

**결정 사항**:
- ✅ 이미지: 20MB
- ✅ 일반 파일: 10MB
- ✅ 영상: 지원 안 함 (추후 검토)
- ✅ 메시지 수정: 10분 이내 (기본값, Config 설정 가능)
- ✅ 메시지 삭제: "삭제된 메시지입니다" 표시

---

### 1.4 실시간 상태
- [ ] 읽음 표시 (Read Receipt)
- [ ] 타이핑 인디케이터 (상대방이 입력 중)
- [ ] 온라인/오프라인 상태
- [ ] 마지막 접속 시간

**구현 우선순위**: 🔴 Phase 1

---

## 2. 소개팅 앱 특화 기능

### 2.1 매칭 기반 채팅
- [ ] 매칭 ID 연동 (매칭된 사용자만 채팅 가능)
- [ ] 매칭 상태 확인 (활성, 만료, 취소)
- [ ] 매칭 해제 시 채팅방 처리 (⚙️ Config 설정 가능)
  - Mode 1: 완전 삭제 (기본값)
  - Mode 2: 읽기 전용으로 전환
  - Mode 3: 일정 기간 후 자동 삭제

**구현 우선순위**: 🔴 Phase 1

**결정 사항**:
- ✅ 기본값: 완전 삭제
- ✅ Config로 모드 선택 가능하도록 구현

---

### 2.2 첫 대화 지원
- [ ] 첫 메시지 전송 여부 확인
- [ ] 아이스브레이킹 메시지 템플릿 (선택사항)
- [ ] 매칭 후 대화 시작 제한 시간 (예: 24시간)

**구현 우선순위**: 🟡 Phase 2

**질문**:
- 아이스브레이킹 기능 정말 필요한가요?
- 대화 시작 제한 시간이 지나면 매칭이 자동 해제되나요?

---

### 2.3 안전 기능
- [ ] 사용자 차단 (차단 시 상호 메시지 불가)
- [ ] 차단 목록 관리
- [ ] 메시지 신고 (신고 사유 선택)
  - **E2E 암호화 절충안**: 신고 시 신고자가 메시지를 복호화하여 제공
  - 복호화된 내용 + 해시값 + 스크린샷(선택) 전송
  - 관리자는 신고된 메시지만 확인 (모든 메시지 접근 불가)
  - 전후 메시지 ID로 패턴 분석 가능
- [ ] 사용자 신고
- [ ] 부적절한 컨텐츠 필터링 (욕설, 개인정보 등)
  - **E2E 암호화 고려**: 클라이언트 측 필터링으로 구현
- [ ] 스팸 방지 (단시간 다수 메시지 제한)

**구현 우선순위**: 
- 차단, 신고: 🔴 Phase 1 (안전이 최우선!)
- 컨텐츠 필터링: 🟡 Phase 2
- 스팸 방지: 🟢 Phase 3

**E2E 암호화와의 조화**:
- 신고된 메시지는 신고자가 복호화한 내용을 서버에 전송
- 해시값으로 무결성 검증
- 스크린샷으로 증거 보강
- 관리자용 별도 에스크로 키 사용 안 함 (E2E 원칙 유지)

**신고 시스템 구조**:
```javascript
// 신고 데이터 구조
{
  target_message_id: UUID,
  reported_content: String,      // 신고자가 복호화한 내용
  reported_content_hash: String,  // SHA-256 해시
  screenshot_url: String,         // 스크린샷 (선택)
  context_message_ids: [UUID],    // 전후 메시지 ID
  report_type: 'inappropriate',
  reason: '부적절한 내용입니다'
}
```

**질문**:
- 컨텐츠 필터링은 어느 수준까지? (단순 욕설 필터 vs AI 기반 분석)
- 스팸 제한: 몇 초에 몇 개 메시지까지 허용?

---

### 2.4 프라이버시
- [ ] 메시지 전송 전 상대방 프로필 확인 가능 여부
- [ ] 사진 공유 동의 시스템 (민감한 사진의 경우)
- [ ] 메시지 자동 삭제 옵션 (일정 기간 후)

**구현 우선순위**: 🟢 Phase 3

**질문**:
- 메시지 자동 삭제는 어느 시점? (읽은 후 24시간? 30일?)
- End-to-end 암호화 필요한가요?

---

## 3. 부가 기능

### 3.1 음성 통화 (WebRTC)
- [ ] 1:1 실시간 음성 통화
- [ ] 통화 연결/종료
- [ ] 통화 중 상태 표시
- [ ] 음성 품질 조절
- [ ] 마이크 on/off
- [ ] 스피커/이어폰 전환
- [ ] 통화 기록 (시작 시간, 종료 시간, 통화 시간)

**구현 우선순위**: 🟡 Phase 2

**결정 사항**:
- ✅ 1:1 통화만 지원
- ❌ 그룹 음성 통화 지원 안 함

**기술 스택**:
- WebRTC (실시간 통신)
- TURN/STUN 서버 (NAT 통과)
- Signaling 서버 (연결 설정)

**참고사항**:
- 시각 장애 사용자 접근성 지원 목적
- TalkBack/VoiceOver 완벽 지원 필요
- 음성 명령 인터페이스 고려

### 3.2 알림 (Notifications)
- [ ] 메시지 수신 알림
  - 포그라운드 인앱 배너
  - 백그라운드/종료 상태 푸시 알림
  - E2E 암호화 상태에서 메시지 미리보기
- [ ] FCM (Android) / APNs (iOS) 통합
- [ ] 전역 알림 설정 (켜기/끄기, 소리, 진동, 미리보기)
- [ ] 채널별 알림 설정
- [ ] 방해금지 모드 (시간대 설정)
- [ ] 일시적 음소거 (1시간, 24시간)
- [ ] 앱 배지 관리
- [ ] 알림 액션 (답장, 읽음 표시)

**구현 우선순위**: 
- 기본 알림 (FCM/APNs, 메시지 미리보기): 🔴 Phase 1
- 고급 설정 (채널별, 방해금지, 알림 액션): 🟡 Phase 2

**핵심 요구사항**:
- ✅ **완전한 실시간**: WebSocket + FCM 실시간 알림
- ✅ **메시지 미리보기 필수**: E2E 암호화 유지하면서 내용 표시
- ✅ **모든 메시지 즉시 알림**: 배칭 없음

**📄 상세 문서**: **[notification-system-spec.md](notification-system-spec.md)** 참조
- 앱 상태별 알림 시나리오 (포그라운드/백그라운드/종료)
- E2E 암호화와 푸시 알림 구현 방법 (Silent Push + 복호화)
- iOS Notification Service Extension 구현 가이드
- Android FirebaseMessagingService 구현 가이드
- 성능 최적화 및 30초 제약 준수 방법
- Phase별 구현 계획 (2-3주)

**기술적 핵심**:
- Silent Push로 암호화된 내용 전송
- 백그라운드에서 클라이언트가 복호화
- 로컬 알림 생성 (복호화된 내용 포함)
- iOS 30초 / Android 10초 제약 준수

---

### 3.3 미디어 관리
### 3.3 미디어 관리
- [ ] 이미지 썸네일 생성
- [ ] 이미지 미리보기
- [ ] 파일 다운로드 진행률
- [ ] 미디어 캐싱 (로컬)

**구현 우선순위**: 🟡 Phase 2

---

### 3.4 검색
- [ ] 채널 내 메시지 검색 (클라이언트 측 검색만 가능)
- [ ] 채널 검색 (채널명, 참여자)

**구현 우선순위**: 🟢 Phase 3

**E2E 암호화 고려**:
- 메시지 내용은 암호화되어 있어 서버에서 검색 불가
- 클라이언트에서 로컬 DB를 검색하는 방식으로 구현
- 성능 고려 필요 (대량 메시지 검색 시)

---

### 3.5 데이터 관리
- [ ] 로컬 데이터 캐싱 (오프라인 지원)
- [ ] 데이터 동기화
- [ ] 캐시 정리
- [ ] 데이터 백업/복원
- [ ] 메시지 자동 삭제 (⚙️ Config에서 설정한 보관 기간 이후)

**구현 우선순위**: 🟡 Phase 2

**메시지 보관 기간**:
- 1시간 단위로 설정 가능 (예: 24시간, 7일, 30일 등)
- 기본값: null (무제한)
- 서버와 클라이언트 모두에서 자동 삭제 처리

---

## 4. 관리자 기능 (선택사항)

- [ ] 모든 채팅방 모니터링
- [ ] 신고된 메시지 검토
- [ ] 사용자 강제 차단
- [ ] 통계 (활성 채팅방 수, 메시지 수 등)

**구현 우선순위**: 🟢 Phase 3

---

## 7. SDK Config 설정 항목

SDK 초기화 시 커스터마이징 가능한 설정들 (⚙️ 표시된 항목들):

```kotlin
ChatSDK.initialize(
    config = ChatConfig(
        // 필수
        apiKey: String,
        serverUrl: String,
        
        // 메시지 관련 (⚙️)
        messageEditTimeLimit: Duration = 10.minutes,
        messageRetentionPeriod: Duration? = null, // null = 무제한, 1시간 단위로 설정 가능
        maxImageSize: Long = 20.MB,
        maxFileSize: Long = 10.MB,
        
        // 채널 관련 (⚙️)
        maxGroupSize: Int = 3,
        directChannelDeleteMode: ChannelDeleteMode = ChannelDeleteMode.INDIVIDUAL,
        
        // 매칭 관련 (⚙️)
        matchReleaseMode: MatchReleaseMode = MatchReleaseMode.DELETE,
        
        // 기타
        enableLogging: Boolean = false,
        reconnectAttempts: Int = 5
    )
)
```

### 설정 가능한 모드들

**ChannelDeleteMode** (1:1 채널 삭제 모드)
- `INDIVIDUAL`: 각자 개별적으로 나가기 (기본값)
- `DELETE_ALL`: 한 명이 나가면 모두에게서 삭제

**MatchReleaseMode** (매칭 해제 시 채팅방 처리)
- `DELETE`: 완전 삭제 (기본값)
- `READ_ONLY`: 읽기 전용으로 전환
- `AUTO_DELETE`: 일정 기간 후 자동 삭제 (기간은 별도 설정)

**메시지 보관 기간 예시**:
```kotlin
// 24시간 후 자동 삭제
messageRetentionPeriod = 24.hours

// 7일 후 자동 삭제
messageRetentionPeriod = 7.days

// 30일 후 자동 삭제
messageRetentionPeriod = 30.days

// 무제한 보관
messageRetentionPeriod = null
```

---

## 8. Phase별 개발 계획

### 🔴 Phase 1 - MVP (8~10주 예상)
**목표**: 기본 채팅 + E2E 암호화 + 안전 기능

#### 핵심 기능
- [x] Signal Protocol E2E 암호화
  - 키 쌍 생성 및 관리
  - 키 교환 (X3DH)
  - Double Ratchet 암호화
- [x] 사용자 인증 및 연결
  - SDK 초기화
  - JWT 인증
  - WebSocket 연결/재연결
  - 연결 상태 모니터링
- [x] 1:1 채팅 채널 관리
  - 채널 생성
  - 채널 목록 조회
  - 채널 정보 조회
  - 채널 나가기
- [x] 메시지 송수신 (기본)
  - 텍스트 메시지 전송/수신
  - 이미지 전송 (20MB, 암호화)
  - 메시지 히스토리 조회 (페이징)
  - 읽지 않은 메시지 카운트
- [x] 실시간 상태
  - 읽음 표시
  - 타이핑 인디케이터
  - 온라인/오프라인 상태
  - 마지막 접속 시간
- [x] 매칭 기반 채팅
  - 매칭 ID 연동
  - 매칭 상태 확인
  - 매칭 해제 시 처리 (Config 모드)
- [x] 안전 기능 (기본)
  - 사용자 차단
  - 차단 목록 관리
  - 메시지 신고 (E2E 대응)
  - 사용자 신고
- [x] 푸시 알림
  - FCM 연동
  - 기본 알림 전송

#### 인프라
- [x] Kotlin + Spring Boot 백엔드
- [x] WebSocket 서버
- [x] PostgreSQL (사용자, 채널, 매칭)
- [x] MongoDB (메시지 - 암호화된 데이터)
- [x] Redis (세션, Pub/Sub, 캐싱)
- [x] 기본 로컬 캐싱 (AsyncStorage/SQLite)

#### 산출물
- 1:1 채팅 가능한 기본 SDK
- E2E 암호화 적용
- 안전 기능 (차단, 신고)
- ReactNative 테스트 앱

**Phase 1 완료 기준**: 소개팅 앱에서 매칭된 두 사용자가 안전하게 1:1 채팅할 수 있음

---

### 🟡 Phase 2 - 확장 기능 (6~8주 예상)
**목표**: 그룹 채팅 + 미디어 + 음성 통화 + 고급 기능

#### 추가 기능
- [ ] 그룹 채팅
  - 그룹 채널 생성 (최대 3명, Config 가능)
  - 참여자 추가/제거
  - 그룹 채팅 전용 기능
- [ ] 파일 전송
  - 문서/기타 파일 (10MB, 암호화)
  - 파일 다운로드 진행률
- [ ] 메시지 수정/삭제
  - 10분 이내 수정 (Config 가능)
  - 삭제 시 "삭제된 메시지입니다" 표시
- [ ] 미디어 관리
  - 이미지 썸네일 생성
  - 이미지 미리보기
  - 미디어 로컬 캐싱
- [ ] 데이터 관리
  - 향상된 로컬 캐싱
  - 데이터 동기화
  - 캐시 정리
  - 메시지 자동 삭제 (보관 기간)
- [ ] 알림 고급 기능
  - 채널별 알림 설정
  - 방해 금지 모드
- [ ] 1:1 음성 통화 (WebRTC)
  - 통화 연결/종료
  - 음성 품질 조절
  - 마이크/스피커 제어
  - 통화 기록
  - 접근성 지원 (TalkBack/VoiceOver)
- [ ] 부적절한 컨텐츠 필터링
  - 클라이언트 측 욕설 필터
  - 기본 패턴 매칭

#### 개선사항
- [ ] 성능 최적화
- [ ] 에러 핸들링 강화
- [ ] 로깅 시스템 개선
- [ ] 모니터링 대시보드

**Phase 2 완료 기준**: 그룹 채팅, 파일 전송, 음성 통화까지 지원하는 완전한 커뮤니케이션 SDK

---

### 🟢 Phase 3 - 고급 기능 (4~6주 예상)
**목표**: 검색 + 관리자 도구 + 고급 안전 기능

#### 추가 기능
- [ ] 검색 기능
  - 채널 내 메시지 검색 (클라이언트 측)
  - 채널 검색 (채널명, 참여자)
- [ ] 스팸 방지
  - 메시지 전송 빈도 제한
  - 의심 행동 감지
- [ ] 관리자 기능
  - 채팅방 모니터링
  - 신고된 메시지 검토
  - 사용자 강제 차단
  - 통계 및 분석
- [ ] 첫 대화 지원 (선택사항)
  - 첫 메시지 확인
  - 아이스브레이킹 템플릿
  - 대화 시작 제한 시간
- [ ] 프라이버시 고급 기능
  - 메시지 자동 삭제 옵션 (고급)
  - 데이터 백업/복원
- [ ] SDK 최적화
  - 번들 크기 최적화
  - 메모리 사용 최적화
  - 배터리 효율성 개선

#### 운영 도구
- [ ] 모니터링 시스템 확장
- [ ] 로그 분석 도구
- [ ] A/B 테스트 인프라
- [ ] 성능 메트릭 수집

**Phase 3 완료 기준**: 엔터프라이즈급 기능과 관리 도구를 갖춘 완성형 SDK

---

### 🔵 Phase 4 이후 - 추가 검토 사항
**장기 로드맵**

#### 검토 필요한 기능들
- [ ] 멀티 디바이스 지원
  - 여러 기기에서 동시 로그인
  - 키 동기화
  - 메시지 동기화
- [ ] 영상 통화 (WebRTC)
  - 1:1 영상 통화
  - 화면 공유
- [ ] 번역 기능
  - 실시간 메시지 번역
  - 다국어 지원 확대
- [ ] AI 기능
  - 메시지 자동 완성
  - 감정 분석
  - 대화 추천
- [ ] 고급 미디어
  - 음성 메시지 (녹음해서 전송)
  - GIF, 스티커 지원
  - 위치 공유
- [ ] 봇/자동화
  - 챗봇 통합
  - 자동 응답
- [ ] 분석 도구
  - 사용자 행동 분석
  - 대화 패턴 분석
  - 매칭 성공률 분석

---

### 개발 일정 요약

| Phase | 기간 | 누적 기간 | 핵심 목표 |
|-------|------|-----------|----------|
| Phase 1 | 8~10주 | 8~10주 | MVP (1:1 채팅 + E2E) |
| Phase 2 | 6~8주 | 14~18주 | 그룹 + 미디어 + 음성 통화 |
| Phase 3 | 4~6주 | 18~24주 | 검색 + 관리 도구 |
| **전체** | **18~24주** | **약 4.5~6개월** | **완성형 SDK** |

### Phase별 우선순위 조정 가능 항목

각 Phase 내에서도 우선순위를 조정할 수 있어요:

**Phase 1에서 조정 가능**:
- Signal Protocol 구현 복잡도에 따라 일정 조정
- 푸시 알림은 Phase 2로 이동 가능

**Phase 2에서 조정 가능**:
- 음성 통화를 Phase 3로 이동 가능
- 그룹 채팅을 Phase 1로 앞당길 수 있음

**Phase 3은 선택적**:
- 초기 런칭에 필수는 아님
- 사용자 피드백 받고 결정 가능

---

## 10. AI Agent 활용 전략

### 개요
ChatGPT, Claude, Cursor AI를 각자의 강점에 맞게 활용하여 개발 효율 극대화

---

### 🤖 AI Agent별 강점 및 활용 방안

#### 1. Claude (현재 대화 중)
**강점**:
- 긴 컨텍스트 이해 (200K 토큰)
- 구조화된 문서 작성
- 복잡한 아키텍처 설계
- 신중하고 깊이 있는 분석

**활용 방안**:
- ✅ **프로젝트 기획 및 설계**: 지금처럼 기능 명세서, 아키텍처 설계
- ✅ **문서 작성**: API 문서, 기술 문서, README
- ✅ **코드 리뷰**: 전체 코드베이스 분석 및 개선 제안
- ✅ **복잡한 로직 설계**: Signal Protocol, WebRTC 같은 복잡한 기능 설계
- ✅ **디버깅 전략 수립**: 복잡한 버그 원인 분석

**추천 워크플로우**:
```
1. 새 기능 개발 시작 → Claude와 설계 논의
2. 상세 스펙 작성 → Claude에게 문서화 요청
3. 구현 중 막히는 부분 → Claude에게 전체 맥락 공유하고 해결책 논의
4. 완성 후 → Claude에게 코드 리뷰 요청
```

---

#### 2. ChatGPT (GPT-4)
**강점**:
- 빠른 응답 속도
- 다양한 예제 코드 생성
- 일반적인 프로그래밍 문제 해결
- 웹 검색 기능 (최신 정보)

**활용 방안**:
- ✅ **빠른 코드 스니펫 생성**: 간단한 유틸리티 함수, 헬퍼 클래스
- ✅ **라이브러리 사용법 조회**: "libsignal-android 사용법"
- ✅ **에러 메시지 해결**: 스택 트레이스 붙여넣고 해결책 받기
- ✅ **최신 정보 검색**: "2025년 WebRTC 베스트 프랙티스"
- ✅ **빠른 프로토타이핑**: 개념 증명 코드 빠르게 생성

**추천 워크플로우**:
```
1. 간단한 함수 필요 → ChatGPT에게 즉시 요청
2. 라이브러리 에러 발생 → ChatGPT에 에러 로그 붙여넣기
3. 새로운 기술 조사 → ChatGPT로 최신 정보 검색
4. 간단한 테스트 코드 → ChatGPT로 빠르게 생성
```

---

#### 3. Cursor AI
**강점**:
- IDE 통합 (코드 컨텍스트 이해)
- 실시간 코드 작성 보조
- 멀티파일 편집
- 코드베이스 전체 이해

**활용 방안**:
- ✅ **실제 코드 작성**: Kotlin, TypeScript 코드 실시간 작성
- ✅ **리팩토링**: 여러 파일에 걸친 코드 수정
- ✅ **보일러플레이트 생성**: 반복적인 코드 자동 생성
- ✅ **코드 컨벤션 적용**: 프로젝트 스타일 가이드 자동 적용
- ✅ **테스트 코드 작성**: 기존 코드 보고 테스트 자동 생성

**추천 워크플로우**:
```
1. 새 클래스/파일 생성 → Cursor에게 구조 생성 요청
2. 함수 구현 → Cursor의 자동완성 활용
3. 리팩토링 필요 → Cursor로 여러 파일 동시 수정
4. 테스트 작성 → Cursor에게 테스트 코드 생성 요청
```

**Cursor 설정 팁**:
```json
// .cursorrules 파일 생성 (프로젝트 루트)
{
  "kotlin": {
    "style": "official kotlin coding conventions",
    "framework": "Spring Boot",
    "preferences": [
      "Use coroutines for async operations",
      "Prefer data classes for DTOs",
      "Use meaningful variable names"
    ]
  },
  "typescript": {
    "style": "airbnb",
    "preferences": [
      "Use arrow functions",
      "Prefer const over let",
      "Use async/await over promises"
    ]
  }
}
```

---

### 🔧 MCP (Model Context Protocol) 활용

#### MCP란?
- Anthropic이 만든 AI와 도구 연결 프로토콜
- Claude가 외부 시스템과 연동 가능
- 데이터베이스, API, 파일 시스템 등에 직접 접근

#### 추천 MCP 서버들

**1. Filesystem MCP**
```bash
# 설치
npm install -g @modelcontextprotocol/server-filesystem

# 사용: Claude가 프로젝트 파일을 직접 읽고 수정
```
- Claude가 코드 파일을 직접 읽고 분석
- 여러 파일에 걸친 변경사항 추적
- 자동으로 파일 구조 이해

**2. GitHub MCP**
```bash
# 설치
npm install -g @modelcontextprotocol/server-github

# 사용: Claude가 GitHub 이슈, PR, 커밋 관리
```
- 이슈 자동 생성 및 관리
- PR 설명 자동 작성
- 커밋 메시지 생성

**3. PostgreSQL MCP**
```bash
# 설치
npm install -g @modelcontextprotocol/server-postgres

# 사용: Claude가 데이터베이스 스키마 설계 및 쿼리 작성
```
- 스키마 자동 생성
- 마이그레이션 스크립트 작성
- 쿼리 최적화 제안

**4. Slack MCP** (팀 협업)
```bash
# 설치
npm install -g @modelcontextprotocol/server-slack

# 사용: 개발 진행상황 자동 리포팅
```
- 일일 진행상황 자동 보고
- 에러 발생 시 알림
- 팀원과 기술 논의 요약

**5. 커스텀 MCP 서버 만들기**
```typescript
// 채팅 SDK 전용 MCP 서버 예시
import { MCPServer } from '@modelcontextprotocol/sdk';

const server = new MCPServer({
  name: 'chat-sdk-tools',
  tools: {
    // WebSocket 연결 테스트
    testWebSocket: async (params) => {
      // WebSocket 서버에 연결하고 결과 반환
    },
    
    // Signal Protocol 키 생성 테스트
    testSignalKeys: async (params) => {
      // libsignal로 키 생성 테스트
    },
    
    // 메시지 암호화/복호화 테스트
    testEncryption: async (params) => {
      // E2E 암호화 테스트
    }
  }
});
```

---

### 📋 개발 워크플로우 예시

#### Phase 1 개발 시나리오

**Week 1-2: Signal Protocol 구현**

1. **Claude**: Signal Protocol 아키텍처 설계
   - "Signal Protocol을 Spring Boot에 통합하는 상세 설계서 작성해줘"
   - 키 관리, 저장 방식, API 설계 논의

2. **ChatGPT**: 최신 libsignal 정보 조사
   - "libsignal-android 최신 버전 사용법과 예제 알려줘"
   - 공식 문서 요약 및 주요 API 파악

3. **Cursor**: 실제 코드 구현
   ```kotlin
   // Cursor에서 작성
   // "Signal Protocol 키 관리자 클래스 만들어줘"
   class SignalKeyManager(
       private val keyStore: KeyStore
   ) {
       // Cursor가 자동완성
   }
   ```

4. **MCP (PostgreSQL)**: 키 저장소 스키마 생성
   - Claude + PostgreSQL MCP
   - "사용자 키를 안전하게 저장할 테이블 스키마 설계해줘"

**Week 3-4: WebSocket 서버 구현**

1. **Claude**: WebSocket 아키텍처 설계
   - 재연결 로직, 에러 핸들링 전략 수립

2. **Cursor**: Spring Boot WebSocket 설정
   ```kotlin
   @Configuration
   @EnableWebSocket
   class WebSocketConfig : WebSocketConfigurer {
       // Cursor가 보일러플레이트 생성
   }
   ```

3. **ChatGPT**: WebSocket 테스트 코드
   - "Spring Boot WebSocket 통합 테스트 코드 작성해줘"

4. **커스텀 MCP**: WebSocket 연결 테스트
   - Claude가 MCP 통해 실제 서버 테스트

**Week 5-6: ReactNative SDK 구현**

1. **Claude**: SDK API 인터페이스 설계
   ```typescript
   // API 설계 문서 작성
   interface ChatSDK {
       initialize(config: ChatConfig): Promise<void>
       connect(): Promise<void>
       sendMessage(channelId: string, message: Message): Promise<void>
   }
   ```

2. **Cursor**: TypeScript 코드 구현
   - Claude가 설계한 인터페이스를 Cursor로 구현

3. **ChatGPT**: 에러 핸들링 패턴
   - "React Native에서 WebSocket 에러 핸들링 베스트 프랙티스"

4. **MCP (Filesystem)**: 파일 구조 자동 관리
   - Claude가 프로젝트 구조 이해하고 파일 자동 생성

---

### 🎯 효율성 극대화 전략

#### 1. 일일 워크플로우

**아침 (계획 수립)**
```
1. Claude와 오늘 할 작업 논의
2. Claude에게 상세 작업 계획 작성 요청
3. Cursor로 개발 환경 준비
```

**오전 (개발)**
```
1. Cursor로 코드 작성
2. 막히는 부분 → ChatGPT에 빠르게 질문
3. 복잡한 로직 → Claude와 설계 논의
```

**오후 (개발 + 테스트)**
```
1. Cursor로 계속 코드 작성
2. 테스트 코드 → ChatGPT로 빠르게 생성
3. 통합 테스트 → 커스텀 MCP 활용
```

**저녁 (리뷰 + 문서화)**
```
1. Claude에게 오늘 작성한 코드 리뷰 요청
2. Claude에게 문서 업데이트 요청
3. 내일 계획 수립
```

#### 2. 병렬 작업

여러 AI를 동시에 활용:
```
[Claude] → 복잡한 아키텍처 설계 논의
    ↓
[ChatGPT] → 관련 라이브러리 최신 정보 조사
    ↓
[Cursor] → 실제 코드 작성
    ↓
[Claude + MCP] → 자동 테스트 및 검증
```

#### 3. 컨텍스트 공유 전략

각 AI에게 프로젝트 컨텍스트 제공:

**Claude용 컨텍스트 파일** (`claude-context.md`)
```markdown
# 프로젝트 개요
- 채팅 SDK 개발
- Kotlin + Spring Boot 백엔드
- ReactNative SDK
- E2E 암호화 (Signal Protocol)

# 현재 진행상황
- Phase 1 Week 3
- WebSocket 서버 구현 중
- 완료: Signal Protocol 통합

# 현재 이슈
- WebSocket 재연결 로직 복잡도
- 메모리 누수 의심
```

**Cursor용 설정** (`.cursorrules`)
```
Project: Chat SDK
Stack: Kotlin, Spring Boot, TypeScript, React Native
Conventions:
- Use Kotlin coroutines
- Follow Signal Protocol patterns
- Test-driven development
Current Focus: WebSocket server implementation
```

#### 4. 코드 리뷰 파이프라인

```
1. Cursor로 코드 작성
2. ChatGPT로 빠른 스타일 체크
3. Claude로 깊이 있는 리뷰
   - 보안 취약점
   - 아키텍처 패턴
   - 성능 이슈
4. 피드백 반영 후 다시 Cursor로 수정
```

---

### 🛠️ 추가 도구 추천

#### 1. GitHub Copilot
- Cursor와 함께 사용
- 실시간 코드 자동완성
- 주석만 작성하면 코드 생성

#### 2. Tabnine
- AI 코드 어시스턴트
- 팀 학습 기능 (팀의 코딩 스타일 학습)

#### 3. Continue.dev
- 오픈소스 Copilot 대안
- 로컬에서 실행 가능
- 프라이버시 보호

#### 4. Aider
- CLI 기반 AI 페어 프로그래밍
- Git과 깊은 통합
- 전체 코드베이스 이해

```bash
# 설치
pip install aider-chat

# 사용
aider --model claude-3-sonnet
# "WebSocket 서버에 재연결 로직 추가해줘"
```

#### 5. Codeium
- 무료 AI 코드 어시스턴트
- 70+ 언어 지원
- IDE 플러그인

---

### 📊 효율성 측정

AI 활용 전후 비교:

| 작업 | 기존 방식 | AI 활용 | 절감 시간 |
|------|-----------|---------|-----------|
| 보일러플레이트 코드 | 30분 | 5분 | 83% ↓ |
| API 문서 작성 | 2시간 | 30분 | 75% ↓ |
| 버그 디버깅 | 3시간 | 1시간 | 67% ↓ |
| 테스트 코드 작성 | 1시간 | 20분 | 67% ↓ |
| 코드 리뷰 | 1시간 | 30분 | 50% ↓ |

**예상 전체 개발 시간 단축**: 40-50%

---

### ⚠️ 주의사항

1. **AI 맹신 금지**
   - AI 코드는 반드시 직접 리뷰
   - 보안 관련 코드는 특히 주의

2. **컨텍스트 관리**
   - 각 AI에게 최신 정보 제공
   - 프로젝트 진행상황 동기화

3. **API 비용 관리**
   - Claude API, GPT-4 API 사용량 모니터링
   - 무료 티어 활용 전략

4. **코드 품질 유지**
   - AI가 생성한 코드도 테스트 작성
   - 코드 리뷰 프로세스 유지

---

## 5. 구현 완료 기능

### 5.1 API Rate Limiting

**구현 상태**: ✅ 구현 완료 (Phase 1)

**실제 구현 내용**:
- Rate Limit 필터: `RateLimitFilter` (Bucket4j + Redis 사용)
- 엔드포인트별 제한 설정: `application.yml`에서 관리
- 제외 대상:
  - localhost 요청 (127.0.0.1, ::1)
  - 테스트 환경 (`test` profile)
  - Health check 엔드포인트 (`/api/v1/health`)
  - Swagger UI 및 API 문서 엔드포인트
  - `X-Test-Script: true` 헤더가 있는 요청

**Rate Limit 정책** (실제 구현):
- 인증 API (로그인, 회원가입): IP 주소 기반
- 기타 API: 사용자 ID 기반
- 응답 헤더: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

**설정 (application.yml)**:
```yaml
rate-limit:
  enabled: true
  limits:
    login:
      requests: 100
      window-minutes: 1
    register:
      requests: 50
      window-hours: 1
    message:
      requests: 1000
      window-minutes: 1
    general:
      requests: 500
      window-minutes: 1
    websocket:
      requests: 100
      window-minutes: 1
```

---

## 6. 미결정 사항 / 논의 필요

### 6.1 기술적 결정
- [x] 음성/영상 통화 기능 필요 여부
  - ✅ 실시간 음성 통화 (WebRTC) - 1:1만 지원
  - ❌ 그룹 음성 통화 - 지원 안 함
  - ❌ 영상 통화 - 지원 안 함
- [x] End-to-end 암호화 필요 여부
  - ✅ Phase 1부터 Signal Protocol 사용
  - 고려사항:
    - 서버는 메시지 내용을 볼 수 없음 (암호화된 데이터만 전달)
    - 서버 측 메시지 검색 불가 → 클라이언트 측 검색만 가능
    - 신고 기능 복잡해짐 (암호화된 메시지를 어떻게 검토할지)
    - 멀티 디바이스 지원 시 키 동기화 필요
    - 개발 시간 +2~4주 예상
- [x] 메시지 보관 기간
  - ✅ 제한 방식: 1시간 단위로 클라이언트에서 설정 가능 (⚙️ Config)
  - 예: 24시간, 48시간, 7일, 30일, 무제한 등
  - 서버 저장소 비용 절감 효과
- [ ] 멀티 디바이스 지원 여부 (추후 고려)

### 6.2 비즈니스 정책
- [x] 그룹 채팅 최대 인원수 (기본 3명, Config 설정 가능)
- [x] 파일 크기 제한 (이미지 20MB, 파일 10MB, 영상 미지원)
- [x] 메시지 수정/삭제 정책 (수정 10분, 삭제 시 표시)
- [x] 매칭 해제 후 채팅방 처리 (완전 삭제, Config로 모드 선택 가능)
- [x] 채널 삭제 정책 (Config로 모드 선택 가능)

### 6.3 우선순위 조정
현재 Phase 구분:
- 🔴 Phase 1 (MVP): 기본 채팅 + 안전 기능
- 🟡 Phase 2: 그룹 채팅 + 미디어 기능
- 🟢 Phase 3: 고급 기능 + 관리자 도구

---

## 다음 단계

1. [ ] 미결정 사항 결정하기
2. [ ] Phase 1 기능 최종 확정
3. [ ] API 명세서 작성
4. [ ] 데이터베이스 스키마 설계
5. [ ] 프로젝트 구조 설계

---

## 메모
- 

---

## 11. 성능 최적화 전략

### 10.1 프로필 이미지 최적화

**목표**: 채팅 앱의 빠른 UX와 낮은 데이터 사용량

**최적화 전략**:

| 최적화 기법 | 개선 효과 | 구현 방법 |
|-----------|----------|-----------|
| 다중 해상도 | 데이터 95% 절감 | 4가지 사이즈 자동 생성 |
| WebP 포맷 | 파일 크기 30-50% 감소 | 자동 변환 + JPEG 폴백 |
| BlurHash | 체감 속도 70% 향상 | Placeholder 즉시 표시 |
| CDN 캐싱 | 응답 시간 50% 단축 | CloudFront/Cloudflare |
| Lazy Loading | 초기 로딩 60% 단축 | IntersectionObserver |
| Redis 캐싱 | DB 부하 90% 감소 | 프로필 정보 1시간 캐싱 |

**이미지 해상도 전략**:
```
Original: 800x800 (WebP, 85% quality, ~45KB) - 프로필 상세
Large:    400x400 (WebP, 80% quality, ~20KB) - 프로필 팝업
Medium:   200x200 (WebP, 75% quality, ~10KB) - 채팅 헤더
Small:    100x100 (WebP, 70% quality, ~5KB)  - 채팅 목록/아바타
JPEG:     800x800 (JPEG, 85% quality, ~80KB) - Safari 구버전 폴백
```

**BlurHash 활용**:
```javascript
// 1. 서버에서 업로드 시 BlurHash 생성
const blurhash = encode(imageData, width, height, 4, 4);
// 결과: "LEHV6nWB2yk8pyo0adR*.7kCMdnj" (20-30바이트)

// 2. DB에 저장
user.profile_image_blurhash = blurhash;

// 3. 클라이언트에서 즉시 렌더링 (< 10ms)
<BlurHashImage hash={blurhash} width={100} height={100} />

// 4. 실제 이미지 로드 (백그라운드)
<Image src={thumbnailUrl} />
```

**CDN 설정**:
```
CloudFront/Cloudflare:
- Cache-Control: public, max-age=31536000 (1년)
- Gzip/Brotli 압축 활성화
- HTTP/2 사용
- TLS 1.3 최신 암호화
- Edge Locations: 전 세계 분산
```

**데이터 절감 효과**:
```
기존 방식 (단일 JPEG):
- 800x800 JPEG: ~200KB
- 채팅 목록 50개: 10MB

최적화 후 (WebP + 다중 해상도):
- 100x100 WebP: ~5KB
- 채팅 목록 50개: 250KB
- 절감률: 97.5%
```

**성능 목표**:
- BlurHash 렌더링: < 10ms
- 이미지 로딩 (CDN): < 100ms
- 프로필 조회 (Redis): < 20ms
- 업로드 처리: < 2s

### 10.2 메시지 최적화

- [ ] MongoDB 인덱스 최적화 (channel_id, created_at 복합)
- [ ] Redis Pub/Sub으로 실시간 전송
- [ ] 메시지 페이징 (20개 단위)
- [ ] 읽지 않은 카운트 Redis 캐싱

### 10.3 연결 최적화

- [ ] WebSocket 연결 풀링
- [ ] 자동 재연결 (Exponential Backoff)
- [ ] Heartbeat (30초 간격)
- [ ] 네트워크 상태 감지 및 적응

### 10.4 데이터베이스 최적화

**PostgreSQL**:
- 적절한 인덱스 설정
- 커넥션 풀 최적화 (HikariCP)
- VACUUM 자동화

**MongoDB**:
- 복합 인덱스 (channel_id, created_at)
- TTL 인덱스 (자동 삭제)
- Read Preference 최적화

**Redis**:
- TTL 설정으로 메모리 관리
- 키 네이밍 컨벤션
- 적절한 데이터 구조 선택 (Hash, Set, Sorted Set)

---
