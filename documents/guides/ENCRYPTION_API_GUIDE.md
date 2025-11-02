# Signal Protocol E2E 암호화 API 통합 가이드

## 📖 목차

1. [개요](#개요)
2. [빠른 시작](#빠른-시작)
3. [통합 플로우](#통합-플로우)
4. [API 엔드포인트 상세](#api-엔드포인트-상세)
5. [에러 처리](#에러-처리)
6. [보안 고려사항](#보안-고려사항)
7. [베스트 프랙티스](#베스트-프랙티스)
8. [트러블슈팅](#트러블슈팅)

---

## 개요

이 가이드는 Signal Protocol 기반 End-to-End 암호화 API를 프론트엔드 애플리케이션에 통합하는 방법을 설명합니다.

### 주요 기능

- ✅ **X3DH 키 교환**: 안전한 초기 키 교환 프로토콜
- ✅ **Double Ratchet**: Forward Secrecy 및 Post-Compromise Security
- ✅ **키 백업/복구**: 새 기기에서 키 복구 지원
- ✅ **자동 키 회전**: 보안 강화를 위한 주기적 키 갱신
- ✅ **보안 감사 로깅**: 모든 암호화 작업 추적

### 기술 스택

- **프론트엔드**: JavaScript/TypeScript, React Native
- **백엔드**: Kotlin + Spring Boot
- **암호화 라이브러리**: Signal Protocol (libsignal-client)

---

## 빠른 시작

### 1. 인증

모든 암호화 API는 JWT 인증이 필요합니다.

```javascript
const token = await authenticate(username, password);
const headers = {
  'Authorization': `Bearer ${token}`,
  'Content-Type': 'application/json'
};
```

### 2. 키 생성

사용자가 처음 가입하거나 새 기기를 등록할 때 키를 생성합니다.

```javascript
// 키 생성
const response = await fetch('http://localhost:8080/api/v1/encryption/keys/generate?password=MySecurePassword123!', {
  method: 'POST',
  headers: headers
});

const result = await response.json();
// result.data: { userId, deviceId, hasIdentityKey, ... }
```

### 3. 키 교환 (세션 초기화)

다른 사용자와 암호화 세션을 시작하려면:

```javascript
// 수신자의 공개키 번들 조회
const bundleResponse = await fetch(`http://localhost:8080/api/v1/encryption/keys/${recipientId}`, {
  method: 'GET',
  headers: headers
});

const bundle = await bundleResponse.json().data;

// 세션 초기화
const initResponse = await fetch('http://localhost:8080/api/v1/encryption/key-exchange/initiate', {
  method: 'POST',
  headers: headers,
  body: JSON.stringify({
    recipientId: recipientId,
    recipientDeviceId: 1
  })
});
```

---

## 통합 플로우

### 전체 플로우 다이어그램

```
┌─────────────┐                    ┌─────────────┐
│   Alice     │                    │    Bob      │
└──────┬──────┘                    └──────┬──────┘
       │                                   │
       │ 1. 키 생성 (Alice)                 │
       │ POST /keys/generate               │
       ├───────────────────────────────────┤
       │                                   │
       │ 2. 키 생성 (Bob)                   │
       │ POST /keys/generate               │
       ├───────────────────────────────────┤
       │                                   │
       │ 3. Bob의 공개키 번들 조회            │
       │ GET /keys/{bobId}                 │
       ├───────────────────────────────────┤
       │                                   │
       │ 4. 세션 초기화                     │
       │ POST /key-exchange/initiate        │
       ├───────────────────────────────────┤
       │                                   │
       │ 5. 암호화된 메시지 전송             │
       │ (WebSocket 또는 HTTP POST)        │
       ├───────────────────────────────────┤
       │                                   │
       │ 6. Bob이 메시지 복호화             │
       │                                   │
```

### 단계별 상세 가이드

#### Step 1: 사용자 등록 및 키 생성

```javascript
async function registerUser(username, password) {
  // 1. 사용자 등록
  const registerResponse = await fetch('http://localhost:8080/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, email: `${username}@example.com` })
  });
  
  const user = await registerResponse.json();
  
  // 2. 로그인하여 토큰 획득
  const loginResponse = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  
  const { token } = await loginResponse.json();
  
  // 3. Signal Protocol 키 생성
  const keyResponse = await fetch(`http://localhost:8080/api/v1/encryption/keys/generate?password=${encodeURIComponent(password)}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });
  
  if (!keyResponse.ok) {
    throw new Error('키 생성 실패');
  }
  
  const keyData = await keyResponse.json();
  console.log('키 생성 완료:', keyData);
  
  return { token, userId: user.id };
}
```

#### Step 2: 다른 사용자와 세션 시작

```javascript
async function startEncryptedSession(token, recipientId) {
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };
  
  // 1. 수신자의 공개키 번들 조회
  const bundleResponse = await fetch(`http://localhost:8080/api/v1/encryption/keys/${recipientId}`, {
    method: 'GET',
    headers: headers
  });
  
  if (!bundleResponse.ok) {
    throw new Error('공개키 번들을 가져올 수 없습니다. 수신자가 키를 생성했는지 확인하세요.');
  }
  
  const bundle = await bundleResponse.json().data;
  
  // 2. 세션 초기화
  const sessionResponse = await fetch('http://localhost:8080/api/v1/encryption/key-exchange/initiate', {
    method: 'POST',
    headers: headers,
    body: JSON.stringify({
      recipientId: recipientId,
      recipientDeviceId: 1
    })
  });
  
  if (!sessionResponse.ok) {
    throw new Error('세션 초기화 실패');
  }
  
  const sessionData = await sessionResponse.json();
  console.log('세션 초기화 완료:', sessionData);
  
  return sessionData;
}
```

#### Step 3: 메시지 암호화 및 전송

```javascript
async function sendEncryptedMessage(token, recipientId, plaintext) {
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };
  
  // 1. 메시지 암호화 (프로덕션에서는 클라이언트 측에서 암호화)
  const encryptResponse = await fetch('http://localhost:8080/api/v1/encryption/encrypt', {
    method: 'POST',
    headers: headers,
    body: JSON.stringify({
      recipientId: recipientId,
      plaintext: plaintext
    })
  });
  
  if (!encryptResponse.ok) {
    throw new Error('메시지 암호화 실패');
  }
  
  const encrypted = await encryptResponse.json().data;
  
  // 2. 암호화된 메시지를 채널에 전송
  const messageResponse = await fetch('http://localhost:8080/api/v1/channels/{channelId}/messages', {
    method: 'POST',
    headers: headers,
    body: JSON.stringify({
      encryptedContent: encrypted.ciphertext,
      messageType: 'TEXT'
    })
  });
  
  return await messageResponse.json();
}
```

#### Step 4: 메시지 수신 및 복호화

```javascript
async function receiveAndDecryptMessage(token, senderId, encryptedMessage) {
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };
  
  // 메시지 복호화
  const decryptResponse = await fetch('http://localhost:8080/api/v1/encryption/decrypt', {
    method: 'POST',
    headers: headers,
    body: JSON.stringify({
      senderId: senderId,
      ciphertext: encryptedMessage.encryptedContent,
      messageType: encryptedMessage.messageType
    })
  });
  
  if (!decryptResponse.ok) {
    throw new Error('메시지 복호화 실패');
  }
  
  const decrypted = await decryptResponse.json().data;
  return decrypted.plaintext;
}
```

---

## API 엔드포인트 상세

### 키 관리

#### 1. 키 생성

```http
POST /api/v1/encryption/keys/generate?password={password}
Authorization: Bearer {token}
```

**Rate Limit**: 5 requests/minute/user

**응답**:
```json
{
  "success": true,
  "data": {
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "deviceId": 1,
    "hasIdentityKey": true,
    "hasSignedPreKey": true,
    "signedPreKeyExpiry": "2025-12-02T12:00:00",
    "availableOneTimePreKeys": 100,
    "identityKeyCreatedAt": "2025-11-02T12:00:00"
  }
}
```

#### 2. 공개키 번들 조회

```http
GET /api/v1/encryption/keys/{userId}
Authorization: Bearer {token}
```

**Rate Limit**: 10 requests/minute/user

**응답**:
```json
{
  "success": true,
  "data": {
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "deviceId": 1,
    "registrationId": 12345,
    "identityKey": "base64_encoded_identity_key",
    "signedPreKey": {
      "keyId": 1,
      "publicKey": "base64_encoded_signed_pre_key",
      "signature": "base64_encoded_signature"
    },
    "oneTimePreKey": {
      "keyId": 1,
      "publicKey": "base64_encoded_one_time_pre_key"
    }
  }
}
```

#### 3. 키 상태 조회

```http
GET /api/v1/encryption/keys/status
Authorization: Bearer {token}
```

---

### 세션 관리

#### 1. 세션 초기화

```http
POST /api/v1/encryption/key-exchange/initiate
Authorization: Bearer {token}
Content-Type: application/json

{
  "recipientId": "123e4567-e89b-12d3-a456-426614174000",
  "recipientDeviceId": 1
}
```

**Rate Limit**: 100 requests/hour/user

#### 2. 세션 상태 확인

```http
GET /api/v1/encryption/session/{peerId}
Authorization: Bearer {token}
```

#### 3. 세션 삭제

```http
DELETE /api/v1/encryption/session/{peerId}
Authorization: Bearer {token}
```

---

### 메시지 암호화/복호화

#### 1. 메시지 암호화 (디버그/테스트용)

```http
POST /api/v1/encryption/encrypt
Authorization: Bearer {token}
Content-Type: application/json

{
  "recipientId": "123e4567-e89b-12d3-a456-426614174000",
  "plaintext": "Hello, Bob!"
}
```

**⚠️ 주의**: 프로덕션에서는 클라이언트 측에서 암호화해야 합니다.

#### 2. 메시지 복호화 (디버그/테스트용)

```http
POST /api/v1/encryption/decrypt
Authorization: Bearer {token}
Content-Type: application/json

{
  "senderId": "123e4567-e89b-12d3-a456-426614174000",
  "ciphertext": "encrypted_message_base64",
  "messageType": 1
}
```

---

### 키 백업/복구

#### 1. 백업 생성

```http
POST /api/v1/keys/backup
Authorization: Bearer {token}
Content-Type: application/json

{
  "backupPassword": "MyBackupPassword123!",
  "expirationDays": 90,
  "metadata": "{\"device_name\": \"iPhone 13\"}"
}
```

**Rate Limit**: 5 requests/hour/user

**응답**:
```json
{
  "success": true,
  "data": {
    "backupId": "backup-uuid",
    "userId": "user-uuid",
    "createdAt": "2025-11-02T12:00:00",
    "expiresAt": "2026-02-01T12:00:00",
    "message": "키 백업이 성공적으로 생성되었습니다."
  }
}
```

#### 2. 백업 복구

```http
POST /api/v1/keys/backup/restore
Authorization: Bearer {token}
Content-Type: application/json

{
  "backupId": "backup-uuid",
  "backupPassword": "MyBackupPassword123!"
}
```

**Rate Limit**: 3 requests/hour/user

#### 3. 백업 목록 조회

```http
GET /api/v1/keys/backup
Authorization: Bearer {token}
```

#### 4. 백업 삭제

```http
DELETE /api/v1/keys/backup/{backupId}
Authorization: Bearer {token}
```

---

## 에러 처리

### HTTP 상태 코드

| 코드 | 의미 | 설명 |
|------|------|------|
| 200 | OK | 요청 성공 |
| 400 | Bad Request | 잘못된 요청 (검증 실패 등) |
| 401 | Unauthorized | 인증 필요 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 429 | Too Many Requests | Rate Limit 초과 |
| 500 | Internal Server Error | 서버 오류 |

### Rate Limit 초과

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1698345600000
Retry-After: 60

{
  "success": false,
  "error": "Rate limit exceeded. Please try again later.",
  "errorCode": "RATE_LIMIT_EXCEEDED"
}
```

### 일반적인 에러 응답 형식

```json
{
  "success": false,
  "error": "Error message",
  "errorCode": "ERROR_CODE",
  "validationErrors": [
    "Field validation error 1",
    "Field validation error 2"
  ]
}
```

### 에러 처리 예제

```javascript
async function handleApiCall(apiCall) {
  try {
    const response = await apiCall();
    
    if (!response.ok) {
      const errorData = await response.json();
      
      if (response.status === 429) {
        const retryAfter = response.headers.get('Retry-After');
        console.warn(`Rate limit exceeded. Retry after ${retryAfter} seconds.`);
        // 재시도 로직
        return;
      }
      
      if (response.status === 401) {
        // 토큰 갱신 또는 재로그인
        await refreshToken();
        return;
      }
      
      throw new Error(errorData.error || 'API 호출 실패');
    }
    
    return await response.json();
  } catch (error) {
    console.error('API 호출 중 오류:', error);
    throw error;
  }
}
```

---

## 보안 고려사항

### 1. 비밀번호 관리

- ✅ 키 생성 시 강력한 비밀번호 사용 (최소 12자)
- ✅ 백업 비밀번호는 사용자 비밀번호와 별도로 설정
- ✅ 비밀번호는 절대 서버에 평문으로 저장하지 않음
- ✅ 클라이언트 측에서 비밀번호 입력 시 마스킹 처리

### 2. 키 저장

```javascript
// ✅ 좋은 예: 안전한 저장소 사용
// iOS: Keychain
// Android: EncryptedSharedPreferences
// React Native: react-native-keychain

import * as Keychain from 'react-native-keychain';

async function storeKeys(userId, keys) {
  await Keychain.setGenericPassword(
    `signal_keys_${userId}`,
    JSON.stringify(keys),
    {
      service: 'com.example.chat.signal',
      accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED
    }
  );
}
```

### 3. 네트워크 보안

- ✅ 항상 HTTPS 사용
- ✅ 인증 토큰은 Secure Cookie 또는 메모리에만 저장
- ✅ 민감한 데이터는 로그에 기록하지 않음

### 4. 키 백업 보안

- ✅ 백업 비밀번호는 사용자가 안전하게 보관
- ✅ 백업은 만료 시간 설정 (기본 90일)
- ✅ 백업 복구 후 재사용 불가 (보안상 일회용)

---

## 베스트 프랙티스

### 1. 초기화 순서

```javascript
async function initializeEncryption(userId, password) {
  // 1. 키 상태 확인
  const status = await checkKeyStatus(userId);
  
  // 2. 키가 없으면 생성
  if (!status.hasIdentityKey) {
    await generateKeys(userId, password);
  }
  
  // 3. 키 상태 재확인
  const updatedStatus = await checkKeyStatus(userId);
  
  // 4. Pre-key 부족 시 경고
  if (updatedStatus.availableOneTimePreKeys < 20) {
    console.warn('Pre-key가 부족합니다. 자동 보충을 기다리세요.');
  }
}
```

### 2. 세션 관리

```javascript
// 세션 상태를 캐시하여 불필요한 API 호출 방지
const sessionCache = new Map();

async function ensureSession(userId, peerId) {
  const cacheKey = `${userId}_${peerId}`;
  
  if (sessionCache.has(cacheKey)) {
    return sessionCache.get(cacheKey);
  }
  
  // 세션 확인
  const hasSession = await checkSessionStatus(peerId);
  
  if (!hasSession) {
    // 세션 초기화
    await initializeSession(peerId);
  }
  
  sessionCache.set(cacheKey, true);
  return true;
}
```

### 3. 에러 재시도

```javascript
async function retryApiCall(apiCall, maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await apiCall();
    } catch (error) {
      if (i === maxRetries - 1) throw error;
      
      // 지수 백오프
      const delay = Math.pow(2, i) * 1000;
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
}
```

### 4. 키 백업 전략

```javascript
// 주기적으로 백업 생성
async function scheduleBackup() {
  const backupInterval = 7 * 24 * 60 * 60 * 1000; // 7일
  
  setInterval(async () => {
    try {
      await createBackup({
        backupPassword: await getBackupPassword(),
        expirationDays: 90
      });
      console.log('자동 백업 완료');
    } catch (error) {
      console.error('자동 백업 실패:', error);
    }
  }, backupInterval);
}
```

---

## 트러블슈팅

### 문제 1: 키 생성 실패

**증상**: `400 Bad Request` 또는 `500 Internal Server Error`

**해결 방법**:
1. 비밀번호 강도 확인 (최소 12자, 대소문자/숫자/특수문자 포함)
2. 데이터베이스 연결 상태 확인 (`GET /api/v1/health`)
3. Rate Limit 확인 (429 에러인지 확인)

### 문제 2: 세션 초기화 실패

**증상**: `404 Not Found` 또는 `Recipient keys not found`

**해결 방법**:
1. 수신자가 키를 생성했는지 확인
2. 수신자 키 상태 조회 (`GET /api/v1/encryption/keys/{userId}`)
3. 수신자에게 키 생성을 요청

### 문제 3: 메시지 복호화 실패

**증상**: `Decryption failed` 또는 `Session invalid`

**해결 방법**:
1. 세션 상태 확인 (`GET /api/v1/encryption/session/{peerId}`)
2. 세션이 없으면 재초기화
3. 키가 만료되었는지 확인 (키 재생성 필요할 수 있음)

### 문제 4: Rate Limit 초과

**증상**: `429 Too Many Requests`

**해결 방법**:
1. `Retry-After` 헤더 확인
2. 요청 빈도 줄이기
3. 캐싱 활용하여 불필요한 API 호출 감소

### 문제 5: 백업 복구 실패

**증상**: `Backup password incorrect` 또는 `Backup expired`

**해결 방법**:
1. 백업 비밀번호 재확인
2. 백업 만료 여부 확인 (`GET /api/v1/keys/backup`)
3. 새 백업 생성 필요

---

## 추가 리소스

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **메트릭 가이드**: `documents/guides/METRICS_GUIDE.md`
- **보안 베스트 프랙티스**: `documents/guides/ENCRYPTION_SECURITY_BEST_PRACTICES.md`
- **프로젝트 스펙**: `documents/specifications/chat-sdk-spec.md`

---

**문의사항이나 문제가 있으면 이슈를 등록하거나 팀에 문의하세요.**

