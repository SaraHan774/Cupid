# WebSocket 연결 상태 추적 기능

## 개요

Redis를 이용한 WebSocket 연결 상태 추적 시스템을 구현했습니다. 이 시스템은 사용자의 온라인/오프라인 상태를 실시간으로 추적하고 관리합니다.

## 주요 기능

### 1. 연결 상태 추적
- **WebSocket 연결 시**: Redis에 온라인 상태 저장 (5분 TTL)
- **WebSocket 해제 시**: Redis에서 온라인 상태 제거
- **하트비트**: 30초마다 연결 상태 갱신
- **자동 정리**: 1분마다 타임아웃된 연결 정리

### 2. 온라인 상태 관리
- 사용자별 온라인 상태 확인
- 온라인 사용자 목록 조회
- 채널별 온라인 멤버 필터링
- 온라인 상태 통계 수집

### 3. 실시간 이벤트 처리
- 타이핑 인디케이터
- 읽음 표시
- 사용자 상태 변경 알림

## 구현된 컴포넌트

### 1. ConnectionInterceptor
**파일**: `src/main/kotlin/com/august/cupid/websocket/ConnectionInterceptor.kt`

WebSocket 연결/해제 시 Redis에 온라인 상태를 저장/삭제하는 인터셉터입니다.

**주요 기능**:
- JWT 토큰에서 사용자 ID 추출
- Redis에 온라인 상태 저장 (`user:online:{userId}`)
- 세션 정보 저장 (`session:{userId}`)
- 연결 해제 시 상태 제거

**Redis 키 구조**:
```
user:online:{userId} = "true" (TTL: 5분)
session:{userId} = "ws-session-{userId}-{timestamp}-{random}" (TTL: 1시간)
```

### 2. OnlineStatusService
**파일**: `src/main/kotlin/com/august/cupid/service/OnlineStatusService.kt`

온라인 상태 관리를 위한 서비스 클래스입니다.

**주요 메서드**:
- `isUserOnline(userId)`: 사용자 온라인 상태 확인
- `getUsersOnlineStatus(userIds)`: 여러 사용자 상태 일괄 확인
- `getOnlineUsers()`: 모든 온라인 사용자 목록 조회
- `processHeartbeat(userId)`: 하트비트 처리
- `processDisconnection(userId)`: 연결 해제 처리

### 3. WebSocketMessageHandler
**파일**: `src/main/kotlin/com/august/cupid/websocket/WebSocketMessageHandler.kt`

WebSocket 메시지 처리를 담당하는 핸들러입니다.

**주요 기능**:
- 하트비트 메시지 처리 (`/app/heartbeat`)
- 타이핑 인디케이터 처리 (`/app/typing`)
- 읽음 표시 처리 (`/app/read`)
- 연결 상태 모니터링

### 4. WebSocketEventListener
**파일**: `src/main/kotlin/com/august/cupid/websocket/WebSocketEventListener.kt`

WebSocket 연결 이벤트를 처리하는 리스너입니다.

**이벤트 처리**:
- `SessionConnectedEvent`: 연결 시 이벤트 처리
- `SessionDisconnectEvent`: 연결 해제 시 이벤트 처리

### 5. WebSocketConnectionMonitor
**파일**: `src/main/kotlin/com/august/cupid/websocket/WebSocketConnectionMonitor.kt`

주기적으로 연결 상태를 모니터링하는 스케줄러입니다.

**스케줄링**:
- 1분마다: 하트비트 타임아웃 확인
- 5분마다: 연결 상태 통계 수집

### 6. OnlineStatusController
**파일**: `src/main/kotlin/com/august/cupid/controller/OnlineStatusController.kt`

온라인 상태 확인을 위한 REST API 엔드포인트입니다.

**API 엔드포인트**:
- `GET /api/v1/online-status/users/{userId}`: 특정 사용자 온라인 상태 확인
- `POST /api/v1/online-status/users/batch`: 여러 사용자 상태 일괄 확인
- `GET /api/v1/online-status/users`: 모든 온라인 사용자 목록
- `GET /api/v1/online-status/stats`: 온라인 상태 통계
- `POST /api/v1/online-status/channels/{channelId}/members`: 채널 온라인 멤버 조회

## 설정 변경사항

### 1. WebSocketConfig 업데이트
**파일**: `src/main/kotlin/com/august/cupid/config/WebSocketConfig.kt`

ConnectionInterceptor를 WebSocket 엔드포인트에 등록했습니다.

```kotlin
registry.addEndpoint("/ws")
    .setAllowedOrigins("*")
    .addInterceptors(connectionInterceptor)  // 추가됨
    .withSockJS()
```

### 2. 스케줄링 활성화
**파일**: `src/main/kotlin/com/august/cupid/CupidApplication.kt`

`@EnableScheduling` 어노테이션을 추가하여 스케줄러를 활성화했습니다.

## 테스트

### 1. 자동 테스트 스크립트
**파일**: `test-websocket-connection.sh`

구현된 기능들을 자동으로 테스트하는 스크립트입니다.

**실행 방법**:
```bash
./test-websocket-connection.sh
```

**테스트 항목**:
- 서버 시작 확인
- WebSocket 엔드포인트 접근 가능성
- 온라인 상태 API 동작
- Redis 연결 확인
- 테스트 HTML 페이지 접근 가능성

### 2. 수동 테스트 페이지
**파일**: `src/main/resources/static/websocket-test.html`

브라우저에서 WebSocket 연결을 테스트할 수 있는 HTML 페이지입니다.

**접근 방법**: `http://localhost:8080/websocket-test.html`

**테스트 기능**:
- WebSocket 연결/해제
- 하트비트 전송 (수동/자동)
- 온라인 상태 확인
- 실시간 로그 확인

## 사용 방법

### 1. 서버 시작
```bash
./gradlew bootRun
```

### 2. Redis 시작
```bash
redis-server
```

### 3. 테스트 실행
```bash
# 자동 테스트
./test-websocket-connection.sh

# 수동 테스트
# 브라우저에서 http://localhost:8080/websocket-test.html 열기
```

### 4. Redis 모니터링
```bash
# Redis 모니터링
redis-cli monitor

# 온라인 상태 키 확인
redis-cli keys "user:online:*"
redis-cli keys "session:*"

# 특정 사용자 상태 확인
redis-cli get "user:online:test-user-1"
```

## API 사용 예시

### 1. 사용자 온라인 상태 확인
```bash
curl http://localhost:8080/api/v1/online-status/users/test-user-1
```

**응답**:
```json
{
  "userId": "test-user-1",
  "isOnline": true,
  "timestamp": 1698345600000
}
```

### 2. 온라인 사용자 목록 조회
```bash
curl http://localhost:8080/api/v1/online-status/users
```

**응답**:
```json
{
  "onlineUsers": ["test-user-1", "test-user-2"],
  "totalOnlineUsers": 2,
  "timestamp": 1698345600000
}
```

### 3. 여러 사용자 상태 일괄 확인
```bash
curl -X POST http://localhost:8080/api/v1/online-status/users/batch \
  -H "Content-Type: application/json" \
  -d '{"userIds": ["test-user-1", "test-user-2", "test-user-3"]}'
```

**응답**:
```json
{
  "statusMap": {
    "test-user-1": true,
    "test-user-2": false,
    "test-user-3": true
  },
  "totalUsers": 3,
  "onlineUsers": 2,
  "timestamp": 1698345600000
}
```

## WebSocket 메시지 형식

### 1. 하트비트 전송
```javascript
// 클라이언트 → 서버
stompClient.send('/app/heartbeat', {}, JSON.stringify({
  timestamp: Date.now(),
  clientInfo: navigator.userAgent
}));

// 서버 → 클라이언트
{
  "timestamp": 1698345600000,
  "serverTime": 1698345600000,
  "status": "ok"
}
```

### 2. 타이핑 인디케이터
```javascript
// 클라이언트 → 서버
stompClient.send('/app/typing', {}, JSON.stringify({
  channelId: "channel-123",
  isTyping: true
}));

// 서버 → 채널 구독자들
{
  "userId": "test-user-1",
  "channelId": "channel-123",
  "isTyping": true,
  "timestamp": 1698345600000
}
```

### 3. 읽음 표시
```javascript
// 클라이언트 → 서버
stompClient.send('/app/read', {}, JSON.stringify({
  messageId: "msg-123",
  channelId: "channel-123"
}));

// 서버 → 채널 구독자들
{
  "userId": "test-user-1",
  "messageId": "msg-123",
  "channelId": "channel-123",
  "readAt": 1698345600000
}
```

## 성능 고려사항

### 1. Redis 최적화
- **TTL 설정**: 자동 만료로 메모리 사용량 최적화
- **키 네이밍**: 일관된 패턴으로 관리 효율성 향상
- **배치 처리**: 여러 사용자 상태를 한 번에 확인

### 2. 연결 관리
- **하트비트 간격**: 30초로 네트워크 부하 최소화
- **타임아웃 처리**: 60초 타임아웃으로 정확한 상태 관리
- **자동 정리**: 주기적으로 불필요한 연결 정리

### 3. 확장성
- **수평 확장**: Redis를 통한 다중 서버 지원
- **로드 밸런싱**: WebSocket 연결 분산 가능
- **모니터링**: 연결 상태 통계 수집

## 보안 고려사항

### 1. 인증
- **JWT 토큰**: WebSocket 연결 시 토큰 검증
- **사용자 식별**: 토큰에서 사용자 ID 추출
- **권한 확인**: 연결 권한이 있는 사용자만 허용

### 2. 데이터 보호
- **상태 정보**: 민감하지 않은 연결 상태만 저장
- **세션 관리**: 세션 ID로 연결 추적
- **자동 만료**: TTL로 데이터 자동 정리

## 문제 해결

### 1. 일반적인 문제

**Q: WebSocket 연결이 실패합니다**
A: 
- JWT 토큰이 유효한지 확인
- 서버가 실행 중인지 확인
- Redis가 실행 중인지 확인

**Q: 온라인 상태가 정확하지 않습니다**
A:
- 하트비트가 정상적으로 전송되는지 확인
- Redis TTL 설정 확인
- 연결 해제 이벤트가 정상 처리되는지 확인

**Q: API 응답이 느립니다**
A:
- Redis 연결 상태 확인
- 네트워크 지연 확인
- 서버 리소스 사용량 확인

### 2. 로그 확인
```bash
# 애플리케이션 로그 확인
tail -f logs/application.log

# Redis 로그 확인
tail -f /var/log/redis/redis-server.log

# 연결 상태 모니터링
redis-cli monitor
```

## 향후 개선 사항

### 1. 기능 확장
- [ ] 사용자별 연결 디바이스 수 제한
- [ ] 지역별 온라인 상태 통계
- [ ] 연결 품질 모니터링

### 2. 성능 최적화
- [ ] Redis 클러스터 지원
- [ ] 연결 상태 캐싱 최적화
- [ ] 배치 처리 개선

### 3. 모니터링 강화
- [ ] 메트릭 수집 (Prometheus)
- [ ] 알림 시스템 연동
- [ ] 대시보드 구축

---

## 요약

WebSocket 연결 상태 추적 기능이 성공적으로 구현되었습니다. 이 시스템은 Redis를 활용하여 사용자의 온라인/오프라인 상태를 실시간으로 추적하고 관리하며, 하트비트 메커니즘을 통해 연결 상태의 정확성을 보장합니다.

구현된 주요 컴포넌트:
- ✅ ConnectionInterceptor: 연결 상태 추적
- ✅ OnlineStatusService: 온라인 상태 관리
- ✅ WebSocketMessageHandler: 메시지 처리
- ✅ WebSocketEventListener: 이벤트 처리
- ✅ WebSocketConnectionMonitor: 상태 모니터링
- ✅ OnlineStatusController: REST API
- ✅ 테스트 페이지 및 스크립트

이제 사용자들이 WebSocket을 통해 실시간으로 연결 상태를 추적하고 관리할 수 있습니다.
