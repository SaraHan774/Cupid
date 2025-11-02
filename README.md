# Cupid - Real-time Chat Application

Spring Boot 3.5 + Kotlin 기반의 실시간 채팅 플랫폼

**모든 문서는 자동 생성됩니다** → http://localhost:8080/swagger-ui.html

## 🚀 Quick Start (3 Commands)

```bash
docker-compose up -d              # 1. DB 시작
./gradlew bootRun                 # 2. 앱 실행
open http://localhost:8080/swagger-ui.html  # 3. 문서 보기
```

## 🧪 API 통합 테스트

모든 엔드포인트를 한 번에 테스트:

```bash
python3 test_all_endpoints.py
```

**특징**:
- 자동 인증: 기존 사용자가 있으면 재사용, 없으면 자동 가입
- Rate Limit 제외: 테스트 스크립트 실행 시 자동으로 제한 해제
- 23개 엔드포인트 테스트: 인증, 알림, 채널, 메시지 등

## 📚 Where to Find Information

| 정보 | 위치 |
|-----|-----|
| **API 문서** (자동 생성) | http://localhost:8080/swagger-ui.html |
| **WebSocket 테스트** | http://localhost:8080/websocket-test.html |
| **Health Check** | http://localhost:8080/api/v1/health |
| **코드 설명** | 각 클래스의 KDoc 주석 |
| **설정** | `application.yml` |

## 🛠️ Stack

- **Backend**: Kotlin + Spring Boot 3.5.7
- **Database**: PostgreSQL, MongoDB, Redis
- **Real-time**: WebSocket (STOMP over SockJS)
- **Auth**: JWT
- **Docs**: SpringDoc OpenAPI (자동 생성)

## 🔐 End-to-End Encryption (Signal Protocol)

### Encryption Flow

1. **Key Generation**: User generates identity key pair, signed pre-key, and one-time pre-keys
   ```bash
   POST /api/v1/encryption/keys/generate
   ```

2. **Key Exchange (X3DH)**: Two users exchange public keys to establish an encrypted session
   ```bash
   POST /api/v1/encryption/key-exchange/initiate
   ```

3. **Message Encryption**: Messages are encrypted using Double Ratchet algorithm before sending
   - Forward Secrecy: Each message uses a unique key
   - Post-Compromise Security: Keys are automatically rotated

4. **Message Decryption**: Recipient decrypts messages using their private keys

### Key Exchange Protocol

**X3DH (Extended Triple Diffie-Hellman)**:
1. Alice requests Bob's pre-key bundle
2. Server returns Bob's identity key, signed pre-key, and one-time pre-key
3. Alice uses these keys to derive a shared secret
4. Encrypted session is established for future messages

**Double Ratchet**:
- Each message generates new encryption keys
- Automatic key rotation for forward secrecy
- Session keys are derived from shared secret

### Security Features

- ✅ **Forward Secrecy**: Past messages remain secure even if current keys are compromised
- ✅ **Automatic Key Rotation**: Signed pre-keys rotated weekly, one-time pre-keys replenished automatically
- ✅ **Security Monitoring**: All encryption operations are logged and audited
- ✅ **MITM Detection**: Identity key fingerprint verification
- ✅ **Private Key Protection**: All private keys encrypted with AES-256-GCM before storage

### Encryption Health Check

```bash
GET /api/v1/health
```

Returns encryption service status including:
- Database connectivity for key storage
- MongoDB connectivity for audit logs
- EncryptionService availability
- Feature status (key generation, storage, audit logging)

## 🐛 문제 해결

### WebSocket 연결 안됨?
```bash
redis-cli ping  # PONG이 나와야 함
```

### Database 연결 안됨?
```bash
docker-compose ps  # 모두 Up 상태여야 함
```

### Encryption Issues?

#### 키 생성 실패
- **원인**: 데이터베이스 연결 문제 또는 비밀번호 강도 부족
- **해결**: 
  ```bash
  # Health check로 데이터베이스 상태 확인
  curl http://localhost:8080/api/v1/health
  
  # 비밀번호는 최소 12자, 대소문자/숫자/특수문자 포함 필요
  ```

#### 세션 초기화 실패
- **원인**: 수신자의 키가 아직 생성되지 않았거나 만료됨
- **해결**: 
  ```bash
  # 수신자 키 상태 확인
  GET /api/v1/encryption/keys/{userId}
  
  # 키가 없으면 먼저 키 생성 필요
  POST /api/v1/encryption/keys/generate
  ```

#### 메시지 암호화/복호화 실패
- **원인**: 세션이 초기화되지 않았거나 키가 만료됨
- **해결**:
  ```bash
  # 세션 상태 확인
  GET /api/v1/encryption/session/{recipientId}/status
  
  # 세션이 없으면 키 교환 필요
  POST /api/v1/encryption/key-exchange/initiate
  ```

#### 암호화 서비스 상태 확인
```bash
curl http://localhost:8080/api/v1/health | jq '.services.encryption'
```

#### 관리자 대시보드에서 통계 확인
```bash
# 키 통계
GET /api/v1/admin/dashboard/keys/statistics

# 사용자 암호화 상태
GET /api/v1/admin/dashboard/users/encryption-status

# 서비스 메트릭
GET /api/v1/admin/dashboard/metrics
```

자세한 내용은 로그 확인: 애플리케이션 시작 시 콘솔에 모든 정보가 나옵니다.

---

## 📖 Additional Documentation

- **API Documentation**: Swagger UI at http://localhost:8080/swagger-ui.html
- **Project Specifications**: See `documents/specifications/` folder
  - `chat-sdk-spec.md` - Complete SDK specification
  - `database-schema.md` - Database schema documentation
  - `notification-system-spec.md` - Notification system specification
- **Task Lists**: See `documents/tasks/` folder
  - `today-tasks.md` - Current development tasks

**That's it!**

- API 문서: Swagger UI가 자동 생성 (항상 최신)
- 코드 설명: 각 클래스의 KDoc 주석
- 설정: `application.yml` 파일
- 프로젝트 스펙: `documents/specifications/` 폴더
