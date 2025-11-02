/**
 * Signal Protocol E2E 암호화 통합 예제
 * 
 * 이 파일은 Signal Protocol API를 사용하는 전체 플로우를 보여줍니다.
 * 
 * 사용법:
 * 1. 필요한 패키지 설치: npm install
 * 2. API_URL을 서버 주소로 변경
 * 3. 사용자 인증 정보 설정
 * 4. 실행
 */

const API_URL = 'http://localhost:8080/api/v1';

// ============================================
// 1. 인증
// ============================================

/**
 * 사용자 로그인
 */
async function login(username, password) {
  const response = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });

  if (!response.ok) {
    throw new Error('로그인 실패');
  }

  const data = await response.json();
  return data.token;
}

/**
 * 사용자 등록
 */
async function register(username, password, email) {
  const response = await fetch(`${API_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, email })
  });

  if (!response.ok) {
    throw new Error('등록 실패');
  }

  return await response.json();
}

// ============================================
// 2. 키 관리
// ============================================

/**
 * Signal Protocol 키 생성
 */
async function generateKeys(token, password) {
  const response = await fetch(
    `${API_URL}/encryption/keys/generate?password=${encodeURIComponent(password)}`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || '키 생성 실패');
  }

  return await response.json();
}

/**
 * 키 상태 확인
 */
async function checkKeyStatus(token) {
  const response = await fetch(`${API_URL}/encryption/keys/status`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error('키 상태 확인 실패');
  }

  return await response.json();
}

/**
 * 다른 사용자의 공개키 번들 조회
 */
async function getPublicKeyBundle(token, userId) {
  const response = await fetch(`${API_URL}/encryption/keys/${userId}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || '공개키 번들 조회 실패');
  }

  return await response.json();
}

// ============================================
// 3. 세션 관리
// ============================================

/**
 * 세션 초기화
 */
async function initializeSession(token, recipientId) {
  const response = await fetch(`${API_URL}/encryption/key-exchange/initiate`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      recipientId: recipientId,
      recipientDeviceId: 1
    })
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || '세션 초기화 실패');
  }

  return await response.json();
}

/**
 * 세션 상태 확인
 */
async function checkSessionStatus(token, peerId) {
  const response = await fetch(`${API_URL}/encryption/session/${peerId}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error('세션 상태 확인 실패');
  }

  const data = await response.json();
  return data.data.hasSession;
}

/**
 * 세션 삭제
 */
async function deleteSession(token, peerId) {
  const response = await fetch(`${API_URL}/encryption/session/${peerId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error('세션 삭제 실패');
  }

  return await response.json();
}

// ============================================
// 4. 메시지 암호화/복호화
// ============================================

/**
 * 메시지 암호화 (디버그/테스트용)
 * 프로덕션에서는 클라이언트 측에서 암호화해야 합니다.
 */
async function encryptMessage(token, recipientId, plaintext) {
  const response = await fetch(`${API_URL}/encryption/encrypt`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      recipientId: recipientId,
      plaintext: plaintext
    })
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || '메시지 암호화 실패');
  }

  return await response.json();
}

/**
 * 메시지 복호화 (디버그/테스트용)
 * 프로덕션에서는 클라이언트 측에서 복호화해야 합니다.
 */
async function decryptMessage(token, senderId, ciphertext, messageType = 1) {
  const response = await fetch(`${API_URL}/encryption/decrypt`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      senderId: senderId,
      ciphertext: ciphertext,
      messageType: messageType
    })
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || '메시지 복호화 실패');
  }

  return await response.json();
}

// ============================================
// 5. 키 백업/복구
// ============================================

/**
 * 키 백업 생성
 */
async function createBackup(token, backupPassword, expirationDays = 90) {
  const response = await fetch(`${API_URL}/keys/backup`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      backupPassword: backupPassword,
      expirationDays: expirationDays,
      metadata: JSON.stringify({
        device_name: 'Example Device',
        app_version: '1.0.0'
      })
    })
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || '백업 생성 실패');
  }

  return await response.json();
}

/**
 * 백업 목록 조회
 */
async function getBackupList(token) {
  const response = await fetch(`${API_URL}/keys/backup`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error('백업 목록 조회 실패');
  }

  return await response.json();
}

/**
 * 백업 복구
 */
async function restoreBackup(token, backupId, backupPassword) {
  const response = await fetch(`${API_URL}/keys/backup/restore`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      backupId: backupId,
      backupPassword: backupPassword
    })
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || '백업 복구 실패');
  }

  return await response.json();
}

/**
 * 백업 삭제
 */
async function deleteBackup(token, backupId) {
  const response = await fetch(`${API_URL}/keys/backup/${backupId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error('백업 삭제 실패');
  }

  return await response.json();
}

// ============================================
// 6. 에러 처리
// ============================================

/**
 * Rate Limit 처리
 */
async function handleRateLimit(response) {
  if (response.status === 429) {
    const retryAfter = response.headers.get('Retry-After');
    const resetTime = response.headers.get('X-RateLimit-Reset');
    
    console.warn('Rate Limit 초과. 재시도 시간:', retryAfter, '초');
    console.warn('제한 해제 시간:', new Date(parseInt(resetTime)));
    
    // 지연 후 재시도
    await new Promise(resolve => setTimeout(resolve, retryAfter * 1000));
    return true;
  }
  
  return false;
}

/**
 * API 호출 래퍼 (에러 처리 포함)
 */
async function apiCall(fn, retries = 3) {
  for (let i = 0; i < retries; i++) {
    try {
      const response = await fn();
      
      // Rate Limit 처리
      if (await handleRateLimit(response)) {
        continue; // 재시도
      }
      
      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || `HTTP ${response.status}`);
      }
      
      return await response.json();
    } catch (error) {
      if (i === retries - 1) throw error;
      
      // 지수 백오프
      const delay = Math.pow(2, i) * 1000;
      console.warn(`재시도 중... (${i + 1}/${retries})`, delay, 'ms 후');
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
}

// ============================================
// 7. 전체 플로우 예제
// ============================================

/**
 * 전체 통합 플로우 예제
 */
async function completeFlow() {
  try {
    console.log('=== Signal Protocol E2E 암호화 통합 예제 ===\n');

    // 1. 사용자 등록
    console.log('1. 사용자 등록 중...');
    const alicePassword = 'AlicePassword123!';
    const bobPassword = 'BobPassword123!';
    
    const aliceUser = await register('alice', alicePassword, 'alice@example.com');
    const bobUser = await register('bob', bobPassword, 'bob@example.com');
    
    console.log('Alice ID:', aliceUser.id);
    console.log('Bob ID:', bobUser.id);

    // 2. 로그인
    console.log('\n2. 로그인 중...');
    const aliceToken = await login('alice', alicePassword);
    const bobToken = await login('bob', bobPassword);
    console.log('로그인 완료');

    // 3. 키 생성
    console.log('\n3. 키 생성 중...');
    await generateKeys(aliceToken, alicePassword);
    await generateKeys(bobToken, bobPassword);
    console.log('키 생성 완료');

    // 4. 키 상태 확인
    console.log('\n4. 키 상태 확인...');
    const aliceKeyStatus = await checkKeyStatus(aliceToken);
    const bobKeyStatus = await checkKeyStatus(bobToken);
    console.log('Alice 키 상태:', aliceKeyStatus.data);
    console.log('Bob 키 상태:', bobKeyStatus.data);

    // 5. 세션 초기화
    console.log('\n5. 세션 초기화 중...');
    await initializeSession(aliceToken, bobUser.id);
    console.log('세션 초기화 완료');

    // 6. 세션 상태 확인
    console.log('\n6. 세션 상태 확인...');
    const hasSession = await checkSessionStatus(aliceToken, bobUser.id);
    console.log('세션 존재 여부:', hasSession);

    // 7. 메시지 암호화 및 전송
    console.log('\n7. 메시지 암호화 및 전송 중...');
    const message = 'Hello, Bob! This is a secret message.';
    const encrypted = await encryptMessage(aliceToken, bobUser.id, message);
    console.log('암호화된 메시지:', encrypted.data.ciphertext.substring(0, 50) + '...');

    // 8. 메시지 복호화
    console.log('\n8. 메시지 복호화 중...');
    const decrypted = await decryptMessage(
      bobToken,
      aliceUser.id,
      encrypted.data.ciphertext,
      encrypted.data.messageType
    );
    console.log('복호화된 메시지:', decrypted.data.plaintext);

    // 9. 백업 생성
    console.log('\n9. 백업 생성 중...');
    const backupPassword = 'BackupPassword123!';
    const backup = await createBackup(aliceToken, backupPassword, 90);
    console.log('백업 ID:', backup.data.backupId);

    // 10. 백업 목록 조회
    console.log('\n10. 백업 목록 조회...');
    const backupList = await getBackupList(aliceToken);
    console.log('백업 개수:', backupList.data.totalCount);

    console.log('\n=== 모든 작업 완료! ===');
  } catch (error) {
    console.error('오류 발생:', error.message);
    console.error(error);
  }
}

// ============================================
// 8. 실행
// ============================================

// Node.js 환경에서 실행
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    login,
    register,
    generateKeys,
    checkKeyStatus,
    getPublicKeyBundle,
    initializeSession,
    checkSessionStatus,
    deleteSession,
    encryptMessage,
    decryptMessage,
    createBackup,
    getBackupList,
    restoreBackup,
    deleteBackup,
    completeFlow
  };
}

// 브라우저 환경에서 실행 (직접 호출)
if (typeof window !== 'undefined') {
  window.encryptionFlow = {
    login,
    register,
    generateKeys,
    checkKeyStatus,
    getPublicKeyBundle,
    initializeSession,
    checkSessionStatus,
    deleteSession,
    encryptMessage,
    decryptMessage,
    createBackup,
    getBackupList,
    restoreBackup,
    deleteBackup,
    completeFlow
  };
}

// 직접 실행 (Node.js)
if (require.main === module) {
  completeFlow().catch(console.error);
}

