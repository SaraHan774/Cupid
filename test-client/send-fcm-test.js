#!/usr/bin/env node
/**
 * Firebase CLI를 사용한 FCM 푸시 알림 테스트 스크립트
 * 
 * 사용 방법:
 * 1. Firebase Admin SDK 설치: npm install firebase-admin
 * 2. Firebase 서비스 계정 키 파일 다운로드 (Firebase Console > 프로젝트 설정 > 서비스 계정)
 * 3. 이 스크립트 실행: node send-fcm-test.js <FCM_TOKEN> [--title "제목"] [--body "내용"]
 * 
 * 예시:
 * node send-fcm-test.js e_fUBUmpOp3yqqyPel6p... --title "테스트" --body "안녕하세요!"
 */

const admin = require('firebase-admin');
const path = require('path');
const fs = require('fs');

// 명령줄 인수 파싱
const args = process.argv.slice(2);
const tokenIndex = args.findIndex(arg => !arg.startsWith('--'));
const token = tokenIndex >= 0 ? args[tokenIndex] : null;

const titleIndex = args.findIndex(arg => arg === '--title');
const title = titleIndex >= 0 && args[titleIndex + 1] ? args[titleIndex + 1] : '테스트 알림';

const bodyIndex = args.findIndex(arg => arg === '--body');
const body = bodyIndex >= 0 && args[bodyIndex + 1] ? args[bodyIndex + 1] : '이것은 Firebase CLI를 통한 테스트 알림입니다.';

if (!token) {
    console.error('❌ 사용법: node send-fcm-test.js <FCM_TOKEN> [--title "제목"] [--body "내용"]');
    console.error('예시: node send-fcm-test.js e_fUBUmpOp3yqqyPel6p... --title "테스트" --body "안녕하세요!"');
    process.exit(1);
}

// Firebase 서비스 계정 키 파일 경로 확인
// 여러 가능한 경로를 시도
const possibleKeyPaths = [
    path.join(__dirname, 'firebase-service-account-key.json'),
    path.join(__dirname, 'serviceAccountKey.json'),
    path.join(process.cwd(), 'firebase-service-account-key.json'),
    path.join(process.cwd(), 'serviceAccountKey.json'),
    process.env.FIREBASE_SERVICE_ACCOUNT_KEY_PATH
].filter(Boolean);

let serviceAccountPath = null;
for (const keyPath of possibleKeyPaths) {
    if (fs.existsSync(keyPath)) {
        serviceAccountPath = keyPath;
        break;
    }
}

if (!serviceAccountPath) {
    console.error('❌ Firebase 서비스 계정 키 파일을 찾을 수 없습니다.');
    console.error('다음 경로 중 하나에 파일을 배치하세요:');
    possibleKeyPaths.forEach(p => console.error(`  - ${p}`));
    console.error('\n서비스 계정 키 파일 다운로드 방법:');
    console.error('1. Firebase Console (https://console.firebase.google.com) 접속');
    console.error('2. 프로젝트 선택: cupid-client-sdk');
    console.error('3. 프로젝트 설정 > 서비스 계정 탭');
    console.error('4. "새 비공개 키 생성" 클릭하여 JSON 파일 다운로드');
    console.error('5. 다운로드한 파일을 위 경로 중 하나에 배치');
    process.exit(1);
}

// Firebase Admin SDK 초기화
try {
    const serviceAccount = require(serviceAccountPath);
    
    if (!admin.apps.length) {
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });
        console.log('✅ Firebase Admin SDK 초기화 완료');
    }
} catch (error) {
    console.error('❌ Firebase Admin SDK 초기화 실패:', error.message);
    process.exit(1);
}

// FCM 메시지 전송
async function sendTestNotification() {
    try {
        console.log('📤 FCM 메시지 전송 중...');
        console.log(`   토큰: ${token.substring(0, 50)}...`);
        console.log(`   제목: ${title}`);
        console.log(`   내용: ${body}`);
        
        const message = {
            notification: {
                title: title,
                body: body
            },
            data: {
                type: 'test',
                timestamp: new Date().toISOString()
            },
            token: token,
            webpush: {
                notification: {
                    title: title,
                    body: body,
                    icon: '/icon.png',
                    badge: '/icon.png'
                }
            }
        };

        const response = await admin.messaging().send(message);
        
        console.log('✅ 푸시 알림 전송 성공!');
        console.log(`   Message ID: ${response}`);
        console.log('\n💡 브라우저를 백그라운드로 보내면 알림이 표시됩니다.');
        
    } catch (error) {
        console.error('❌ 푸시 알림 전송 실패:', error.message);
        
        if (error.code === 'messaging/invalid-registration-token') {
            console.error('   원인: 유효하지 않은 FCM 토큰입니다.');
            console.error('   해결: 새로운 토큰을 발급받아 다시 시도하세요.');
        } else if (error.code === 'messaging/registration-token-not-registered') {
            console.error('   원인: 등록되지 않은 토큰입니다.');
            console.error('   해결: 토큰이 만료되었거나 삭제되었습니다. 새로 등록하세요.');
        } else {
            console.error('   오류 코드:', error.code);
        }
        
        process.exit(1);
    }
}

// 실행
sendTestNotification();

