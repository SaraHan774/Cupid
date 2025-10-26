# 📱 알림 시스템 상세 기획서

## 목차
1. [개요](#1-개요)
2. [알림 시나리오](#2-알림-시나리오)
3. [E2E 암호화와 알림](#3-e2e-암호화와-알림)
4. [기술 아키텍처](#4-기술-아키텍처)
5. [알림 설정](#5-알림-설정)
6. [데이터베이스 스키마](#6-데이터베이스-스키마)
7. [API 명세](#7-api-명세)
8. [구현 가이드](#8-구현-가이드)
9. [성능 최적화](#9-성능-최적화)
10. [Phase별 구현 계획](#10-phase별-구현-계획)

---

## 1. 개요

### 1.1 목표
- ✅ **완전한 실시간**: 메시지 도착 즉시 알림
- ✅ **메시지 미리보기**: E2E 암호화 상태에서도 내용 표시
- ✅ **모든 메시지 알림**: 배칭 없이 즉시 알림

### 1.2 핵심 요구사항
- E2E 암호화 유지하면서 메시지 내용 미리보기
- 앱 상태(포그라운드/백그라운드/종료)에 관계없이 실시간 알림
- 사용자별 세밀한 알림 설정 가능
- iOS, Android 네이티브 알림 기능 완전 활용

### 1.3 알림 유형
| 유형 | 설명 | 앱 상태 | 구현 방식 |
|------|------|---------|-----------|
| 인앱 배너 | 상단 배너 알림 | 포그라운드 | React Native 컴포넌트 |
| 로컬 알림 | OS 알림 센터 | 백그라운드 | Native Notification |
| 푸시 알림 | FCM/APNs | 백그라운드/종료 | Silent Push + 복호화 |

---

## 2. 알림 시나리오

### 시나리오 1: 포그라운드 - 채팅 목록 화면

**상황**: Alice가 채팅 목록을 보고 있을 때 Bob의 메시지 도착

```
Bob 메시지 전송: "안녕하세요!"
         ↓
[WebSocket으로 서버 → Alice 전송]
         ↓
[Alice 앱에서 수신 (포그라운드)]
         ↓
[즉시 복호화: < 100ms]
         ↓
┌─────────────────────────────┐
│ 🔵 Bob                      │
│ 안녕하세요!                   │ ← 인앱 배너 (3초)
└─────────────────────────────┘
         ↓
[채팅 목록 업데이트]
┌─────────────────────────────┐
│ 👤 Bob               [1]     │ ← 읽지 않은 배지
│    안녕하세요!                 │
│    방금                       │
└─────────────────────────────┘
         ↓
[소리/진동 (설정에 따라)]
         ↓
[앱 배지 +1]
```

**코드 플로우**:
```javascript
// WebSocket 메시지 수신
socket.on('message:new', async (encryptedMessage) => {
  // 1. 앱 상태 확인
  const appState = AppState.currentState;
  
  if (appState === 'active') {
    // 2. 복호화
    const decrypted = await signalProtocol.decrypt(
      encryptedMessage.content,
      encryptedMessage.senderId
    );
    
    // 3. 발신자 정보 조회
    const sender = await getUser(encryptedMessage.senderId);
    
    // 4. 인앱 배너 표시
    showInAppBanner({
      sender: sender.username,
      avatar: sender.profileThumbnailUrl,
      preview: decrypted.text,
      timestamp: 'just now',
      onTap: () => {
        navigation.navigate('Chat', { 
          channelId: encryptedMessage.channelId 
        });
      }
    });
    
    // 5. 채팅 목록 업데이트
    chatListStore.updateLastMessage(
      encryptedMessage.channelId,
      decrypted.text,
      encryptedMessage.timestamp
    );
    
    // 6. 읽지 않은 카운트 증가
    chatListStore.incrementUnread(encryptedMessage.channelId);
    
    // 7. 앱 배지 업데이트
    const totalUnread = await getTotalUnreadCount();
    updateAppBadge(totalUnread);
    
    // 8. 알림 설정 확인 후 소리/진동
    const settings = await getNotificationSettings(encryptedMessage.channelId);
    if (settings.soundEnabled) {
      playSound('message.mp3');
    }
    if (settings.vibrationEnabled) {
      Vibration.vibrate([0, 250, 250, 250]);
    }
  }
});
```

---

### 시나리오 2: 포그라운드 - 채팅방 내부

**상황**: Alice가 Bob과의 채팅방에서 대화 중

```
Bob 메시지 전송: "네, 좋아요!"
         ↓
[WebSocket 수신]
         ↓
[현재 채팅방 확인]
         ↓
    같은 채팅방?
         YES
         ↓
[복호화]
         ↓
[화면에 즉시 표시]
┌─────────────────────────────┐
│ Bob: 안녕하세요!              │
│ Alice: 안녕하세요!            │
│ Bob: 네, 좋아요! ← 새 메시지  │
└─────────────────────────────┘
         ↓
[자동 스크롤]
         ↓
[자동 읽음 처리]
         ↓
[소리/진동/배너 없음]
```

**코드 플로우**:
```javascript
socket.on('message:new', async (encryptedMessage) => {
  const currentChannelId = navigationState.getCurrentChannelId();
  
  if (encryptedMessage.channelId === currentChannelId) {
    // 같은 채팅방의 메시지
    
    // 1. 복호화
    const decrypted = await signalProtocol.decrypt(
      encryptedMessage.content,
      encryptedMessage.senderId
    );
    
    // 2. 화면에 즉시 추가
    chatStore.addMessage(currentChannelId, {
      id: encryptedMessage.id,
      senderId: encryptedMessage.senderId,
      text: decrypted.text,
      timestamp: encryptedMessage.timestamp,
      status: 'received'
    });
    
    // 3. 자동 스크롤
    chatScrollView.scrollToEnd({ animated: true });
    
    // 4. 읽음 처리 (서버에 알림)
    await markAsRead(currentChannelId, encryptedMessage.id);
    
    // 5. 소리/진동 없음 (이미 보고 있으므로)
    
  } else {
    // 다른 채팅방의 메시지 → 배너만 표시
    // (시나리오 1과 동일)
  }
});
```

---

### 시나리오 3: 백그라운드 (핵심 시나리오 🌟)

**상황**: Alice가 인스타그램을 보고 있을 때 Bob의 메시지 도착

```
Bob 메시지 전송: "오늘 저녁 약속 괜찮으세요?"
         ↓
[서버가 수신]
         ↓
Alice WebSocket 연결 확인
         ↓
    연결됨?
    ↙    ↘
  NO      YES (5분 이내)
   ↓       ↓
FCM     WebSocket
전송    시도 실패
   ↓       ↓
   └───→ FCM
        전송
         ↓
[FCM Silent Push]
{
  data: {
    type: "new_message",
    channel_id: "...",
    sender_id: "bob-id",
    encrypted_content: "E3xK9pL...",
    timestamp: "..."
  },
  priority: "high",
  content_available: true
}
         ↓
[OS가 앱을 백그라운드에서 깨움]
         ↓
[FirebaseMessagingService / NotificationServiceExtension]
         ↓
[복호화 작업 시작]
         ↓
┌─────────────────────────────┐
│ 1. 암호화된 내용 추출          │
│ 2. 로컬 저장소에서 키 로드      │
│ 3. Signal Protocol 복호화    │
│ 4. 발신자 정보 조회 (캐시)     │
│ 5. 프로필 이미지 다운로드       │
└─────────────────────────────┘
         ↓
[로컬 알림 생성]
┌─────────────────────────────┐
│ 🔔 알림                      │
│ ┌─────────────────────────┐ │
│ │ 👤 Bob              방금 │ │
│ │ 오늘 저녁 약속 괜찮으세요? │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
         ↓
[사용자가 알림 탭]
         ↓
[앱 실행 → 해당 채팅방 열림]
```

**Android 구현**:
```kotlin
// MyFirebaseMessagingService.kt
class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        remoteMessage.data.let { data ->
            if (data["type"] == "new_message") {
                handleNewMessage(data)
            }
        }
    }
    
    private fun handleNewMessage(data: Map<String, String>) {
        try {
            val startTime = System.currentTimeMillis()
            
            // 1. 데이터 추출
            val channelId = data["channel_id"]!!
            val senderId = data["sender_id"]!!
            val encryptedContent = data["encrypted_content"]!!
            val messageId = data["message_id"]!!
            
            // 2. Signal Protocol 복호화
            val signalProtocol = SignalProtocol.getInstance(applicationContext)
            val decrypted = signalProtocol.decrypt(encryptedContent, senderId)
            
            // 3. 발신자 정보 조회 (로컬 DB 캐시)
            val localDB = LocalDatabase.getInstance(applicationContext)
            val sender = localDB.userDao().getUserById(senderId)
            
            // 4. 알림 설정 확인
            val settings = localDB.notificationSettingsDao()
                .getChannelSettings(channelId)
            
            if (!settings.enabled) {
                Log.d(TAG, "Notifications disabled for channel: $channelId")
                return
            }
            
            // 5. 방해금지 모드 확인
            if (isDoNotDisturbActive()) {
                Log.d(TAG, "Do Not Disturb is active")
                return
            }
            
            // 6. 로컬 알림 생성
            val notificationBuilder = NotificationCompat.Builder(
                this,
                CHANNEL_ID_MESSAGES
            )
                .setSmallIcon(R.drawable.ic_message)
                .setContentTitle(sender.username)
                .setContentText(decrypted)  // ✅ 복호화된 내용
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
            
            // 7. 프로필 이미지 추가
            sender.profileThumbnailUrl?.let { url ->
                val bitmap = loadImageFromCache(url) ?: downloadImage(url)
                bitmap?.let {
                    notificationBuilder.setLargeIcon(it)
                }
            }
            
            // 8. 소리 설정
            if (settings.soundEnabled) {
                val soundUri = getSoundUri(settings.soundName)
                notificationBuilder.setSound(soundUri)
            } else {
                notificationBuilder.setSound(null)
            }
            
            // 9. 진동 설정
            if (settings.vibrationEnabled) {
                notificationBuilder.setVibrate(longArrayOf(0, 250, 250, 250))
            } else {
                notificationBuilder.setVibrate(null)
            }
            
            // 10. 클릭 액션 (채팅방 열기)
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("channel_id", channelId)
                putExtra("message_id", messageId)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                channelId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.setContentIntent(pendingIntent)
            
            // 11. 알림 액션 추가
            addNotificationActions(notificationBuilder, channelId, messageId)
            
            // 12. 알림 표시
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) 
                as NotificationManager
            notificationManager.notify(
                channelId.hashCode(),
                notificationBuilder.build()
            )
            
            // 13. 배지 업데이트
            updateBadgeCount()
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Notification created in ${duration}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle message", e)
            
            // 폴백: Generic 알림
            showGenericNotification(data["sender_id"] ?: "Unknown")
            
            // 에러 로깅
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
    
    private fun addNotificationActions(
        builder: NotificationCompat.Builder,
        channelId: String,
        messageId: String
    ) {
        // 답장 액션
        val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
            putExtra("channel_id", channelId)
            putExtra("message_id", messageId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        
        val remoteInput = RemoteInput.Builder("reply_text")
            .setLabel("답장하기")
            .build()
        
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reply,
            "답장",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
        
        builder.addAction(replyAction)
        
        // 읽음 표시 액션
        val markReadIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "MARK_AS_READ"
            putExtra("channel_id", channelId)
            putExtra("message_id", messageId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_check,
            "읽음 표시",
            markReadPendingIntent
        ).build()
        
        builder.addAction(markReadAction)
    }
    
    private fun isDoNotDisturbActive(): Boolean {
        val settings = LocalDatabase.getInstance(applicationContext)
            .notificationSettingsDao()
            .getGlobalSettings()
        
        if (!settings.dndEnabled) return false
        
        val now = LocalTime.now()
        val start = LocalTime.parse(settings.dndStartTime)
        val end = LocalTime.parse(settings.dndEndTime)
        
        return if (start < end) {
            now in start..end
        } else {
            // 자정을 넘어가는 경우 (예: 22:00 ~ 08:00)
            now >= start || now <= end
        }
    }
    
    private fun showGenericNotification(senderName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(senderName)
            .setContentText("새 메시지가 도착했습니다")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}
```

**iOS 구현**:
```swift
// NotificationService.swift (Notification Service Extension)
class NotificationService: UNNotificationServiceExtension {
    
    var contentHandler: ((UNNotificationContent) -> Void)?
    var bestAttemptContent: UNMutableNotificationContent?
    
    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        self.contentHandler = contentHandler
        bestAttemptContent = (request.content.mutableCopy() as? UNMutableNotificationContent)
        
        guard let bestAttemptContent = bestAttemptContent else {
            contentHandler(request.content)
            return
        }
        
        // 데이터 추출
        let userInfo = request.content.userInfo
        guard let type = userInfo["type"] as? String,
              type == "new_message",
              let channelId = userInfo["channel_id"] as? String,
              let senderId = userInfo["sender_id"] as? String,
              let encryptedContent = userInfo["encrypted_content"] as? String else {
            contentHandler(bestAttemptContent)
            return
        }
        
        let startTime = Date()
        
        do {
            // 1. Signal Protocol 복호화
            let signalProtocol = SignalProtocol.shared
            let decrypted = try signalProtocol.decrypt(encryptedContent, from: senderId)
            
            // 2. 발신자 정보 조회
            let localDB = LocalDatabase.shared
            guard let sender = try localDB.getUser(senderId) else {
                throw NSError(domain: "NotificationService", code: 1, userInfo: nil)
            }
            
            // 3. 알림 설정 확인
            let settings = try localDB.getChannelNotificationSettings(channelId)
            
            if !settings.enabled {
                print("Notifications disabled for channel: \(channelId)")
                contentHandler(UNNotificationContent())
                return
            }
            
            // 4. 방해금지 모드 확인
            if isDoNotDisturbActive() {
                print("Do Not Disturb is active")
                contentHandler(UNNotificationContent())
                return
            }
            
            // 5. 알림 내용 수정
            bestAttemptContent.title = sender.username
            bestAttemptContent.body = decrypted  // ✅ 복호화된 내용
            bestAttemptContent.threadIdentifier = channelId
            
            // 6. 소리 설정
            if settings.soundEnabled {
                bestAttemptContent.sound = .default
            } else {
                bestAttemptContent.sound = nil
            }
            
            // 7. 배지
            let unreadCount = try localDB.getTotalUnreadCount()
            bestAttemptContent.badge = NSNumber(value: unreadCount)
            
            // 8. 카테고리 (액션)
            bestAttemptContent.categoryIdentifier = "MESSAGE_CATEGORY"
            
            // 9. 프로필 이미지 첨부
            if let thumbnailURL = URL(string: sender.profileThumbnailUrl ?? "") {
                downloadImage(from: thumbnailURL) { attachment in
                    if let attachment = attachment {
                        bestAttemptContent.attachments = [attachment]
                    }
                    
                    let duration = Date().timeIntervalSince(startTime)
                    print("Notification created in \(duration)s")
                    
                    contentHandler(bestAttemptContent)
                }
            } else {
                contentHandler(bestAttemptContent)
            }
            
        } catch {
            print("Failed to process notification: \(error)")
            
            // 폴백: Generic 알림
            bestAttemptContent.title = userInfo["sender_name"] as? String ?? "Unknown"
            bestAttemptContent.body = "새 메시지가 도착했습니다"
            contentHandler(bestAttemptContent)
            
            // 에러 로깅
            Crashlytics.crashlytics().record(error: error)
        }
    }
    
    override func serviceExtensionTimeWillExpire() {
        // 시간 초과 (30초)
        if let contentHandler = contentHandler,
           let bestAttemptContent = bestAttemptContent {
            bestAttemptContent.body = "새 메시지가 도착했습니다"
            contentHandler(bestAttemptContent)
        }
    }
    
    private func downloadImage(
        from url: URL,
        completion: @escaping (UNNotificationAttachment?) -> Void
    ) {
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data,
                  error == nil,
                  let image = UIImage(data: data),
                  let jpegData = image.jpegData(compressionQuality: 0.8) else {
                completion(nil)
                return
            }
            
            let tempDirectory = FileManager.default.temporaryDirectory
            let imageFileURL = tempDirectory.appendingPathComponent(
                UUID().uuidString + ".jpg"
            )
            
            do {
                try jpegData.write(to: imageFileURL)
                let attachment = try UNNotificationAttachment(
                    identifier: "image",
                    url: imageFileURL,
                    options: nil
                )
                completion(attachment)
            } catch {
                print("Failed to create attachment: \(error)")
                completion(nil)
            }
        }.resume()
    }
    
    private func isDoNotDisturbActive() -> Bool {
        guard let settings = try? LocalDatabase.shared.getGlobalNotificationSettings() else {
            return false
        }
        
        if !settings.dndEnabled {
            return false
        }
        
        let calendar = Calendar.current
        let now = Date()
        let currentTime = calendar.component(.hour, from: now) * 60 +
                         calendar.component(.minute, from: now)
        
        let startTime = parseTime(settings.dndStartTime)
        let endTime = parseTime(settings.dndEndTime)
        
        if startTime < endTime {
            return currentTime >= startTime && currentTime <= endTime
        } else {
            return currentTime >= startTime || currentTime <= endTime
        }
    }
    
    private func parseTime(_ timeString: String) -> Int {
        let components = timeString.split(separator: ":")
        guard components.count == 2,
              let hour = Int(components[0]),
              let minute = Int(components[1]) else {
            return 0
        }
        return hour * 60 + minute
    }
}
```

---

### 시나리오 4: 앱 완전 종료

**상황**: Alice가 앱을 완전히 종료한 상태

```
→ 시나리오 3과 동일하게 처리됨
→ FCM이 OS 레벨에서 앱을 깨움
→ NotificationServiceExtension / FirebaseMessagingService 실행
```

---

## 3. E2E 암호화와 알림

### 3.1 문제점

```
Bob: "점심 같이 드실래요?" 
  → [Signal 암호화] 
  → 서버: "A3xK9pL2mN..."

서버는 암호화된 데이터만 가짐
하지만 알림에는 "점심 같이 드실래요?"가 표시되어야 함
```

### 3.2 해결 방법: Silent Push + 클라이언트 복호화

#### 서버가 전송하는 FCM 페이로드
```json
{
  "message": {
    "token": "alice-fcm-token",
    "data": {
      "type": "new_message",
      "message_id": "msg-123",
      "channel_id": "channel-abc",
      "sender_id": "bob-id",
      "sender_name": "Bob",
      "encrypted_content": "A3xK9pL2mN...",
      "timestamp": "2025-10-26T12:00:00Z"
    },
    "apns": {
      "headers": {
        "apns-priority": "10",
        "apns-push-type": "background"
      },
      "payload": {
        "aps": {
          "content-available": 1,
          "mutable-content": 1
        }
      }
    },
    "android": {
      "priority": "high"
    }
  }
}
```

#### 클라이언트 처리 흐름
```
1. FCM Silent Push 수신
   ↓
2. OS가 백그라운드에서 앱 깨움
   ↓
3. NotificationService / MessagingService 실행
   ↓
4. 로컬 저장소에서 Signal 키 로드
   ↓
5. 암호화된 내용 복호화
   ↓
6. 로컬 알림 생성 (복호화된 내용 포함)
   ↓
7. OS 알림 센터에 표시
```

### 3.3 보안 고려사항

**키 저장**:
- iOS: Keychain (가장 안전)
- Android: EncryptedSharedPreferences (Android Keystore 사용)

**Extension/Service에서 키 접근**:
```swift
// iOS: App Group으로 키 공유
let keychain = KeychainAccess(
    service: "com.example.chat",
    accessGroup: "group.com.example.chat"
)
```

```kotlin
// Android: 동일한 앱이므로 SharedPreferences 접근 가능
val sharedPrefs = EncryptedSharedPreferences.create(
    context,
    "signal_keys",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### 3.4 성능 제약

**iOS**:
- Notification Service Extension 실행 시간: 최대 30초
- 30초 내에 복호화 + 이미지 다운로드 완료 필요
- 실패 시 시스템이 원본 알림 표시

**Android**:
- 특별한 제약 없음
- 하지만 너무 오래 걸리면 ANR 발생 가능
- 권장: 10초 이내 완료

**최적화 전략**:
- 키 캐싱 (메모리)
- 사용자 정보 캐싱 (로컬 DB)
- 이미지 캐싱 (디스크)
- 복호화 알고리즘 최적화 (Native 모듈)

---

## 4. 기술 아키텍처

### 4.1 전체 구조

```
┌─────────────────────────────────────────────┐
│              클라이언트 (Alice)               │
├─────────────────────────────────────────────┤
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │     React Native 앱                  │  │
│  │  ┌────────────────────────────────┐  │  │
│  │  │  WebSocket 클라이언트          │  │  │
│  │  │  - 실시간 메시지 수신           │  │  │
│  │  │  - 하트비트 (30초)             │  │  │
│  │  │  - 자동 재연결                 │  │  │
│  │  └────────────────────────────────┘  │  │
│  │                                      │  │
│  │  ┌────────────────────────────────┐  │  │
│  │  │  Signal Protocol SDK           │  │  │
│  │  │  - 메시지 복호화                │  │  │
│  │  │  - 키 관리                     │  │  │
│  │  └────────────────────────────────┘  │  │
│  │                                      │  │
│  │  ┌────────────────────────────────┐  │  │
│  │  │  Notification Manager          │  │  │
│  │  │  - 인앱 배너                   │  │  │
│  │  │  - 소리/진동                   │  │  │
│  │  │  - 배지 관리                   │  │  │
│  │  └────────────────────────────────┘  │  │
│  └──────────────────────────────────────┘  │
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │     Native Modules                   │  │
│  ├──────────────────┬───────────────────┤  │
│  │ iOS              │ Android           │  │
│  │ ────────         │ ────────          │  │
│  │ NotificationSvc  │ FCM Service       │  │
│  │ Extension        │                   │  │
│  │ - FCM 수신       │ - FCM 수신        │  │
│  │ - 복호화         │ - 복호화          │  │
│  │ - 로컬 알림 생성  │ - 로컬 알림 생성   │  │
│  └──────────────────┴───────────────────┘  │
└─────────────────────────────────────────────┘
                     ↕
┌─────────────────────────────────────────────┐
│                  서버                        │
├─────────────────────────────────────────────┤
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │  WebSocket 서버                      │  │
│  │  - 실시간 연결 관리                   │  │
│  │  - 메시지 브로드캐스트                │  │
│  │  - 연결 상태 추적                     │  │
│  └──────────────────────────────────────┘  │
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │  Message Delivery Service            │  │
│  │  - WebSocket 전송 시도                │  │
│  │  - FCM 폴백                          │  │
│  │  - 전송 확인 (ACK)                   │  │
│  └──────────────────────────────────────┘  │
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │  FCM Service                         │  │
│  │  - Firebase Admin SDK                │  │
│  │  - Silent Push 전송                  │  │
│  │  - 디바이스 토큰 관리                 │  │
│  └──────────────────────────────────────┘  │
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │  Redis                               │  │
│  │  - 온라인 상태 캐시                   │  │
│  │  - FCM 토큰 캐시                     │  │
│  │  - 읽지 않은 카운트                   │  │
│  └──────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
                     ↕
┌─────────────────────────────────────────────┐
│            FCM / APNs                       │
│  - Google Firebase Cloud Messaging          │
│  - Apple Push Notification service          │
└─────────────────────────────────────────────┘
```

### 4.2 메시지 전달 플로우

```
Bob이 메시지 전송
       ↓
[서버 WebSocket Handler]
       ↓
메시지 저장 (MongoDB)
       ↓
Alice 온라인 체크
       ↓
┌──────┴──────┐
│             │
YES           NO
│             │
WebSocket     FCM Token
전송 시도      조회 (Redis)
│             │
성공?         FCM 전송
│             │
YES  NO       │
│    │        │
OK   └────→ FCM
            전송
```

### 4.3 기술 스택

**클라이언트**:
- React Native
- @react-native-firebase/messaging (FCM)
- react-native-push-notification (로컬 알림)
- @notifee/react-native (고급 알림 기능)
- libsignal-client (Signal Protocol)

**서버**:
- Kotlin + Spring Boot
- Firebase Admin SDK (FCM)
- WebSocket (Spring WebSocket)
- Redis (상태 관리)
- PostgreSQL (설정 저장)
- MongoDB (메시지 저장)

---

## 5. 알림 설정

### 5.1 데이터 구조

```typescript
// 전역 알림 설정
interface GlobalNotificationSettings {
  userId: string;
  enabled: boolean;              // 전체 알림 켜기/끄기
  soundEnabled: boolean;          // 소리
  vibrationEnabled: boolean;      // 진동
  showPreview: boolean;          // 메시지 미리보기
  
  // 방해금지 모드
  dndEnabled: boolean;
  dndStartTime: string;          // "22:00"
  dndEndTime: string;            // "08:00"
  dndDays: number[];             // [1,2,3,4,5] = 월~금
  
  createdAt: Date;
  updatedAt: Date;
}

// 채널별 알림 설정
interface ChannelNotificationSettings {
  channelId: string;
  userId: string;
  enabled: boolean;              // 이 채팅방 알림 켜기/끄기
  soundEnabled: boolean;
  soundName: string;             // "message.mp3"
  vibrationEnabled: boolean;
  vibrationPattern: number[];    // [0, 250, 250, 250]
  
  // 일시적 음소거
  mutedUntil: Date | null;       // 1시간/24시간 음소거
  
  createdAt: Date;
  updatedAt: Date;
}
```

### 5.2 설정 UI

```typescript
// React Native 설정 화면
const NotificationSettingsScreen = () => {
  const [settings, setSettings] = useState<GlobalNotificationSettings>();
  
  return (
    <ScrollView>
      {/* 전역 설정 */}
      <Section title="알림">
        <SwitchRow
          label="알림 받기"
          value={settings.enabled}
          onChange={(v) => updateSetting('enabled', v)}
        />
        
        <SwitchRow
          label="메시지 미리보기"
          value={settings.showPreview}
          onChange={(v) => updateSetting('showPreview', v)}
          subtitle="잠금화면에 메시지 내용 표시"
        />
        
        <SwitchRow
          label="소리"
          value={settings.soundEnabled}
          onChange={(v) => updateSetting('soundEnabled', v)}
        />
        
        <SwitchRow
          label="진동"
          value={settings.vibrationEnabled}
          onChange={(v) => updateSetting('vibrationEnabled', v)}
        />
      </Section>
      
      {/* 방해금지 모드 */}
      <Section title="방해금지">
        <SwitchRow
          label="방해금지 모드"
          value={settings.dndEnabled}
          onChange={(v) => updateDND('enabled', v)}
        />
        
        {settings.dndEnabled && (
          <>
            <TimePickerRow
              label="시작 시간"
              value={settings.dndStartTime}
              onChange={(v) => updateDND('startTime', v)}
            />
            
            <TimePickerRow
              label="종료 시간"
              value={settings.dndEndTime}
              onChange={(v) => updateDND('endTime', v)}
            />
            
            <WeekdayPickerRow
              label="요일"
              value={settings.dndDays}
              onChange={(v) => updateDND('days', v)}
            />
          </>
        )}
      </Section>
      
      {/* 채널별 설정 */}
      <Section title="채팅방별 알림">
        {channels.map(channel => (
          <ChannelNotificationRow
            key={channel.id}
            channel={channel}
            onPress={() => navigateToChannelSettings(channel.id)}
          />
        ))}
      </Section>
    </ScrollView>
  );
};

// 채팅방별 설정 화면
const ChannelNotificationSettingsScreen = ({ channelId }) => {
  const [settings, setSettings] = useState<ChannelNotificationSettings>();
  
  return (
    <ScrollView>
      <Section>
        <SwitchRow
          label="알림 받기"
          value={settings.enabled}
          onChange={(v) => updateChannelSetting('enabled', v)}
        />
        
        <SwitchRow
          label="소리"
          value={settings.soundEnabled}
          onChange={(v) => updateChannelSetting('soundEnabled', v)}
        />
        
        <SelectRow
          label="알림음"
          value={settings.soundName}
          options={['message.mp3', 'notification.mp3', 'ding.mp3']}
          onChange={(v) => updateChannelSetting('soundName', v)}
        />
        
        <SwitchRow
          label="진동"
          value={settings.vibrationEnabled}
          onChange={(v) => updateChannelSetting('vibrationEnabled', v)}
        />
      </Section>
      
      <Section title="일시적 음소거">
        <Button
          title="1시간 동안 알림 끄기"
          onPress={() => muteFor(1)}
        />
        <Button
          title="24시간 동안 알림 끄기"
          onPress={() => muteFor(24)}
        />
        {settings.mutedUntil && (
          <Button
            title="음소거 해제"
            onPress={() => unmute()}
          />
        )}
      </Section>
    </ScrollView>
  );
};
```

---

## 6. 데이터베이스 스키마

### 6.1 PostgreSQL

```sql
-- 전역 알림 설정
CREATE TABLE user_notification_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    sound_enabled BOOLEAN NOT NULL DEFAULT true,
    vibration_enabled BOOLEAN NOT NULL DEFAULT true,
    show_preview BOOLEAN NOT NULL DEFAULT true,
    
    -- 방해금지 모드
    dnd_enabled BOOLEAN NOT NULL DEFAULT false,
    dnd_start_time TIME NOT NULL DEFAULT '22:00:00',
    dnd_end_time TIME NOT NULL DEFAULT '08:00:00',
    dnd_days INTEGER[] NOT NULL DEFAULT ARRAY[1,2,3,4,5,6,7],
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_notification_settings_user ON user_notification_settings(user_id);

-- 채널별 알림 설정
CREATE TABLE channel_notification_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    sound_enabled BOOLEAN NOT NULL DEFAULT true,
    sound_name VARCHAR(100) NOT NULL DEFAULT 'message.mp3',
    vibration_enabled BOOLEAN NOT NULL DEFAULT true,
    vibration_pattern INTEGER[] NOT NULL DEFAULT ARRAY[0, 250, 250, 250],
    
    -- 일시적 음소거
    muted_until TIMESTAMP,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(channel_id, user_id)
);

CREATE INDEX idx_channel_notification_settings_channel ON channel_notification_settings(channel_id);
CREATE INDEX idx_channel_notification_settings_user ON channel_notification_settings(user_id);
CREATE INDEX idx_channel_notification_settings_muted ON channel_notification_settings(muted_until) 
    WHERE muted_until IS NOT NULL;

-- FCM 디바이스 토큰
CREATE TABLE fcm_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL UNIQUE,
    device_type VARCHAR(20) NOT NULL CHECK (device_type IN ('ios', 'android')),
    device_name VARCHAR(100),
    app_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_fcm_tokens_user ON fcm_tokens(user_id);
CREATE INDEX idx_fcm_tokens_token ON fcm_tokens(token);
CREATE INDEX idx_fcm_tokens_active ON fcm_tokens(is_active) WHERE is_active = true;
```

### 6.2 Redis

```redis
# 사용자 온라인 상태
KEY: user:online:{user_id}
VALUE: "true"
TTL: 5분 (하트비트로 갱신)

# FCM 토큰 캐시 (빠른 조회)
KEY: user:fcm_token:{user_id}
VALUE: "fcm_token_string"
TTL: 1시간

# 읽지 않은 메시지 카운트
KEY: unread:{user_id}:{channel_id}
VALUE: "5"
TTL: 없음 (명시적 삭제)

# 총 읽지 않은 메시지 (앱 배지용)
KEY: unread:total:{user_id}
VALUE: "12"
TTL: 없음
```

---

## 7. API 명세

### 7.1 알림 설정 API

#### 전역 설정 조회
```http
GET /api/v1/notifications/settings

Response 200:
{
  "userId": "...",
  "enabled": true,
  "soundEnabled": true,
  "vibrationEnabled": true,
  "showPreview": true,
  "dndEnabled": false,
  "dndStartTime": "22:00",
  "dndEndTime": "08:00",
  "dndDays": [1,2,3,4,5],
  "createdAt": "...",
  "updatedAt": "..."
}
```

#### 전역 설정 업데이트
```http
PUT /api/v1/notifications/settings

Request:
{
  "enabled": true,
  "soundEnabled": false,
  "vibrationEnabled": true,
  "showPreview": true,
  "dndEnabled": true,
  "dndStartTime": "23:00",
  "dndEndTime": "07:00",
  "dndDays": [1,2,3,4,5]
}

Response 200:
{
  "success": true,
  "settings": { ... }
}
```

#### 채널 설정 조회
```http
GET /api/v1/channels/{channelId}/notifications/settings

Response 200:
{
  "channelId": "...",
  "userId": "...",
  "enabled": true,
  "soundEnabled": true,
  "soundName": "message.mp3",
  "vibrationEnabled": true,
  "vibrationPattern": [0, 250, 250, 250],
  "mutedUntil": null,
  "createdAt": "...",
  "updatedAt": "..."
}
```

#### 채널 설정 업데이트
```http
PUT /api/v1/channels/{channelId}/notifications/settings

Request:
{
  "enabled": false,
  "soundEnabled": false
}

Response 200:
{
  "success": true,
  "settings": { ... }
}
```

#### 채널 음소거
```http
POST /api/v1/channels/{channelId}/notifications/mute

Request:
{
  "duration": 1  // 시간 (1, 8, 24)
}

Response 200:
{
  "success": true,
  "mutedUntil": "2025-10-26T13:00:00Z"
}
```

#### 채널 음소거 해제
```http
POST /api/v1/channels/{channelId}/notifications/unmute

Response 200:
{
  "success": true
}
```

### 7.2 FCM 토큰 API

#### FCM 토큰 등록
```http
POST /api/v1/notifications/fcm-token

Request:
{
  "token": "fcm_token_string",
  "deviceType": "android",
  "deviceName": "Samsung Galaxy S21",
  "appVersion": "1.0.0"
}

Response 200:
{
  "success": true,
  "tokenId": "..."
}
```

#### FCM 토큰 삭제 (로그아웃 시)
```http
DELETE /api/v1/notifications/fcm-token/{tokenId}

Response 200:
{
  "success": true
}
```

---

## 8. 구현 가이드

### 8.1 프로젝트 설정

#### React Native

```bash
# FCM 설치
npm install @react-native-firebase/app
npm install @react-native-firebase/messaging

# 로컬 알림
npm install react-native-push-notification
npm install @notifee/react-native

# iOS
cd ios && pod install
```

#### Android 설정

**AndroidManifest.xml**:
```xml
<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <application>
        <!-- FCM Service -->
        <service
            android:name=".fcm.MyFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        
        <!-- Notification Channel -->
        <meta-data
            android:name="com.google.firebase.messaging.default_notification_channel_id"
            android:value="messages" />
    </application>
</manifest>
```

**build.gradle**:
```gradle
dependencies {
    implementation platform('com.google.firebase:firebase-bom:32.0.0')
    implementation 'com.google.firebase:firebase-messaging'
    implementation 'androidx.work:work-runtime:2.8.0'
}
```

#### iOS 설정

**AppDelegate.swift**:
```swift
import Firebase
import UserNotifications

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        
        // Firebase 초기화
        FirebaseApp.configure()
        
        // 알림 권한 요청
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { granted, error in
            print("Permission granted: \(granted)")
        }
        
        application.registerForRemoteNotifications()
        
        return true
    }
    
    // FCM 토큰 수신
    func messaging(
        _ messaging: Messaging,
        didReceiveRegistrationToken fcmToken: String?
    ) {
        print("FCM Token: \(fcmToken ?? "")")
        // 서버에 토큰 전송
    }
}
```

**Notification Service Extension 추가**:
1. Xcode → File → New → Target
2. Notification Service Extension 선택
3. 이름: `NotificationService`
4. App Group 설정 (메인 앱과 Extension 간 데이터 공유)

---

## 9. 성능 최적화

### 9.1 복호화 성능

**목표**: 30초 제약 내 완료

**최적화 방법**:
1. **키 캐싱** (메모리): 자주 사용하는 키는 메모리에 보관
2. **사용자 정보 캐싱** (SQLite): 프로필 정보 로컬 저장
3. **이미지 캐싱** (디스크): 프로필 이미지 미리 다운로드
4. **Native 모듈**: C++로 작성된 Signal 라이브러리 사용
5. **병렬 처리**: 이미지 다운로드와 복호화 동시 진행

**성능 측정**:
```javascript
// 각 단계별 시간 측정
const startTime = Date.now();

// 1. 키 로드: ~50ms
const key = await loadKey(senderId);
console.log(`Key loaded: ${Date.now() - startTime}ms`);

// 2. 복호화: ~100ms
const decrypted = await decrypt(encrypted, key);
console.log(`Decrypted: ${Date.now() - startTime}ms`);

// 3. 사용자 정보: ~30ms (캐시)
const user = await getUser(senderId);
console.log(`User loaded: ${Date.now() - startTime}ms`);

// 4. 이미지 다운로드: ~1000ms
const image = await downloadImage(user.profileUrl);
console.log(`Image loaded: ${Date.now() - startTime}ms`);

// 총 시간: ~1180ms (✅ 30초 제약 안전)
```

### 9.2 배터리 최적화

**전략**:
1. **WebSocket 연결 관리**
   - 포그라운드: 항상 연결
   - 백그라운드: 5분 후 연결 해제
   - 종료: 연결 없음 (FCM만)

2. **하트비트 간격 조정**
   - 포그라운드: 30초
   - 백그라운드: 60초 (또는 연결 해제)

3. **알림 배칭 고려 안 함**
   - 요구사항: 모든 메시지 즉시 알림
   - 배터리보다 사용자 경험 우선

---

## 10. Phase별 구현 계획

### Phase 1 (MVP) - 2-3주

**목표**: 기본 알림 동작

✅ **구현 항목**:
- WebSocket 실시간 수신
- 포그라운드 인앱 알림
- 백그라운드 Silent Push + 복호화
- 로컬 알림 생성 (메시지 미리보기)
- 기본 소리/진동
- 앱 배지
- FCM 토큰 관리
- 전역 알림 설정 (켜기/끄기, 소리, 진동)

**개발 일정**:
| 작업 | 예상 시간 | 담당 |
|------|----------|------|
| WebSocket 통합 | 3일 | Backend |
| FCM 설정 (Android/iOS) | 2일 | Frontend |
| Notification Service Extension (iOS) | 4일 | iOS |
| FirebaseMessagingService (Android) | 3일 | Android |
| Signal 복호화 통합 | 3일 | Frontend |
| 인앱 배너 UI | 2일 | Frontend |
| 설정 UI (기본) | 2일 | Frontend |
| 테스트 및 디버깅 | 3일 | All |
| **총 계** | **22일** | |

**산출물**:
- 완전한 실시간 알림
- E2E 암호화 상태에서 메시지 미리보기
- 모든 메시지 즉시 알림

---

### Phase 2 - 1-2주

**목표**: 고급 알림 기능

✅ **추가 항목**:
- 채널별 알림 설정
- 일시적 음소거 (1시간, 24시간)
- 방해금지 모드
- 알림 그룹화 (같은 채팅방)
- 알림 액션 (답장, 읽음 표시)

**개발 일정**:
| 작업 | 예상 시간 |
|------|----------|
| 채널별 설정 DB/API | 2일 |
| 방해금지 모드 로직 | 2일 |
| 알림 액션 구현 | 3일 |
| 설정 UI 확장 | 2일 |
| 테스트 | 2일 |
| **총 계** | **11일** |

---

### Phase 3 - 1주

**목표**: 최적화 및 통계

✅ **추가 항목**:
- 알림 통계 (읽음률, 반응률)
- 스마트 알림 (중요도 학습)
- 성능 모니터링

---

## 11. 테스트 계획

### 11.1 테스트 시나리오

#### 시나리오 1: 포그라운드 알림
```
1. 앱을 켠다
2. 채팅 목록 화면에 있다
3. 다른 사용자가 메시지 전송
4. 확인사항:
   - 인앱 배너 표시됨
   - 채팅 목록 업데이트됨
   - 읽지 않은 배지 표시됨
   - 소리/진동 작동 (설정에 따라)
```

#### 시나리오 2: 백그라운드 알림
```
1. 앱을 백그라운드로 보낸다
2. 다른 사용자가 메시지 전송
3. 확인사항:
   - OS 알림 센터에 알림 표시됨
   - 메시지 내용 미리보기 표시됨
   - 프로필 이미지 표시됨
   - 앱 배지 업데이트됨
   - 알림 클릭 시 해당 채팅방 열림
```

#### 시나리오 3: 앱 종료 상태 알림
```
1. 앱을 완전히 종료한다
2. 다른 사용자가 메시지 전송
3. 확인사항:
   - 알림 표시됨
   - 메시지 내용 표시됨
   - 알림 클릭 시 앱 실행 → 채팅방 열림
```

#### 시나리오 4: 알림 설정
```
1. 설정에서 알림 끄기
2. 메시지 수신
3. 확인: 알림 표시 안 됨

4. 채널별로 알림 끄기
5. 메시지 수신
6. 확인: 해당 채팅방만 알림 안 옴
```

#### 시나리오 5: 방해금지 모드
```
1. 방해금지 모드 설정 (22:00 ~ 08:00)
2. 해당 시간대에 메시지 수신
3. 확인: 알림 표시 안 됨
4. 앱 열면 메시지 확인 가능
```

### 11.2 성능 테스트

```
복호화 시간 측정:
- 평균: < 100ms
- 최대: < 500ms

알림 생성 시간:
- 평균: < 2s
- 최대: < 5s (iOS 30초 제약 충분히 여유)

배터리 소모:
- 24시간 기준: < 5%
```

---

## 12. 트러블슈팅

### 12.1 일반적인 문제

#### iOS: 알림이 표시되지 않음
```
원인:
- 알림 권한 없음
- Production APNs 인증서 문제
- App Group 설정 누락

해결:
1. 설정 → 알림에서 권한 확인
2. Apple Developer에서 APNs 키 확인
3. Xcode에서 App Group 설정 확인
```

#### Android: FCM 메시지 수신 안 됨
```
원인:
- google-services.json 누락
- 토큰 등록 실패
- Doze 모드

해결:
1. google-services.json 확인
2. 로그에서 토큰 확인
3. Battery Optimization 제외 설정
```

#### 복호화 실패
```
원인:
- 키가 없음
- 키가 손상됨
- Signal 버전 불일치

해결:
1. 키 재동기화
2. 앱 재설치 (테스트)
3. 버전 확인
```

---

## 13. 보안 고려사항

### 13.1 키 저장

**iOS**:
```swift
// Keychain에 안전하게 저장
let keychain = KeychainAccess(
    service: "com.example.chat",
    accessGroup: "group.com.example.chat"
)

try keychain.set(signalKey, key: "signal_key_\(userId)")
```

**Android**:
```kotlin
// EncryptedSharedPreferences 사용
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "signal_keys",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

sharedPreferences.edit()
    .putString("signal_key_$userId", signalKey)
    .apply()
```

### 13.2 FCM 토큰 보안

- HTTPS 통신만 사용
- 토큰은 서버에만 저장
- 토큰 만료 시 자동 갱신
- 로그아웃 시 토큰 삭제

---

## 14. 모니터링

### 14.1 메트릭

```javascript
// 알림 성능 메트릭
{
  "notification_delivery_time": 150,  // ms
  "decryption_time": 85,              // ms
  "fcm_delivery_time": 1200,          // ms
  "notification_click_rate": 0.65,    // 65%
  "notification_error_rate": 0.02     // 2%
}
```

### 14.2 로깅

```kotlin
// 구조화된 로그
logger.info(
    "Notification delivered",
    mapOf(
        "user_id" to userId,
        "channel_id" to channelId,
        "message_id" to messageId,
        "delivery_method" to "fcm",
        "decryption_time_ms" to decryptionTime,
        "total_time_ms" to totalTime
    )
)
```

---

## 메모
- FCM 무료 할당량: 무제한 (단, 전송 속도 제한 있음)
- APNs: 무료
- 복호화 성능이 핵심 → Native 모듈 필수
- iOS 30초 제약 반드시 준수
