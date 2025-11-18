#!/usr/bin/env node
/**
 * 백엔드 API를 통한 메시지 전송 테스트 스크립트
 * 
 * 백엔드의 실제 메시지 전송 플로우를 테스트합니다:
 * 1. REST API로 메시지 전송
 * 2. 백엔드가 온라인 사용자에게는 WebSocket으로 전송
 * 3. 백엔드가 오프라인 사용자에게는 FCM Silent Push로 전송
 * 
 * 사용 방법:
 * 1. 먼저 로그인하여 accessToken을 얻어야 합니다 (test-client/index.html 사용)
 * 2. 이 스크립트 실행: 
 *    node send-message-via-backend.js <ACCESS_TOKEN> <CHANNEL_ID> <MESSAGE_CONTENT> [--encrypted]
 * 
 * 예시:
 * node send-message-via-backend.js eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... <channel-uuid> "안녕하세요!"
 * 
 * 옵션:
 * --encrypted: 메시지가 이미 암호화된 경우 (ENCRYPTED: 접두사 자동 추가)
 */

const https = require('https');
const http = require('http');

// 백엔드 API Base URL
const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

// 명령줄 인수 파싱
const args = process.argv.slice(2);

// --encrypted 옵션 확인
const isEncrypted = args.includes('--encrypted');

// 필수 인수 추출
const requiredArgs = args.filter(arg => !arg.startsWith('--'));
const accessToken = requiredArgs[0];
const channelId = requiredArgs[1];
const messageContent = requiredArgs.slice(2).join(' ');

if (!accessToken || !channelId || !messageContent) {
    console.error('❌ 사용법: node send-message-via-backend.js <ACCESS_TOKEN> <CHANNEL_ID> <MESSAGE_CONTENT> [--encrypted]');
    console.error('\n예시:');
    console.error('  node send-message-via-backend.js eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... <channel-uuid> "안녕하세요!"');
    console.error('\n옵션:');
    console.error('  --encrypted: 메시지가 이미 암호화된 경우');
    console.error('\n💡 AccessToken 얻는 방법:');
    console.error('  1. test-client/index.html을 브라우저에서 열기');
    console.error('  2. 로그인 후 브라우저 콘솔에서: localStorage.getItem("accessToken")');
    console.error('  3. 또는 Network 탭에서 /api/v1/auth/login 응답의 accessToken 확인');
    process.exit(1);
}

// URL 파싱
function parseUrl(url) {
    const urlObj = new URL(url);
    return {
        protocol: urlObj.protocol,
        hostname: urlObj.hostname,
        port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
        path: urlObj.pathname
    };
}

// HTTP 요청 헬퍼 함수
function makeRequest(options, data) {
    return new Promise((resolve, reject) => {
        const urlInfo = parseUrl(options.url);
        const isHttps = urlInfo.protocol === 'https:';
        const client = isHttps ? https : http;

        const requestOptions = {
            hostname: urlInfo.hostname,
            port: urlInfo.port,
            path: urlInfo.path,
            method: options.method || 'GET',
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        };

        const req = client.request(requestOptions, (res) => {
            let responseData = '';

            res.on('data', (chunk) => {
                responseData += chunk;
            });

            res.on('end', () => {
                try {
                    const jsonData = JSON.parse(responseData);
                    resolve({
                        statusCode: res.statusCode,
                        headers: res.headers,
                        data: jsonData
                    });
                } catch (e) {
                    resolve({
                        statusCode: res.statusCode,
                        headers: res.headers,
                        data: responseData
                    });
                }
            });
        });

        req.on('error', (error) => {
            reject(error);
        });

        if (data) {
            req.write(JSON.stringify(data));
        }

        req.end();
    });
}

// 메시지 전송
async function sendMessage() {
    try {
        console.log('📤 백엔드 API를 통해 메시지 전송 중...');
        console.log(`   API: ${API_BASE}/channels/${channelId}/messages`);
        console.log(`   채널 ID: ${channelId}`);
        console.log(`   메시지: ${messageContent.substring(0, 50)}${messageContent.length > 50 ? '...' : ''}`);
        console.log(`   암호화 여부: ${isEncrypted ? '예 (이미 암호화됨)' : '아니오 (평문)'}`);
        console.log('');

        // 메시지 내용 준비 (암호화된 경우 ENCRYPTED: 접두사 추가)
        const encryptedContent = isEncrypted 
            ? (messageContent.startsWith('ENCRYPTED:') ? messageContent : `ENCRYPTED:${messageContent}`)
            : messageContent;

        // 요청 본문
        const requestBody = {
            channelId: channelId,
            encryptedContent: encryptedContent,
            messageType: 'TEXT'
        };

        // API 호출
        const response = await makeRequest({
            url: `${API_BASE}/channels/${channelId}/messages`,
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${accessToken}`
            }
        }, requestBody);

        // 응답 처리
        if (response.statusCode === 201 || response.statusCode === 200) {
            console.log('✅ 메시지 전송 성공!');
            console.log('');
            console.log('📋 응답 정보:');
            console.log(`   상태 코드: ${response.statusCode}`);
            
            if (response.data.success) {
                console.log(`   메시지 ID: ${response.data.data?.id || 'N/A'}`);
                console.log(`   채널 ID: ${response.data.data?.channelId || 'N/A'}`);
                console.log(`   발신자 ID: ${response.data.data?.senderId || 'N/A'}`);
                console.log(`   전송 시간: ${response.data.data?.createdAt || 'N/A'}`);
                console.log('');
                console.log('💡 백엔드 처리 플로우:');
                console.log('   1. 메시지가 MongoDB에 저장됨');
                console.log('   2. 채널 멤버들의 온라인 상태 확인');
                console.log('   3. 온라인 사용자 → WebSocket으로 즉시 전송');
                console.log('   4. 오프라인 사용자 → FCM Silent Push 전송');
                console.log('');
                console.log('🔔 FCM 알림 확인 방법:');
                console.log('   - 수신자가 오프라인 상태인지 확인 (WebSocket 연결 없음)');
                console.log('   - 브라우저를 백그라운드로 보내거나 다른 탭으로 이동');
                console.log('   - Service Worker가 FCM 메시지를 수신하면 알림 표시');
            } else {
                console.log(`   오류: ${response.data.error || response.data.message || '알 수 없는 오류'}`);
            }
        } else if (response.statusCode === 401) {
            console.error('❌ 인증 실패: AccessToken이 유효하지 않거나 만료되었습니다.');
            console.error('   해결: 새로운 AccessToken을 발급받아 다시 시도하세요.');
            console.error('   방법: test-client/index.html에서 다시 로그인');
        } else if (response.statusCode === 404) {
            console.error('❌ 채널을 찾을 수 없습니다.');
            console.error(`   채널 ID: ${channelId}`);
            console.error('   해결: 올바른 채널 ID를 확인하세요.');
        } else {
            console.error(`❌ 메시지 전송 실패: HTTP ${response.statusCode}`);
            console.error(`   응답: ${JSON.stringify(response.data, null, 2)}`);
        }

    } catch (error) {
        console.error('❌ 요청 실패:', error.message);
        
        if (error.code === 'ECONNREFUSED') {
            console.error('   원인: 백엔드 서버에 연결할 수 없습니다.');
            console.error('   해결: 백엔드 서버가 실행 중인지 확인하세요.');
            console.error(`   예상 URL: ${API_BASE}`);
        } else if (error.code === 'ENOTFOUND') {
            console.error('   원인: 호스트를 찾을 수 없습니다.');
            console.error(`   해결: API_BASE 환경 변수 또는 코드의 API_BASE를 확인하세요.`);
        } else {
            console.error('   오류 상세:', error);
        }
        
        process.exit(1);
    }
}

// 실행
sendMessage();

