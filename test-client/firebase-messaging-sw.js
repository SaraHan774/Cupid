// Firebase Cloud Messaging 서비스 워커 설정 파일입니다.
// 브라우저가 백그라운드에서 푸시 알림을 수신할 때 이 스크립트가 실행됩니다.

// Firebase 설정 파일을 불러옵니다. (없을 경우 예외를 잡아 로그로 안내합니다)
// Service Worker는 test-client 폴더에 있으므로 상대 경로로 참조
try {
    importScripts("/test-client/firebase-config.js");
} catch (error) {
    console.warn("firebase-config.js 를 불러오지 못했습니다. Firebase 푸시 알림이 동작하지 않을 수 있습니다.", error);
}

// Firebase SDK 라이브러리를 로드합니다.
importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js");

// Service Worker 활성화를 위한 메시지 리스너
// 대기 중인 Service Worker를 즉시 활성화하기 위해 사용됩니다.
self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'SKIP_WAITING') {
        console.log('Service Worker 즉시 활성화 요청 수신');
        self.skipWaiting();
    }
});

if (self.CUPID_FIREBASE_CONFIG) {
    // Firebase 앱을 초기화합니다. (이미 초기화되었더라도 에러 없이 기존 인스턴스를 재사용합니다)
    firebase.initializeApp(self.CUPID_FIREBASE_CONFIG);

    // 메시징 인스턴스를 생성합니다.
    const messaging = firebase.messaging();

    // 백그라운드 메시지 수신 시 알림을 생성합니다.
    messaging.onBackgroundMessage((payload) => {
        // 수신한 페이로드를 기반으로 알림 제목/본문을 구성합니다.
        const notificationTitle = payload.notification?.title || "새 메시지 도착";
        const notificationOptions = {
            body: payload.notification?.body || "백엔드에서 전송된 푸시 알림입니다.",
            data: payload.data || {},
            icon: payload.notification?.icon
        };

        self.registration.showNotification(notificationTitle, notificationOptions);
    });
} else {
    console.warn("CUPID_FIREBASE_CONFIG 가 설정되지 않았습니다. Firebase 푸시 알림을 초기화할 수 없습니다.");
}

