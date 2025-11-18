// 이 파일은 예시 Firebase 설정 값을 제공합니다. 실제 값을 사용하려면
// 이 파일을 복사하여 firebase-config.js 로 이름을 변경한 후
// Firebase 콘솔에서 발급받은 값으로 모두 교체해야 합니다.
// 서비스 워커와 브라우저 모두에서 동일한 설정을 사용해야 하므로
// window/self 전역 객체에 설정을 노출하도록 구현했습니다.
(function(global) {
    // Firebase SDK 초기화에 필요한 기본 설정 값입니다.
    global.CUPID_FIREBASE_CONFIG = {
        apiKey: "YOUR_FIREBASE_API_KEY",
        authDomain: "YOUR_FIREBASE_PROJECT_ID.firebaseapp.com",
        projectId: "YOUR_FIREBASE_PROJECT_ID",
        storageBucket: "YOUR_FIREBASE_PROJECT_ID.appspot.com",
        messagingSenderId: "YOUR_SENDER_ID",
        appId: "YOUR_FIREBASE_APP_ID"
    };

    // 웹 푸시 인증서(VAPID 키) 공개키를 입력해야 합니다.
    // Firebase 콘솔 > Cloud Messaging > 웹 구성에서 확인할 수 있습니다.
    global.CUPID_FIREBASE_VAPID_KEY = "YOUR_WEB_PUSH_CERTIFICATE_KEY_PAIR";
})(typeof self !== "undefined" ? self : window);

