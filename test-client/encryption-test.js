// ============================================
// 암호화 API 테스트 함수들
// ============================================

// API_BASE와 accessToken은 config.js와 app.js에서 정의됨 (전역 변수)
// 만약 정의되지 않았다면 기본값 사용 (로컬 개발 환경)
if (typeof window.API_BASE === 'undefined') {
    window.API_BASE = 'http://localhost:8080/api/v1';
}

// 전역 변수 접근 헬퍼
function getApiBase() {
    return window.API_BASE || 'http://localhost:8080/api/v1';
}

function getAccessToken() {
    return window.accessToken || null;
}

// API 호출 래퍼 (에러 처리 포함)
async function apiCall(fn, retries = 3) {
    for (let i = 0; i < retries; i++) {
        try {
            const response = await fn();
            
            if (response.status === 429) {
                const retryAfter = parseInt(response.headers.get('Retry-After') || '60');
                addLog(`Rate Limit 초과. ${retryAfter}초 후 재시도...`, 'warn');
                await new Promise(r => setTimeout(r, retryAfter * 1000));
                continue;
            }
            
            if (!response.ok) {
                let errorData;
                try {
                    errorData = await response.json();
                } catch (e) {
                    errorData = { error: `HTTP ${response.status}: ${response.statusText}` };
                }
                
                const errorMessage = errorData.error || errorData.message || `HTTP ${response.status}`;
                const errorCode = errorData.errorCode || '';
                
                throw new Error(`${errorMessage}${errorCode ? ` (${errorCode})` : ''}`);
            }
            
            return await response.json();
        } catch (error) {
            if (i === retries - 1) throw error;
            const delay = Math.pow(2, i) * 1000;
            addLog(`재시도 중... (${i + 1}/${retries})`, 'warn');
            await new Promise(r => setTimeout(r, delay));
        }
    }
}

// 모든 함수를 window 객체에 명시적으로 할당하여 전역 스코프에 노출
window.apiCall = apiCall;

// 로그 추가 함수
function addLog(message, type = 'info', targetId = 'fullFlowResult') {
    const logContainer = document.getElementById(targetId);
    if (!logContainer) return;
    
    const entry = document.createElement('div');
    entry.className = `log-entry log-${type}`;
    const timestamp = new Date().toLocaleTimeString();
    
    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '❌';
    if (type === 'warn') icon = '⚠️';
    
    entry.innerHTML = `<span class="log-time">[${timestamp}]</span> ${icon} ${message}`;
    logContainer.appendChild(entry);
    logContainer.scrollTop = logContainer.scrollHeight;
}

// 결과 표시 함수
function showResult(elementId, data, success = true) {
    const element = document.getElementById(elementId);
    if (!element) return;
    
    element.innerHTML = `
        <div class="result-${success ? 'success' : 'error'}">
            <pre>${JSON.stringify(data, null, 2)}</pre>
        </div>
    `;
}

// 키 생성 테스트
async function testGenerateKeys() {
    const password = document.getElementById('keyPassword').value;
    if (!password || password.length < 12) {
        showResult('keyStatusResult', { error: '비밀번호는 최소 12자여야 합니다.' }, false);
        return;
    }
    
    try {
        addLog('키 생성 중...', 'info', 'keyStatusResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(
            `${getApiBase()}/encryption/keys/generate?password=${encodeURIComponent(password)}`,
            {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
            }
        ));
        addLog('키 생성 완료!', 'success', 'keyStatusResult');
        showResult('keyStatusResult', response, true);
    } catch (error) {
        const errorMsg = error.message || '알 수 없는 오류가 발생했습니다.';
        addLog(`❌ 오류: ${errorMsg}`, 'error', 'keyStatusResult');
        addLog('💡 팁: 서버 로그를 확인하거나 브라우저 개발자 도구의 Network 탭에서 응답을 확인하세요.', 'info', 'keyStatusResult');
        showResult('keyStatusResult', { 
            error: errorMsg,
            details: error.stack || '서버 로그를 확인하세요.'
        }, false);
        console.error('키 생성 오류 상세:', error);
    }
}

// 키 상태 확인 테스트
async function testCheckKeyStatus() {
    try {
        addLog('키 상태 확인 중...', 'info', 'keyStatusResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/encryption/keys/status`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
        }));
        addLog('키 상태 확인 완료!', 'success', 'keyStatusResult');
        showResult('keyStatusResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'keyStatusResult');
        showResult('keyStatusResult', { error: error.message }, false);
    }
}

// 세션 초기화 테스트
async function testInitializeSession() {
    const recipientId = document.getElementById('recipientUserId').value;
    if (!recipientId) {
        showResult('sessionResult', { error: '수신자 User ID를 입력하세요.' }, false);
        return;
    }
    
    try {
        addLog('세션 초기화 중...', 'info', 'sessionResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/encryption/key-exchange/initiate`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ recipientId, recipientDeviceId: 1 })
        }));
        addLog('세션 초기화 완료!', 'success', 'sessionResult');
        showResult('sessionResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'sessionResult');
        showResult('sessionResult', { error: error.message }, false);
    }
}

// 세션 상태 확인 테스트
async function testCheckSession() {
    const peerId = document.getElementById('recipientUserId').value;
    if (!peerId) {
        showResult('sessionResult', { error: '수신자 User ID를 입력하세요.' }, false);
        return;
    }
    
    try {
        addLog('세션 상태 확인 중...', 'info', 'sessionResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/encryption/session/${peerId}`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
        }));
        addLog('세션 상태 확인 완료!', 'success', 'sessionResult');
        showResult('sessionResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'sessionResult');
        showResult('sessionResult', { error: error.message }, false);
    }
}

// 세션 삭제 테스트
async function testDeleteSession() {
    const peerId = document.getElementById('recipientUserId').value;
    if (!peerId) {
        showResult('sessionResult', { error: '수신자 User ID를 입력하세요.' }, false);
        return;
    }
    
    try {
        addLog('세션 삭제 중...', 'info', 'sessionResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/encryption/session/${peerId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
        }));
        addLog('세션 삭제 완료!', 'success', 'sessionResult');
        showResult('sessionResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'sessionResult');
        showResult('sessionResult', { error: error.message }, false);
    }
}

// 메시지 암호화 테스트
async function testEncryptMessage() {
    const recipientId = document.getElementById('encryptRecipientId').value;
    const plaintext = document.getElementById('plaintextMessage').value;
    
    if (!recipientId || !plaintext) {
        showResult('encryptionResult', { error: '수신자 ID와 메시지를 입력하세요.' }, false);
        return;
    }
    
    try {
        addLog('메시지 암호화 중...', 'info', 'encryptionResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/encryption/encrypt`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ recipientId, plaintext })
        }));
        addLog('암호화 완료!', 'success', 'encryptionResult');
        // 암호화된 메시지를 저장 (복호화용)
        window.lastEncryptedMessage = response.data;
        showResult('encryptionResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'encryptionResult');
        showResult('encryptionResult', { error: error.message }, false);
    }
}

// 메시지 복호화 테스트
async function testDecryptMessage() {
    const senderId = document.getElementById('encryptRecipientId').value;
    
    if (!window.lastEncryptedMessage || !senderId) {
        showResult('encryptionResult', { error: '먼저 메시지를 암호화하세요.' }, false);
        return;
    }
    
    try {
        addLog('메시지 복호화 중...', 'info', 'encryptionResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/encryption/decrypt`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({
                senderId: senderId,
                ciphertext: window.lastEncryptedMessage.ciphertext,
                messageType: window.lastEncryptedMessage.messageType || 1
            })
        }));
        addLog('복호화 완료!', 'success', 'encryptionResult');
        showResult('encryptionResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'encryptionResult');
        showResult('encryptionResult', { error: error.message }, false);
    }
}

// 백업 생성 테스트
async function testCreateBackup() {
    const backupPassword = document.getElementById('backupPassword').value;
    const expirationDays = parseInt(document.getElementById('backupExpirationDays').value) || 90;
    
    if (!backupPassword || backupPassword.length < 12) {
        showResult('backupResult', { error: '백업 비밀번호는 최소 12자여야 합니다.' }, false);
        return;
    }
    
    try {
        addLog('백업 생성 중...', 'info', 'backupResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/keys/backup`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({
                backupPassword,
                expirationDays,
                metadata: JSON.stringify({ device_name: 'Browser Test', app_version: '1.0.0' })
            })
        }));
        addLog('백업 생성 완료!', 'success', 'backupResult');
        window.lastBackupId = response.data.backupId;
        showResult('backupResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'backupResult');
        showResult('backupResult', { error: error.message }, false);
    }
}

// 백업 목록 조회 테스트
async function testGetBackupList() {
    try {
        addLog('백업 목록 조회 중...', 'info', 'backupResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/keys/backup`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
        }));
        addLog('백업 목록 조회 완료!', 'success', 'backupResult');
        showResult('backupResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'backupResult');
        showResult('backupResult', { error: error.message }, false);
    }
}

// 백업 복구 테스트
async function testRestoreBackup() {
    const backupPassword = document.getElementById('backupPassword').value;
    const backupId = prompt('백업 ID를 입력하세요:', window.lastBackupId || '');
    
    if (!backupId || !backupPassword) {
        showResult('backupResult', { error: '백업 ID와 비밀번호를 입력하세요.' }, false);
        return;
    }
    
    try {
        addLog('백업 복구 중...', 'info', 'backupResult');
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/keys/backup/restore`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ backupId, backupPassword })
        }));
        addLog('백업 복구 완료!', 'success', 'backupResult');
        showResult('backupResult', response, true);
    } catch (error) {
        addLog(`오류: ${error.message}`, 'error', 'backupResult');
        showResult('backupResult', { error: error.message }, false);
    }
}

// 전체 플로우 자동 실행
async function runCompleteEncryptionFlow() {
    const logContainer = document.getElementById('fullFlowResult');
    logContainer.innerHTML = '';
    
    try {
        addLog('=== Signal Protocol E2E 암호화 통합 플로우 시작 ===', 'info');
        
        // Step 1: Health Check
        addLog('Step 1: Health Check...', 'info');
        const healthRes = await apiCall(() => fetch(`${getApiBase()}/health`));
        addLog(`✓ 서버 상태: ${healthRes.status}`, 'success');
        
        // Step 2: 키 상태 확인
        addLog('Step 2: 키 상태 확인...', 'info');
        const token = getAccessToken();
        if (!token) {
            addLog('⚠️ 로그인이 필요합니다.', 'warn');
            return;
        }
        const keyStatus = await apiCall(() => fetch(`${getApiBase()}/encryption/keys/status`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
        }));
        addLog(`✓ 키 상태: ${keyStatus.data.hasIdentityKey ? '키 있음' : '키 없음'}`, 'success');
        
        if (!keyStatus.data.hasIdentityKey) {
            addLog('⚠️ 키가 없습니다. 먼저 키를 생성하세요.', 'warn');
            return;
        }
        
        // Step 3: 테스트용 두 번째 사용자 필요
        addLog('Step 3: 테스트를 위해 다른 사용자와 세션을 초기화해야 합니다.', 'info');
        addLog('다른 사용자의 ID를 입력하거나, 먼저 암호화 테스트 섹션에서 수동으로 테스트하세요.', 'warn');
        
        addLog('\n=== 플로우 완료 ===', 'success');
    } catch (error) {
        addLog(`\n❌ 오류 발생: ${error.message}`, 'error');
    }
}

// 암호화 로그 지우기
function clearEncryptionLogs() {
    document.getElementById('fullFlowResult').innerHTML = '';
}

// ============================================
// API 테스트 함수들
// ============================================

// Health Check 테스트
async function testHealthCheck() {
    try {
        const response = await apiCall(() => fetch(`${getApiBase()}/health`));
        showResult('healthResult', response, true);
    } catch (error) {
        showResult('healthResult', { error: error.message }, false);
    }
}

// 현재 사용자 정보 조회
async function testGetCurrentUser() {
    try {
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/auth/me`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
        }));
        showResult('userInfoResult', response, true);
    } catch (error) {
        showResult('userInfoResult', { error: error.message }, false);
    }
}

// 채널 생성 테스트
async function testCreateChannel() {
    const channelName = document.getElementById('testChannelName').value || null;
    const channelType = document.getElementById('testChannelType').value;
    
    try {
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/channels`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ name: channelName, type: channelType })
        }));
        showResult('channelApiResult', response, true);
    } catch (error) {
        showResult('channelApiResult', { error: error.message }, false);
    }
}

// 채널 목록 조회 테스트
async function testGetChannels() {
    try {
        const token = getAccessToken();
        if (!token) {
            throw new Error('로그인이 필요합니다.');
        }
        const response = await apiCall(() => fetch(`${getApiBase()}/channels`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
        }));
        showResult('channelApiResult', response, true);
    } catch (error) {
        showResult('channelApiResult', { error: error.message }, false);
    }
}

// 모든 테스트 함수를 window 객체에 명시적으로 할당
window.testGenerateKeys = testGenerateKeys;
window.testCheckKeyStatus = testCheckKeyStatus;
window.testInitializeSession = testInitializeSession;
window.testCheckSession = testCheckSession;
window.testDeleteSession = testDeleteSession;
window.testEncryptMessage = testEncryptMessage;
window.testDecryptMessage = testDecryptMessage;
window.testCreateBackup = testCreateBackup;
window.testGetBackupList = testGetBackupList;
window.testRestoreBackup = testRestoreBackup;
window.runCompleteEncryptionFlow = runCompleteEncryptionFlow;
window.clearEncryptionLogs = clearEncryptionLogs;
window.testHealthCheck = testHealthCheck;
window.testGetCurrentUser = testGetCurrentUser;
window.testCreateChannel = testCreateChannel;
window.testGetChannels = testGetChannels;

