# Agent 6: Notification System Architect 🔔

**역할**: 알림 시스템 전문가  
**담당 작업**: Task 8 - 알림 고급 기능 (전역/채널별 알림 설정, 방해금지 모드)

---

## 📋 프로젝트 개요

**프로젝트명**: Cupid - 레즈비언 소개팅 앱 채팅 SDK  
**기술 스택**: Kotlin + Spring Boot 3.5.7  
**푸시 알림**: Firebase Cloud Messaging (FCM)  
**데이터베이스**: PostgreSQL  
**캐시**: Redis (음소거 상태 등)  
**현재 단계**: Phase 1 MVP 완성을 위한 기능 보완

---

## ✅ 현재 구현 상태

### 완료된 기능
- ✅ `UserNotificationSettings` 엔티티 존재
- ✅ `ChannelNotificationSettings` 엔티티 존재
- ✅ `FcmToken` 엔티티 및 기본 알림 기능 존재
- ✅ FCM 기본 전송 구현 완료 (`NotificationService`)
- ✅ Firebase Admin SDK 통합 완료

### 미구현 기능
- ❌ 알림 설정 API 미구현 (전역/채널별)
- ❌ 방해금지 모드 로직 미구현
- ❌ 음소거 기능 미구현
- ❌ 알림 전송 시 설정 확인 로직 미구현
- ❌ 설정 상속 로직 미구현 (전역 → 채널)

---

## 🔑 핵심 엔티티 및 구조

### UserNotificationSettings Entity
```kotlin
package com.august.cupid.model.entity.notification

@Entity
@Table(name = "user_notification_settings")
data class UserNotificationSettings(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,

    @Column(name = "sound_enabled", nullable = false)
    val soundEnabled: Boolean = true,

    @Column(name = "vibration_enabled", nullable = false)
    val vibrationEnabled: Boolean = true,

    @Column(name = "show_preview", nullable = false)
    val showPreview: Boolean = true,

    // 방해금지 모드
    @Column(name = "dnd_enabled", nullable = false)
    val dndEnabled: Boolean = false,

    @Column(name = "dnd_start_time", nullable = false)
    val dndStartTime: LocalTime = LocalTime.of(22, 0),

    @Column(name = "dnd_end_time", nullable = false)
    val dndEndTime: LocalTime = LocalTime.of(8, 0),

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dnd_days", nullable = false, columnDefinition = "integer[]")
    val dndDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=월요일, 7=일요일

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

### ChannelNotificationSettings Entity
```kotlin
@Entity
@Table(
    name = "channel_notification_settings",
    uniqueConstraints = [
        UniqueConstraint(
            name = "unique_channel_user_notification",
            columnNames = ["channel_id", "user_id"]
        )
    ]
)
data class ChannelNotificationSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    val channel: Channel,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,

    @Column(name = "sound_enabled", nullable = false)
    val soundEnabled: Boolean = true,

    @Column(name = "sound_name", nullable = false, length = 100)
    val soundName: String = "message.mp3",

    @Column(name = "vibration_enabled", nullable = false)
    val vibrationEnabled: Boolean = true,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "vibration_pattern", nullable = false)
    val vibrationPattern: List<Int> = listOf(0, 250, 250, 250),

    // 일시적 음소거
    @Column(name = "muted_until")
    val mutedUntil: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

### FcmToken Entity
```kotlin
@Entity
@Table(name = "fcm_tokens")
data class FcmToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "token", nullable = false, unique = true, length = 500)
    val token: String,

    @Column(name = "device_type", length = 50)
    val deviceType: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

### NotificationService (현재 구현)
```kotlin
@Service
class NotificationService(
    private val fcmDeliveryService: FcmDeliveryService,
    private val fcmTokenRepository: FcmTokenRepository
) {
    fun sendMessageNotification(
        channelId: UUID,
        senderId: UUID,
        messageContent: String,
        messageType: MessageType
    ): ApiResponse<Unit> {
        // FCM 전송 로직 (기본 구현)
        // 설정 확인 로직 없음 (구현 필요)
    }
}
```

---

## 📦 의존성 (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.google.firebase:firebase-admin:9.2.0")
}
```

---

## 🗄️ 데이터베이스 스키마

### user_notification_settings 테이블
```sql
CREATE TABLE user_notification_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    enabled BOOLEAN NOT NULL DEFAULT true,
    sound_enabled BOOLEAN NOT NULL DEFAULT true,
    vibration_enabled BOOLEAN NOT NULL DEFAULT true,
    show_preview BOOLEAN NOT NULL DEFAULT true,
    dnd_enabled BOOLEAN NOT NULL DEFAULT false,
    dnd_start_time TIME NOT NULL DEFAULT '22:00:00',
    dnd_end_time TIME NOT NULL DEFAULT '08:00:00',
    dnd_days INTEGER[] NOT NULL DEFAULT ARRAY[1,2,3,4,5,6,7],
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### channel_notification_settings 테이블
```sql
CREATE TABLE channel_notification_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES channels(id),
    user_id UUID NOT NULL REFERENCES users(id),
    enabled BOOLEAN NOT NULL DEFAULT true,
    sound_enabled BOOLEAN NOT NULL DEFAULT true,
    sound_name VARCHAR(100) NOT NULL DEFAULT 'message.mp3',
    vibration_enabled BOOLEAN NOT NULL DEFAULT true,
    vibration_pattern INTEGER[] NOT NULL DEFAULT ARRAY[0,250,250,250],
    muted_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (channel_id, user_id),
    INDEX idx_channel_notification_settings_muted (muted_until)
);
```

---

## 🎯 구현해야 할 작업

### Task 8.1: 전역 알림 설정 API (2시간)

**요구사항**:
- [ ] `GET /api/v1/notifications/settings` - 설정 조회
- [ ] `PUT /api/v1/notifications/settings` - 설정 업데이트
- [ ] 방해금지 모드 로직 (시간대, 요일 확인)
- [ ] 설정 없으면 기본값으로 생성

**구현 예시**:
```kotlin
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    @GetMapping("/settings")
    fun getSettings(@AuthenticationPrincipal userId: UUID): ApiResponse<UserNotificationSettingsResponse> {
        return notificationService.getUserSettings(userId)
    }
    
    @PutMapping("/settings")
    fun updateSettings(
        @AuthenticationPrincipal userId: UUID,
        @RequestBody request: UpdateNotificationSettingsRequest
    ): ApiResponse<UserNotificationSettingsResponse> {
        return notificationService.updateUserSettings(userId, request)
    }
}
```

### Task 8.2: 채널별 알림 설정 API (2-3시간)

**요구사항**:
- [ ] `GET /api/v1/channels/{channelId}/notifications/settings` - 채널 설정 조회
- [ ] `PUT /api/v1/channels/{channelId}/notifications/settings` - 채널 설정 업데이트
- [ ] `POST /api/v1/channels/{channelId}/notifications/mute` - 음소거 (1시간/24시간)
- [ ] `POST /api/v1/channels/{channelId}/notifications/unmute` - 음소거 해제
- [ ] Redis TTL 활용 (음소거 만료 자동 처리)

**구현 예시**:
```kotlin
@PostMapping("/channels/{channelId}/notifications/mute")
fun muteChannel(
    @PathVariable channelId: UUID,
    @RequestParam duration: Int, // 1 (1시간) 또는 24 (24시간)
    @AuthenticationPrincipal userId: UUID
): ApiResponse<Unit> {
    val muteUntil = LocalDateTime.now().plusHours(duration.toLong())
    return notificationService.muteChannel(channelId, userId, muteUntil)
}
```

### Task 8.3: 알림 전송 시 설정 확인 로직

**요구사항**:
- [ ] `NotificationService.sendMessageNotification`에서 설정 확인
- [ ] 전역 설정 → 채널별 설정 상속/우선순위
- [ ] 방해금지 모드 확인 (시간대, 요일)
- [ ] 음소거 확인 (Redis 캐시)
- [ ] 설정에 따라 알림 전송 여부 결정

**구현 예시**:
```kotlin
fun sendMessageNotification(...): ApiResponse<Unit> {
    // 1. 전역 설정 확인
    val globalSettings = getUserNotificationSettings(userId)
    if (!globalSettings.enabled) {
        return ApiResponse.error("알림이 비활성화되어 있습니다")
    }
    
    // 2. 방해금지 모드 확인
    if (isDoNotDisturbActive(globalSettings)) {
        return ApiResponse.error("방해금지 시간입니다")
    }
    
    // 3. 채널별 설정 확인
    val channelSettings = getChannelNotificationSettings(channelId, userId)
    if (!channelSettings.enabled) {
        return ApiResponse.error("채널 알림이 비활성화되어 있습니다")
    }
    
    // 4. 음소거 확인
    if (isChannelMuted(channelId, userId)) {
        return ApiResponse.error("채널이 음소거되어 있습니다")
    }
    
    // 5. FCM 전송
    return fcmDeliveryService.sendNotification(...)
}

private fun isDoNotDisturbActive(settings: UserNotificationSettings): Boolean {
    if (!settings.dndEnabled) return false
    
    val now = LocalTime.now()
    val currentDay = LocalDate.now().dayOfWeek.value
    
    // 요일 확인
    if (!settings.dndDays.contains(currentDay)) return false
    
    // 시간대 확인
    val isInDndTime = when {
        settings.dndStartTime <= settings.dndEndTime -> {
            // 같은 날 범위 (예: 22:00 ~ 08:00 → 다음날)
            now >= settings.dndStartTime || now <= settings.dndEndTime
        }
        else -> {
            // 다음날로 넘어가는 범위
            now >= settings.dndStartTime || now <= settings.dndEndTime
        }
    }
    
    return isInDndTime
}
```

---

## 📝 기존 코드 패턴

### FCM 전송 패턴
```kotlin
@Service
class FcmDeliveryService {
    fun sendNotification(token: String, title: String, body: String) {
        val message = Message.builder()
            .setToken(token)
            .setNotification(Notification(title, body))
            .build()
        
        FirebaseMessaging.getInstance().send(message)
    }
}
```

### Redis TTL 패턴
```kotlin
fun muteChannel(channelId: UUID, userId: UUID, muteUntil: LocalDateTime) {
    val key = "channel:muted:$channelId:$userId"
    val ttlSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), muteUntil)
    
    redisTemplate.opsForValue().set(
        key,
        "1",
        ttlSeconds,
        TimeUnit.SECONDS
    )
    
    // DB에도 저장 (Redis는 캐시)
    channelNotificationSettingsRepository.updateMutedUntil(channelId, userId, muteUntil)
}
```

---

## 🔧 설정 파일 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/chatsdk
  redis:
    host: localhost
    port: 6379

firebase:
  credentials:
    path: classpath:firebase-service-account.json
```

---

## 📚 참고 문서

1. **알림 시스템 스펙**: `documents/specifications/notification-system-spec.md` 섹션 5, 7
2. **작업 목록**: `documents/tasks/today-tasks.md` - Task 8

---

## 💡 구현 가이드

### 구현 순서
1. 전역 알림 설정 API 구현 (GET/PUT)
2. 채널별 알림 설정 API 구현 (GET/PUT)
3. 음소거 API 구현 (Redis TTL 활용)
4. 알림 전송 시 설정 확인 로직 통합
5. 방해금지 모드 로직 구현
6. 설정 상속 로직 구현 (전역 → 채널)
7. 테스트 케이스 작성

### 방해금지 모드 구현
- 시간대: `LocalTime` 비교
- 요일: `LocalDate.now().dayOfWeek.value` 사용 (1=월요일, 7=일요일)
- 타임존 고려: UTC 기준 또는 사용자 타임존 설정

### 음소거 구현
- Redis: 빠른 확인을 위한 캐시 (TTL 자동 만료)
- PostgreSQL: 영구 저장 (Redis 캐시 미스 시 참조)
- 만료 체크: Redis TTL 또는 `mutedUntil` 비교

---

## 🎯 MEGA PROMPT (시작 시 사용)

```
You are a notification system expert for a chat application.

Existing:
- FCM basic implementation works
- UserNotificationSettings entity exists
- ChannelNotificationSettings entity exists
- Firebase Admin SDK integrated

Requirements:
- Global notification settings API
- Per-channel notification settings
- Do Not Disturb mode with time ranges
- Mute functionality (1 hour, 24 hours)

Please provide:
1. Notification settings REST API endpoints
2. DND logic with timezone handling
3. Temporary mute with Redis TTL
4. Notification preference inheritance (global -> channel)
5. Integration with existing NotificationService
6. Error handling and validation
7. Test cases

Consider:
- How to handle timezone differences?
- Redis vs PostgreSQL for mute state?
- Performance: Cache notification settings?
- Privacy: Should admins see notification settings?
```

