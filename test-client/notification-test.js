// Firebase Cloud Messaging 알림 테스트 스크립트
// FCM 토큰 등록, 조회, 삭제 및 푸시 알림 수신 기능을 제공합니다.

// API_BASE는 config.js와 app.js에서 정의됨 (전역 변수)
// 만약 정의되지 않았다면 기본값 사용 (로컬 개발 환경)
if (typeof window.API_BASE === 'undefined') {
    window.API_BASE = 'http://localhost:8080/api/v1';
}
const API_BASE = window.API_BASE;

// 전역 변수
let messaging = null;
let currentFcmToken = null;
let registeredTokenId = null; // 서버에 등록된 토큰 ID

// 페이지 로드 시 Firebase 초기화 및 이벤트 리스너 설정
window.addEventListener('DOMContentLoaded', function() {
    initializeFirebaseMessaging();
    setupNotificationEventListeners();
});

/**
 * Firebase Messaging 초기화
 * firebase-config.js가 로드된 후 실행되어야 합니다.
 */
function initializeFirebaseMessaging() {
    try {
        if (!window.CUPID_FIREBASE_CONFIG) {
            console.error('Firebase 설정이 없습니다. firebase-config.js를 확인하세요.');
            return;
        }

        // Firebase 앱이 이미 초기화되었는지 확인
        if (!firebase.apps.length) {
            firebase.initializeApp(window.CUPID_FIREBASE_CONFIG);
        }

        // Messaging 인스턴스 생성
        messaging = firebase.messaging();

        // VAPID 키는 getToken() 호출 시 옵션으로 전달됩니다.
        // usePublicVapidKey()는 최신 Firebase SDK에서 제거되었습니다.

        // 포그라운드 메시지 수신 처리
        messaging.onMessage((payload) => {
            console.log('포그라운드에서 푸시 알림 수신:', payload);
            displayNotificationResult('포그라운드 푸시 알림 수신:\n' + JSON.stringify(payload, null, 2));
            
            // 브라우저 알림 표시 (선택사항)
            if (Notification.permission === 'granted') {
                const notificationTitle = payload.notification?.title || '새 메시지';
                const notificationOptions = {
                    body: payload.notification?.body || payload.data?.message || '메시지가 도착했습니다.',
                    icon: payload.notification?.icon || '/icon.png',
                    data: payload.data || {}
                };
                new Notification(notificationTitle, notificationOptions);
            }
        });

        console.log('Firebase Messaging 초기화 완료');
    } catch (error) {
        console.error('Firebase Messaging 초기화 실패:', error);
        displayNotificationResult('Firebase 초기화 실패: ' + error.message);
    }
}

/**
 * 알림 테스트 버튼 이벤트 리스너 설정
 */
function setupNotificationEventListeners() {
    const permissionBtn = document.getElementById('notificationPermissionBtn');
    const registerBtn = document.getElementById('notificationRegisterBtn');
    const listBtn = document.getElementById('notificationListBtn');
    const deleteBtn = document.getElementById('notificationDeleteBtn');
    const testBtn = document.getElementById('notificationTestBtn');

    if (permissionBtn) {
        permissionBtn.addEventListener('click', requestNotificationPermission);
    }
    if (registerBtn) {
        registerBtn.addEventListener('click', registerFcmToken);
    }
    if (listBtn) {
        listBtn.addEventListener('click', getRegisteredTokens);
    }
    if (deleteBtn) {
        deleteBtn.addEventListener('click', deleteCurrentToken);
    }
    if (testBtn) {
        testBtn.addEventListener('click', sendTestNotification);
    }
}

/**
 * 브라우저 알림 권한 요청
 */
async function requestNotificationPermission() {
    try {
        displayNotificationResult('알림 권한 요청 중...');

        if (!('Notification' in window)) {
            displayNotificationResult('이 브라우저는 알림을 지원하지 않습니다.');
            return;
        }

        const permission = await Notification.requestPermission();
        
        if (permission === 'granted') {
            displayNotificationResult('✅ 알림 권한이 허용되었습니다.');
            console.log('알림 권한 허용됨');
        } else if (permission === 'denied') {
            displayNotificationResult('❌ 알림 권한이 거부되었습니다. 브라우저 설정에서 수동으로 허용해주세요.');
            console.log('알림 권한 거부됨');
        } else {
            displayNotificationResult('⚠️ 알림 권한이 차단되었습니다.');
            console.log('알림 권한 차단됨');
        }
    } catch (error) {
        console.error('알림 권한 요청 실패:', error);
        displayNotificationResult('알림 권한 요청 실패: ' + error.message);
    }
}

/**
 * FCM 토큰 발급 및 서버 등록
 */
async function registerFcmToken() {
    try {
        if (!messaging) {
            displayNotificationResult('❌ Firebase Messaging이 초기화되지 않았습니다.');
            return;
        }

        if (!accessToken) {
            displayNotificationResult('❌ 로그인이 필요합니다.');
            return;
        }

        displayNotificationResult('FCM 토큰 발급 중...');

        // 1. 알림 권한 확인
        if (Notification.permission !== 'granted') {
            const permission = await Notification.requestPermission();
            if (permission !== 'granted') {
                displayNotificationResult('❌ 알림 권한이 필요합니다.');
                return;
            }
        }

        // 2. FCM 토큰 발급 (VAPID 키와 Service Worker 경로를 옵션으로 전달)
        const tokenOptions = {};
        if (window.CUPID_FIREBASE_VAPID_KEY) {
            tokenOptions.vapidKey = window.CUPID_FIREBASE_VAPID_KEY;
        }
        
        // Service Worker 등록 및 경로 지정
        // 현재 페이지가 test-client 폴더에 있으므로 상대 경로로 지정
        const serviceWorkerPath = '/test-client/firebase-messaging-sw.js';
        let registration;
        try {
            registration = await navigator.serviceWorker.register(serviceWorkerPath, {
                scope: '/test-client/'
            });
            console.log('Service Worker 등록 성공:', registration.scope);
            
            // Service Worker가 활성화될 때까지 대기
            if (registration.installing) {
                console.log('Service Worker 설치 중...');
                await new Promise((resolve) => {
                    registration.installing.addEventListener('statechange', function() {
                        if (this.state === 'activated') {
                            console.log('Service Worker 활성화 완료');
                            resolve();
                        }
                    });
                });
            } else if (registration.waiting) {
                console.log('Service Worker 대기 중...');
                // 대기 중인 Service Worker 활성화
                registration.waiting.postMessage({ type: 'SKIP_WAITING' });
                await new Promise((resolve) => {
                    registration.addEventListener('controllerchange', () => {
                        console.log('Service Worker 컨트롤러 변경됨');
                        resolve();
                    });
                });
            } else if (registration.active) {
                console.log('Service Worker 이미 활성화됨');
            }
            
            // 최종적으로 활성화된 Service Worker 확인
            if (!registration.active) {
                throw new Error('Service Worker가 활성화되지 않았습니다.');
            }
            
            tokenOptions.serviceWorkerRegistration = registration;
        } catch (swError) {
            console.error('Service Worker 등록/활성화 실패:', swError);
            displayNotificationResult('❌ Service Worker 등록 실패: ' + swError.message);
            return;
        }
        
        const token = await messaging.getToken(tokenOptions);
        
        if (!token) {
            displayNotificationResult('❌ FCM 토큰을 발급받을 수 없습니다.');
            return;
        }

        currentFcmToken = token;
        console.log('FCM 토큰 발급됨:', token.substring(0, 50) + '...');

        // 3. 서버에 토큰 등록
        const deviceName = document.getElementById('notificationDeviceName')?.value || 
                          navigator.userAgent.substring(0, 50);
        const appVersion = document.getElementById('notificationAppVersion')?.value || 
                          '1.0.0';

        const response = await fetch(`${API_BASE}/notifications/fcm-token`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`
            },
            body: JSON.stringify({
                token: token,
                deviceType: 'WEB', // 백엔드에서 WEB 타입 지원
                deviceName: deviceName,
                appVersion: appVersion
            })
        });

        const data = await response.json();
        console.log('토큰 등록 응답:', data);

        if (data.success) {
            registeredTokenId = data.tokenId || data.data?.id;
            displayNotificationResult(
                '✅ FCM 토큰이 서버에 등록되었습니다.\n\n' +
                `토큰 ID: ${registeredTokenId || 'N/A'}\n` +
                `토큰 (일부): ${token.substring(0, 50)}...\n` +
                `디바이스: ${deviceName}\n` +
                `앱 버전: ${appVersion}`
            );
        } else {
            displayNotificationResult('❌ 토큰 등록 실패: ' + (data.error || data.message || '알 수 없는 오류'));
        }
    } catch (error) {
        console.error('FCM 토큰 등록 실패:', error);
        displayNotificationResult('❌ 토큰 등록 실패: ' + error.message);
    }
}

/**
 * 서버에 등록된 FCM 토큰 목록 조회
 */
async function getRegisteredTokens() {
    try {
        if (!accessToken) {
            displayNotificationResult('❌ 로그인이 필요합니다.');
            return;
        }

        displayNotificationResult('토큰 목록 조회 중...');

        const response = await fetch(`${API_BASE}/notifications/fcm-token`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${accessToken}`
            }
        });

        const data = await response.json();
        console.log('토큰 목록 응답:', data);

        if (data.success && data.tokens) {
            if (data.tokens.length === 0) {
                displayNotificationResult('등록된 토큰이 없습니다.');
            } else {
                let resultText = `✅ 등록된 토큰 ${data.count}개:\n\n`;
                data.tokens.forEach((token, index) => {
                    resultText += `${index + 1}. 토큰 ID: ${token.id}\n`;
                    resultText += `   디바이스: ${token.deviceName || 'N/A'}\n`;
                    resultText += `   타입: ${token.deviceType}\n`;
                    resultText += `   앱 버전: ${token.appVersion || 'N/A'}\n`;
                    resultText += `   등록일: ${token.createdAt}\n`;
                    resultText += `   마지막 사용: ${token.lastUsedAt}\n`;
                    resultText += `   토큰 (일부): ${token.token}\n\n`;
                });
                
                // 현재 토큰이 있으면 표시
                if (currentFcmToken) {
                    resultText += `\n💡 현재 브라우저의 FCM 토큰:\n`;
                    resultText += `   ${currentFcmToken}\n\n`;
                    resultText += `📋 Firebase CLI로 테스트:\n`;
                    resultText += `   node send-fcm-test.js "${currentFcmToken}" --title "테스트" --body "안녕하세요!"\n\n`;
                    resultText += `   (토큰을 클릭하면 클립보드에 복사됩니다)`;
                } else {
                    resultText += `\n💡 Firebase CLI로 테스트하려면:\n`;
                    resultText += `   1. "FCM 토큰 발급 및 서버 등록" 버튼을 먼저 클릭하세요\n`;
                    resultText += `   2. 그 다음 이 목록을 다시 조회하면 토큰이 표시됩니다\n`;
                    resultText += `   3. node send-fcm-test.js <FCM_TOKEN> --title "제목" --body "내용"\n\n`;
                    resultText += `   자세한 내용은 README-FCM-TEST.md 참고`;
                }
                
                displayNotificationResult(resultText);
                
                // 토큰 클릭 시 복사 기능 (현재 토큰이 있는 경우)
                if (currentFcmToken) {
                    const resultBox = document.getElementById('notificationResult');
                    if (resultBox) {
                        // 기존 이벤트 리스너 제거 (중복 방지)
                        const newResultBox = resultBox.cloneNode(true);
                        resultBox.parentNode.replaceChild(newResultBox, resultBox);
                        
                        newResultBox.addEventListener('click', async function(e) {
                            if (currentFcmToken && newResultBox.textContent.includes(currentFcmToken)) {
                                try {
                                    await navigator.clipboard.writeText(currentFcmToken);
                                    displayNotificationResult('✅ FCM 토큰이 클립보드에 복사되었습니다!\n\n' +
                                        `토큰: ${currentFcmToken}\n\n` +
                                        `📋 Firebase CLI로 테스트:\n` +
                                        `node send-fcm-test.js "${currentFcmToken}" --title "테스트" --body "안녕하세요!"\n\n` +
                                        `또는:\n` +
                                        `node send-fcm-test.js "${currentFcmToken}"`);
                                } catch (error) {
                                    console.error('클립보드 복사 실패:', error);
                                }
                            }
                        });
                    }
                }
            }
        } else {
            displayNotificationResult('❌ 토큰 목록 조회 실패: ' + (data.error || '알 수 없는 오류'));
        }
    } catch (error) {
        console.error('토큰 목록 조회 실패:', error);
        displayNotificationResult('❌ 토큰 목록 조회 실패: ' + error.message);
    }
}

/**
 * 현재 브라우저의 FCM 토큰 삭제
 */
async function deleteCurrentToken() {
    try {
        if (!accessToken) {
            displayNotificationResult('❌ 로그인이 필요합니다.');
            return;
        }

        if (!registeredTokenId) {
            // 토큰 목록을 먼저 조회하여 토큰 ID 찾기
            displayNotificationResult('토큰 목록을 조회하여 삭제할 토큰을 찾는 중...');
            const listResponse = await fetch(`${API_BASE}/notifications/fcm-token`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${accessToken}`
                }
            });

            const listData = await listResponse.json();
            if (listData.success && listData.tokens && listData.tokens.length > 0) {
                // 현재 토큰과 일치하는 토큰 찾기
                const currentToken = currentFcmToken || await messaging?.getToken();
                const matchingToken = listData.tokens.find(t => 
                    t.token && currentToken && t.token.includes(currentToken.substring(0, 20))
                );

                if (matchingToken) {
                    registeredTokenId = matchingToken.id;
                } else {
                    displayNotificationResult('❌ 삭제할 토큰을 찾을 수 없습니다. 토큰 목록에서 토큰 ID를 확인하세요.');
                    return;
                }
            } else {
                displayNotificationResult('❌ 등록된 토큰이 없습니다.');
                return;
            }
        }

        displayNotificationResult('토큰 삭제 중...');

        const response = await fetch(`${API_BASE}/notifications/fcm-token/${registeredTokenId}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${accessToken}`
            }
        });

        const data = await response.json();
        console.log('토큰 삭제 응답:', data);

        if (data.success) {
            registeredTokenId = null;
            currentFcmToken = null;
            
            // FCM 토큰도 삭제
            if (messaging) {
                try {
                    await messaging.deleteToken();
                    console.log('FCM 토큰 삭제됨');
                } catch (error) {
                    console.warn('FCM 토큰 삭제 실패 (무시 가능):', error);
                }
            }

            displayNotificationResult('✅ 토큰이 삭제되었습니다.');
        } else {
            displayNotificationResult('❌ 토큰 삭제 실패: ' + (data.error || data.message || '알 수 없는 오류'));
        }
    } catch (error) {
        console.error('토큰 삭제 실패:', error);
        displayNotificationResult('❌ 토큰 삭제 실패: ' + error.message);
    }
}

/**
 * 알림 테스트 결과를 화면에 표시
 */
function displayNotificationResult(message) {
    const resultBox = document.getElementById('notificationResult');
    if (resultBox) {
        resultBox.textContent = message;
        resultBox.style.whiteSpace = 'pre-wrap'; // 줄바꿈 유지
        resultBox.scrollTop = resultBox.scrollHeight; // 자동 스크롤
    }
    console.log('알림 테스트 결과:', message);
}

/**
 * 테스트 알림 전송
 * 백엔드 API를 통해 테스트 푸시 알림을 전송합니다.
 */
async function sendTestNotification() {
    try {
        if (!accessToken) {
            displayNotificationResult('❌ 로그인이 필요합니다.');
            return;
        }

        displayNotificationResult('테스트 알림 전송 중...');

        // 백엔드에 테스트 알림 전송 요청
        // 백엔드에 테스트 API가 없다면, 로컬 알림으로 대체
        try {
            const response = await fetch(`${API_BASE}/notifications/test`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${accessToken}`
                },
                body: JSON.stringify({
                    title: '테스트 알림',
                    body: '이것은 테스트 푸시 알림입니다!',
                    data: {
                        type: 'test',
                        timestamp: new Date().toISOString()
                    }
                })
            });

            if (response.ok) {
                const data = await response.json();
                displayNotificationResult('✅ 테스트 알림이 전송되었습니다!\n\n' + JSON.stringify(data, null, 2));
            } else {
                // 백엔드에 테스트 API가 없거나 오류가 발생한 경우 로컬 알림으로 대체
                const errorData = await response.json().catch(() => ({}));
                console.warn('백엔드 테스트 API 응답 오류:', response.status, errorData);
                displayNotificationResult('⚠️ 백엔드 테스트 API가 없거나 오류가 발생했습니다. 로컬 알림으로 테스트합니다.');
                sendLocalTestNotification();
            }
        } catch (fetchError) {
            // 네트워크 오류 시 로컬 알림으로 대체
            console.warn('백엔드 API 호출 실패, 로컬 알림으로 대체:', fetchError);
            displayNotificationResult('⚠️ 백엔드 연결 실패. 로컬 알림으로 테스트합니다.');
            sendLocalTestNotification();
        }
    } catch (error) {
        console.error('테스트 알림 전송 실패:', error);
        displayNotificationResult('❌ 테스트 알림 전송 실패: ' + error.message);
    }
}

/**
 * 로컬 테스트 알림 전송 (백엔드 API가 없는 경우)
 */
function sendLocalTestNotification() {
    if (!('Notification' in window)) {
        displayNotificationResult('❌ 이 브라우저는 알림을 지원하지 않습니다.');
        return;
    }

    if (Notification.permission === 'granted') {
        const notification = new Notification('테스트 알림', {
            body: '이것은 로컬 테스트 알림입니다! 푸시 알림이 정상적으로 작동합니다.',
            icon: '/icon.png',
            badge: '/icon.png',
            tag: 'test-notification',
            data: {
                type: 'test',
                timestamp: new Date().toISOString()
            }
        });

        notification.onclick = () => {
            console.log('테스트 알림 클릭됨');
            notification.close();
        };

        displayNotificationResult('✅ 로컬 테스트 알림이 표시되었습니다!\n\n' +
            '백그라운드 푸시 알림을 테스트하려면:\n' +
            '1. 브라우저를 백그라운드로 보내거나 다른 탭으로 이동\n' +
            '2. 백엔드에 테스트 API를 추가하여 실제 FCM 푸시 전송\n' +
            '3. 또는 Firebase Console에서 직접 테스트 메시지 전송');
    } else {
        displayNotificationResult('❌ 알림 권한이 필요합니다. "브라우저 알림 권한 요청" 버튼을 먼저 클릭하세요.');
    }
}

// 전역 스코프에 함수 노출 (필요시)
window.requestNotificationPermission = requestNotificationPermission;
window.registerFcmToken = registerFcmToken;
window.getRegisteredTokens = getRegisteredTokens;
window.deleteCurrentToken = deleteCurrentToken;
window.sendTestNotification = sendTestNotification;

