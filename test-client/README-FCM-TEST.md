# Firebase CLI를 사용한 FCM 푸시 알림 테스트

Firebase CLI를 사용하여 FCM 푸시 알림을 테스트하는 방법입니다.

## 사전 준비

### 1. Firebase 서비스 계정 키 파일 다운로드

1. [Firebase Console](https://console.firebase.google.com) 접속
2. 프로젝트 선택: `cupid-client-sdk`
3. 프로젝트 설정 (⚙️ 아이콘) > **서비스 계정** 탭
4. **"새 비공개 키 생성"** 클릭
5. JSON 파일 다운로드
6. 다운로드한 파일을 `test-client/` 폴더에 `firebase-service-account-key.json` 이름으로 저장

### 2. Firebase Admin SDK 설치

```bash
cd test-client
npm install firebase-admin
```

## 사용 방법

### 방법 1: Node.js 스크립트 사용 (권장)

```bash
# 기본 사용 (FCM 토큰만 지정)
node send-fcm-test.js <FCM_TOKEN>

# 제목과 내용 지정
node send-fcm-test.js <FCM_TOKEN> --title "테스트 알림" --body "안녕하세요!"

# 예시
node send-fcm-test.js e_fUBUmpOp3yqqyPel6p... --title "테스트" --body "Firebase CLI 테스트입니다!"
```

### 방법 2: curl을 사용한 Firebase REST API 직접 호출

```bash
# Firebase 서버 키 필요 (Firebase Console > 프로젝트 설정 > 클라우드 메시징 > 서버 키)
FCM_SERVER_KEY="YOUR_SERVER_KEY"
FCM_TOKEN="YOUR_FCM_TOKEN"

curl -X POST https://fcm.googleapis.com/v1/projects/cupid-client-sdk/messages:send \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d "{
    \"message\": {
      \"token\": \"$FCM_TOKEN\",
      \"notification\": {
        \"title\": \"테스트 알림\",
        \"body\": \"Firebase CLI 테스트입니다!\"
      },
      \"webpush\": {
        \"notification\": {
          \"title\": \"테스트 알림\",
          \"body\": \"Firebase CLI 테스트입니다!\",
          \"icon\": \"/icon.png\"
        }
      }
    }
  }"
```

## FCM 토큰 확인 방법

1. 브라우저에서 `http://127.0.0.1:5500/test-client/index.html` 접속
2. 로그인 후 **"📡 API 테스트"** 탭 클릭
3. **"서버에 등록된 토큰 조회"** 버튼 클릭
4. 표시된 토큰을 복사하여 위 명령어에 사용

## 주의사항

- FCM 토큰은 브라우저마다 다릅니다
- 토큰이 만료되면 새로 발급받아야 합니다
- 백그라운드 알림을 테스트하려면 브라우저를 다른 탭으로 이동하거나 최소화하세요

