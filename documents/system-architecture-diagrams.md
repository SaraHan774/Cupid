# Cupid 시스템 아키텍처 다이어그램

## 목차
1. [시스템 개요](#1-시스템-개요)
2. [레이어 구조](#2-레이어-구조)
3. [인증 및 보안 모듈](#3-인증-및-보안-모듈)
4. [메시징 모듈](#4-메시징-모듈)
5. [실시간 통신 모듈](#5-실시간-통신-모듈)
6. [알림 시스템](#6-알림-시스템)
7. [Signal Protocol 암호화 시스템](#7-signal-protocol-암호화-시스템)
8. [데이터베이스 관계도](#8-데이터베이스-관계도)

---

## 1. 시스템 개요

```mermaid
graph TB
    subgraph "Client Layer"
        Client[클라이언트 앱<br/>React Native]
        WebSocketClient[WebSocket 클라이언트<br/>STOMP over SockJS]
    end
    
    subgraph "API Gateway Layer"
        Security[Security Config<br/>JWT 인증 + Rate Limit]
        Health[Health Controller<br/>시스템 상태 체크]
    end
    
    subgraph "Controller Layer"
        AuthCtrl[Auth Controller<br/>인증/회원가입]
        ProfileCtrl[Profile Controller<br/>프로필 관리]
        ChannelCtrl[Channel Controller<br/>채널 관리]
        MessageCtrl[Message Controller<br/>메시지 CRUD]
        ChatCtrl[Chat Controller<br/>WebSocket 메시징]
        RealtimeCtrl[Realtime Controller<br/>실시간 기능]
        NotificationCtrl[Notification Controller<br/>알림 설정]
        OnlineStatusCtrl[Online Status Controller<br/>온라인 상태]
        KeyExchangeCtrl[Key Exchange Controller<br/>키 교환]
        AdminCtrl[Admin Controllers<br/>관리자 기능]
    end
    
    subgraph "Service Layer"
        AuthSvc[Auth Service<br/>인증 로직]
        UserSvc[User Service<br/>사용자 관리]
        ProfileSvc[Profile Image Service<br/>프로필 이미지]
        ChannelSvc[Channel Service<br/>채널 비즈니스 로직]
        MessageSvc[Message Service<br/>메시지 처리]
        EncryptionSvc[Encryption Service<br/>암호화 유틸]
        SignalSvc[Signal Protocol Service<br/>Signal Protocol]
        NotificationSvc[Notification Service<br/>알림 처리]
        OnlineSvc[Online Status Service<br/>온라인 상태]
        TypingSvc[Typing Indicator Service<br/>타이핑 표시]
        ReadReceiptSvc[Read Receipt Service<br/>읽음 표시]
        MatchSvc[Match Service<br/>매칭 관리]
        FcmSvc[FCM Delivery Service<br/>푸시 알림]
        AuditSvc[Security Audit Logger<br/>보안 감사]
    end
    
    subgraph "Repository Layer"
        UserRepo[User Repository]
        ChannelRepo[Channel Repository]
        MessageRepo[Message Repository<br/>MongoDB]
        SignalRepo[Signal Repositories<br/>키 관리]
        NotificationRepo[Notification Repositories<br/>알림 설정]
        MatchRepo[Match Repository]
        ReportRepo[Report Repository]
    end
    
    subgraph "Infrastructure Layer"
        PostgreSQL[(PostgreSQL<br/>관계형 데이터)]
        MongoDB[(MongoDB<br/>메시지 저장)]
        Redis[(Redis<br/>캐시 + Pub/Sub)]
        Firebase[Firebase<br/>FCM 푸시]
        Storage[S3/Storage<br/>파일 저장]
    end
    
    subgraph "WebSocket Layer"
        WSConfig[WebSocket Config<br/>STOMP 설정]
        WSInterceptor[Connection Interceptor<br/>인증 처리]
        WSHandler[WebSocket Message Handler<br/>메시지 라우팅]
        WSMonitor[WebSocket Connection Monitor<br/>연결 관리]
    end
    
    subgraph "Background Tasks"
        Scheduler[Key Rotation Scheduler<br/>키 자동 교체]
    end
    
    Client --> WebSocketClient
    Client --> Security
    WebSocketClient --> WSConfig
    Security --> AuthCtrl
    Security --> ProfileCtrl
    Security --> ChannelCtrl
    Security --> MessageCtrl
    Security --> NotificationCtrl
    Security --> OnlineStatusCtrl
    Security --> KeyExchangeCtrl
    
    AuthCtrl --> AuthSvc
    ProfileCtrl --> ProfileSvc
    ChannelCtrl --> ChannelSvc
    MessageCtrl --> MessageSvc
    ChatCtrl --> MessageSvc
    RealtimeCtrl --> OnlineSvc
    RealtimeCtrl --> TypingSvc
    RealtimeCtrl --> ReadReceiptSvc
    NotificationCtrl --> NotificationSvc
    OnlineStatusCtrl --> OnlineSvc
    KeyExchangeCtrl --> SignalSvc
    
    AuthSvc --> UserRepo
    ProfileSvc --> Storage
    ProfileSvc --> UserRepo
    ChannelSvc --> ChannelRepo
    MessageSvc --> MessageRepo
    SignalSvc --> SignalRepo
    NotificationSvc --> NotificationRepo
    NotificationSvc --> FcmSvc
    MatchSvc --> MatchRepo
    
    UserRepo --> PostgreSQL
    ChannelRepo --> PostgreSQL
    SignalRepo --> PostgreSQL
    NotificationRepo --> PostgreSQL
    MatchRepo --> PostgreSQL
    MessageRepo --> MongoDB
    FcmSvc --> Firebase
    
    OnlineSvc --> Redis
    TypingSvc --> Redis
    WSHandler --> Redis
    NotificationSvc --> Redis
    
    WSConfig --> WSInterceptor
    WSInterceptor --> WSHandler
    WSHandler --> WSMonitor
    WSMonitor --> Redis
    
    Scheduler --> SignalSvc
    Scheduler --> SignalRepo
    
    style Client fill:#e1f5ff
    style PostgreSQL fill:#336791
    style MongoDB fill:#47A248
    style Redis fill:#DC382D
    style Firebase fill:#FFA000
```

---

## 2. 레이어 구조

```mermaid
graph TB
    subgraph "Presentation Layer"
        direction TB
        RestControllers[REST Controllers<br/>13개 컨트롤러]
        WebSocketControllers[WebSocket Controllers<br/>STOMP 핸들러]
    end
    
    subgraph "Business Logic Layer"
        direction TB
        Services[Services<br/>15개 서비스]
    end
    
    subgraph "Data Access Layer"
        direction TB
        Repositories[Repositories<br/>17개 리포지토리]
    end
    
    subgraph "Domain Model Layer"
        direction TB
        Entities[Entities<br/>엔티티 클래스]
        DTOs[DTOs<br/>데이터 전송 객체]
    end
    
    subgraph "Infrastructure Layer"
        direction TB
        Config[Configuration<br/>6개 Config 클래스]
        Security[Security Components<br/>인증/보안 필터]
        Utils[Utility Classes<br/>유틸리티]
        Schedulers[Background Schedulers<br/>스케줄러]
    end
    
    RestControllers --> Services
    WebSocketControllers --> Services
    Services --> Repositories
    Repositories --> Entities
    Services --> DTOs
    Security --> Services
    Config --> Services
    Utils --> Services
    Schedulers --> Services
    
    style RestControllers fill:#e3f2fd
    style Services fill:#fff3e0
    style Repositories fill:#f3e5f5
    style Entities fill:#e8f5e9
    style Config fill:#fce4ec
```

### 2.1 컨트롤러 상세

```mermaid
classDiagram
    class HealthController {
        -firebaseMessaging: FirebaseMessaging
        -dataSource: DataSource
        -mongoTemplate: MongoTemplate
        -redisTemplate: RedisTemplate
        -encryptionService: EncryptionService
        +health(): ResponseEntity
        +checkFCMStatus(): Map
        +checkPostgreSQLStatus(): Map
        +checkMongoDBStatus(): Map
        +checkRedisStatus(): Map
        +checkEncryptionStatus(): Map
    }
    
    class AuthController {
        -authService: AuthService
        +register(request): ResponseEntity
        +login(request): ResponseEntity
        +refreshToken(refreshToken): ResponseEntity
        +logout(): ResponseEntity
    }
    
    class ProfileController {
        -profileImageService: ProfileImageService
        -userService: UserService
        +uploadProfileImage(userId, file): ResponseEntity
        +getProfile(userId): ResponseEntity
        +updateProfile(userId, request): ResponseEntity
    }
    
    class ChannelController {
        -channelService: ChannelService
        +createChannel(request): ResponseEntity
        +getChannels(userId, page, size): ResponseEntity
        +getChannel(channelId): ResponseEntity
        +leaveChannel(channelId, userId): ResponseEntity
        +addMembers(channelId, request): ResponseEntity
    }
    
    class MessageController {
        -messageService: MessageService
        +getMessages(channelId, page, size): ResponseEntity
        +getMessage(messageId): ResponseEntity
    }
    
    class ChatController {
        -messageService: MessageService
        -onlineStatusService: OnlineStatusService
        -notificationService: NotificationService
        -encryptionService: EncryptionService
        -sim messagingTemplate: SimpMessagingTemplate
        +sendMessage(message, headerAccessor): MessageResponse
    }
    
    class RealtimeWebSocketController {
        -onlineStatusService: OnlineStatusService
        -typingIndicatorService: TypingIndicatorService
        -readReceiptService: ReadReceiptService
        +handleTypingIndicator(payload, headerAccessor): void
        +handleReadReceipt(payload, headerAccessor): void
        +handleOnlineStatus(payload, headerAccessor): void
    }
    
    class NotificationController {
        -notificationService: NotificationService
        +registerFcmToken(userId, request): ResponseEntity
        +getUserNotificationSettings(userId): ResponseEntity
        +updateUserNotificationSettings(userId, request): ResponseEntity
        +getChannelNotificationSettings(channelId, userId): ResponseEntity
        +updateChannelNotificationSettings(channelId, userId, request): ResponseEntity
    }
    
    class OnlineStatusController {
        -onlineStatusService: OnlineStatusService
        +getOnlineStatus(userId): ResponseEntity
        +getOnlineUsers(userIds): ResponseEntity
    }
    
    class KeyExchangeController {
        -signalProtocolService: SignalProtocolService
        +generateKeys(userId, request): ResponseEntity
        +getKeys(userId): ResponseEntity
        +initiateKeyExchange(request): ResponseEntity
        +getPreKeyBundle(recipientId): ResponseEntity
    }
    
    class AdminDashboardController {
        +getDashboardStats(): ResponseEntity
        +getUserStats(): ResponseEntity
        +getChannelStats(): ResponseEntity
        +getMessageStats(): ResponseEntity
    }
    
    class AdminKeyRotationController {
        -signalProtocolService: SignalProtocolService
        +rotateAllKeys(): ResponseEntity
        +getRotationHistory(): ResponseEntity
    }
    
    class SecurityAuditController {
        -auditLogger: SecurityAuditLogger
        +getAuditLogs(filters): ResponseEntity
    }
```

---

## 3. 인증 및 보안 모듈

```mermaid
graph LR
    subgraph "Security Layer"
        JwtFilter[JWT Authentication Filter<br/>토큰 검증]
        RateLimitFilter[Rate Limit Filter<br/>요청 제한]
        SecurityConfig[Security Config<br/>Spring Security 설정]
    end
    
    subgraph "Authentication Services"
        AuthService[Auth Service<br/>인증 로직]
        JwtUtil[JWT Util<br/>토큰 생성/검증]
        TokenBlacklistService[Token Blacklist Service<br/>로그아웃 토큰 관리]
    end
    
    subgraph "User Management"
        UserService[User Service<br/>사용자 관리]
        UserRepository[User Repository]
        UserEntity[(User Entity)]
    end
    
    subgraph "Rate Limiting"
        RateLimitService[Rate Limit Service<br/>Bucket4j + Redis]
        Redis[(Redis<br/>요청 카운터)]
    end
    
    JwtFilter --> AuthService
    JwtFilter --> JwtUtil
    RateLimitFilter --> RateLimitService
    RateLimitFilter --> Redis
    
    AuthService --> UserService
    AuthService --> JwtUtil
    AuthService --> TokenBlacklistService
    
    UserService --> UserRepository
    UserRepository --> UserEntity
    
    TokenBlacklistService --> Redis
    
    SecurityConfig --> JwtFilter
    SecurityConfig --> RateLimitFilter
    
    style JwtFilter fill:#ffebee
    style RateLimitFilter fill:#fff3e0
    style AuthService fill:#e3f2fd
    style Redis fill:#DC382D
```

---

## 4. 메시징 모듈

```mermaid
graph TB
    subgraph "Message Controllers"
        MessageCtrl[Message Controller<br/>REST API]
        ChatCtrl[Chat Controller<br/>WebSocket]
    end
    
    subgraph "Message Services"
        MessageService[Message Service<br/>메시지 비즈니스 로직]
        EncryptionService[Encryption Service<br/>암호화 유틸]
        SignalProtocolService[Signal Protocol Service<br/>E2E 암호화]
    end
    
    subgraph "Message Storage"
        MessageRepository[Message Repository<br/>MongoDB]
        MessageReadsRepository[Message Reads Repository<br/>읽음 표시]
    end
    
    subgraph "Channel Management"
        ChannelService[Channel Service<br/>채널 관리]
        ChannelRepository[Channel Repository]
        ChannelMembersRepository[Channel Members Repository]
    end
    
    subgraph "Real-time Delivery"
        WebSocketHandler[WebSocket Message Handler]
        RedisPubSub[(Redis Pub/Sub<br/>메시지 브로드캐스트)]
    end
    
    MessageCtrl --> MessageService
    ChatCtrl --> MessageService
    ChatCtrl --> EncryptionService
    
    MessageService --> SignalProtocolService
    MessageService --> ChannelService
    MessageService --> MessageRepository
    MessageService --> MessageReadsRepository
    MessageService --> WebSocketHandler
    
    ChannelService --> ChannelRepository
    ChannelService --> ChannelMembersRepository
    
    EncryptionService --> SignalProtocolService
    
    WebSocketHandler --> RedisPubSub
    
    MessageRepository --> MongoDB[(MongoDB)]
    ChannelRepository --> PostgreSQL[(PostgreSQL)]
    
    style MessageService fill:#e3f2fd
    style SignalProtocolService fill:#fff3e0
    style MongoDB fill:#47A248
    style RedisPubSub fill:#DC382D
```

### 4.1 메시지 전송 플로우

```mermaid
sequenceDiagram
    participant Client
    participant ChatController
    participant MessageService
    participant SignalService
    participant MessageRepository
    participant MongoDB
    participant WebSocketHandler
    participant Redis
    participant Recipient
    
    Client->>ChatController: WebSocket 메시지 전송
    ChatController->>MessageService: sendMessage()
    MessageService->>SignalService: encryptMessage()
    SignalService-->>MessageService: 암호화된 메시지
    MessageService->>MessageRepository: save()
    MessageRepository->>MongoDB: 메시지 저장
    MongoDB-->>MessageRepository: 저장 완료
    MessageService->>WebSocketHandler: 브로드캐스트
    WebSocketHandler->>Redis: Pub/Sub 전송
    Redis->>Recipient: 실시간 전달
    Recipient->>SignalService: decryptMessage()
    SignalService-->>Recipient: 복호화된 메시지
```

---

## 5. 실시간 통신 모듈

```mermaid
graph TB
    subgraph "WebSocket Infrastructure"
        WebSocketConfig[WebSocket Config<br/>STOMP 설정]
        ConnectionInterceptor[Connection Interceptor<br/>인증 처리]
        StompChannelInterceptor[STOMP Channel Interceptor<br/>메시지 검증]
        WebSocketEventListener[WebSocket Event Listener<br/>연결 이벤트]
    end
    
    subgraph "Real-time Services"
        OnlineStatusService[Online Status Service<br/>온라인 상태]
        TypingIndicatorService[Typing Indicator Service<br/>타이핑 표시]
        ReadReceiptService[Read Receipt Service<br/>읽음 표시]
    end
    
    subgraph "Connection Management"
        WebSocketConnectionMonitor[WebSocket Connection Monitor<br/>연결 추적]
        WebSocketMessageHandler[WebSocket Message Handler<br/>메시지 라우팅]
    end
    
    subgraph "Redis State"
        OnlineStatus[(Redis<br/>온라인 상태)]
        TypingStatus[(Redis<br/>타이핑 상태)]
        ReadReceipts[(Redis<br/>읽음 표시)]
        PubSub[(Redis Pub/Sub<br/>실시간 이벤트)]
    end
    
    WebSocketConfig --> ConnectionInterceptor
    ConnectionInterceptor --> StompChannelInterceptor
    WebSocketEventListener --> WebSocketConnectionMonitor
    
    OnlineStatusService --> OnlineStatus
    TypingIndicatorService --> TypingStatus
    ReadReceiptService --> ReadReceipts
    
    WebSocketMessageHandler --> PubSub
    WebSocketConnectionMonitor --> OnlineStatus
    
    style WebSocketConfig fill:#e3f2fd
    style OnlineStatusService fill:#fff3e0
    style Redis fill:#DC382D
```

---

## 6. 알림 시스템

```mermaid
graph TB
    subgraph "Notification Controllers"
        NotificationController[Notification Controller<br/>알림 설정 관리]
    end
    
    subgraph "Notification Services"
        NotificationService[Notification Service<br/>알림 로직]
        FcmDeliveryService[FCM Delivery Service<br/>푸시 전송]
    end
    
    subgraph "Notification Settings"
        UserNotificationSettingsRepository[User Notification Settings<br/>전역 설정]
        ChannelNotificationSettingsRepository[Channel Notification Settings<br/>채널별 설정]
        FcmTokenRepository[FCM Token Repository<br/>디바이스 토큰]
    end
    
    subgraph "External Services"
        Firebase[Firebase Cloud Messaging<br/>푸시 알림 서비스]
    end
    
    subgraph "Notification Entities"
        UserNotificationSettings[User Notification Settings Entity<br/>방해금지 모드 등]
        ChannelNotificationSettings[Channel Notification Settings Entity<br/>채널별 설정]
        FcmToken[FCM Token Entity<br/>디바이스 토큰]
    end
    
    NotificationController --> NotificationService
    NotificationService --> FcmDeliveryService
    NotificationService --> UserNotificationSettingsRepository
    NotificationService --> ChannelNotificationSettingsRepository
    NotificationService --> FcmTokenRepository
    
    FcmDeliveryService --> Firebase
    
    UserNotificationSettingsRepository --> UserNotificationSettings
    ChannelNotificationSettingsRepository --> ChannelNotificationSettings
    FcmTokenRepository --> FcmToken
    
    UserNotificationSettings --> PostgreSQL[(PostgreSQL)]
    ChannelNotificationSettings --> PostgreSQL
    FcmToken --> PostgreSQL
    
    style NotificationService fill:#e3f2fd
    style FcmDeliveryService fill:#fff3e0
    style Firebase fill:#FFA000
```

### 6.1 알림 전송 플로우

```mermaid
sequenceDiagram
    participant Sender
    participant MessageService
    participant NotificationService
    participant FcmService
    participant Firebase
    participant ClientDevice
    participant NotificationExtension
    
    Sender->>MessageService: 메시지 전송
    MessageService->>NotificationService: 메시지 저장 후 알림 요청
    NotificationService->>NotificationService: 알림 설정 확인
    NotificationService->>FcmService: 푸시 전송 요청
    FcmService->>Firebase: FCM API 호출
    Firebase->>ClientDevice: Silent Push 전송
    ClientDevice->>NotificationExtension: 백그라운드 실행
    NotificationExtension->>NotificationExtension: Signal 복호화
    NotificationExtension->>ClientDevice: 로컬 알림 생성
    ClientDevice->>ClientDevice: 알림 표시
```

---

## 7. Signal Protocol 암호화 시스템

```mermaid
graph TB
    subgraph "Encryption Controllers"
        KeyExchangeController[Key Exchange Controller<br/>키 교환 API]
        AdminKeyRotationController[Admin Key Rotation Controller<br/>관리자 키 관리]
    end
    
    subgraph "Encryption Services"
        SignalProtocolService[Signal Protocol Service<br/>메인 암호화 서비스]
        EncryptionService[Encryption Service<br/>암호화 유틸리티]
        DatabaseSignalProtocolStore[Database Signal Protocol Store<br/>키 저장소 구현]
    end
    
    subgraph "Key Management"
        KeyEncryptionUtil[Key Encryption Util<br/>키 암호화]
        KeyRotationScheduler[Key Rotation Scheduler<br/>자동 키 교체]
    end
    
    subgraph "Signal Protocol Repositories"
        SignalIdentityRepository[Signal Identity Repository<br/>Identity Key]
        SignalPreKeyRepository[Signal Pre Key Repository<br/>Pre Keys]
        SignalSignedPreKeyRepository[Signal Signed Pre Key Repository<br/>Signed Pre Key]
        SignalSessionRepository[Signal Session Repository<br/>세션 정보]
        UserKeysRepository[User Keys Repository<br/>사용자 키 쌍]
        KeyRotationHistoryRepository[Key Rotation History Repository<br/>키 교체 이력]
    end
    
    subgraph "Signal Protocol Entities"
        SignalIdentity[Signal Identity<br/>Identity Key]
        SignalPreKey[Signal Pre Key<br/>One-time Pre Key]
        SignalSignedPreKey[Signal Signed Pre Key<br/>Signed Pre Key]
        SignalSession[Signal Session<br/>세션 상태]
        UserKeys[User Keys<br/>사용자 키 쌍]
        KeyRotationHistory[Key Rotation History<br/>교체 이력]
    end
    
    KeyExchangeController --> SignalProtocolService
    AdminKeyRotationController --> SignalProtocolService
    
    SignalProtocolService --> EncryptionService
    SignalProtocolService --> DatabaseSignalProtocolStore
    SignalProtocolService --> KeyEncryptionUtil
    
    DatabaseSignalProtocolStore --> SignalIdentityRepository
    DatabaseSignalProtocolStore --> SignalPreKeyRepository
    DatabaseSignalProtocolStore --> SignalSignedPreKeyRepository
    DatabaseSignalProtocolStore --> SignalSessionRepository
    
    KeyRotationScheduler --> SignalProtocolService
    KeyRotationScheduler --> KeyRotationHistoryRepository
    
    SignalIdentityRepository --> SignalIdentity
    SignalPreKeyRepository --> SignalPreKey
    SignalSignedPreKeyRepository --> SignalSignedPreKey
    SignalSessionRepository --> SignalSession
    UserKeysRepository --> UserKeys
    KeyRotationHistoryRepository --> KeyRotationHistory
    
    SignalIdentity --> PostgreSQL[(PostgreSQL)]
    SignalPreKey --> PostgreSQL
    SignalSignedPreKey --> PostgreSQL
    SignalSession --> PostgreSQL
    UserKeys --> PostgreSQL
    KeyRotationHistory --> PostgreSQL
    
    style SignalProtocolService fill:#e3f2fd
    style KeyEncryptionUtil fill:#fff3e0
    style PostgreSQL fill:#336791
```

### 7.1 Signal Protocol 키 교환 플로우

```mermaid
sequenceDiagram
    participant Alice
    participant KeyExchangeController
    participant SignalProtocolService
    participant DatabaseStore
    participant PostgreSQL
    participant Bob
    
    Alice->>KeyExchangeController: 키 생성 요청
    KeyExchangeController->>SignalProtocolService: generateKeys()
    SignalProtocolService->>DatabaseStore: Identity Key 생성
    SignalProtocolService->>DatabaseStore: Signed Pre Key 생성
    SignalProtocolService->>DatabaseStore: Pre Keys 생성 (100개)
    DatabaseStore->>PostgreSQL: 키 저장
    
    Alice->>KeyExchangeController: Bob과 키 교환 시작
    KeyExchangeController->>SignalProtocolService: initiateKeyExchange()
    SignalProtocolService->>DatabaseStore: Bob의 Pre Key Bundle 조회
    DatabaseStore->>PostgreSQL: 조회
    PostgreSQL-->>DatabaseStore: Pre Key Bundle
    DatabaseStore-->>SignalProtocolService: 반환
    SignalProtocolService->>SignalProtocolService: X3DH 키 교환 수행
    SignalProtocolService->>DatabaseStore: 세션 저장
    DatabaseStore->>PostgreSQL: 세션 저장
    SignalProtocolService-->>Alice: 세션 초기화 완료
    
    Alice->>SignalProtocolService: 메시지 암호화
    SignalProtocolService->>SignalProtocolService: Double Ratchet 수행
    SignalProtocolService-->>Alice: 암호화된 메시지
    
    Alice->>Bob: 암호화된 메시지 전송
    Bob->>SignalProtocolService: 메시지 복호화
    SignalProtocolService->>SignalProtocolService: Double Ratchet 수행
    SignalProtocolService-->>Bob: 복호화된 메시지
```

---

## 8. 데이터베이스 관계도

```mermaid
erDiagram
    USER ||--o{ USER_KEYS : "has"
    USER ||--o{ CHANNEL_MEMBERS : "participates"
    USER ||--o{ MATCH : "user1_id or user2_id"
    USER ||--o{ USER_BLOCKS : "blocker_id or blocked_id"
    USER ||--o{ REPORT : "submitter_id or target_user_id"
    USER ||--o{ FCM_TOKEN : "has"
    USER ||--|| USER_NOTIFICATION_SETTINGS : "has"
    
    CHANNEL ||--o{ CHANNEL_MEMBERS : "contains"
    CHANNEL ||--o{ MESSAGE : "contains"
    CHANNEL ||--o| MATCH : "linked_to"
    CHANNEL ||--o{ CHANNEL_NOTIFICATION_SETTINGS : "has_settings"
    
    USER ||--o{ CHANNEL_NOTIFICATION_SETTINGS : "has"
    
    MESSAGE ||--o{ MESSAGE_READS : "read_by"
    
    USER_KEYS ||--o{ SIGNAL_IDENTITY : "has"
    USER_KEYS ||--o{ SIGNAL_SIGNED_PRE_KEY : "has"
    USER_KEYS ||--o{ SIGNAL_PRE_KEY : "has"
    USER_KEYS ||--o{ SIGNAL_SESSION : "has"
    USER_KEYS ||--o{ KEY_ROTATION_HISTORY : "has_history"
    
    USER {
        uuid id PK
        string username UK
        string password_hash
        string email UK
        string profile_image_url
        string profile_thumbnail_url
        string profile_image_blurhash
        jsonb profile_image_metadata
        timestamp created_at
        timestamp updated_at
        timestamp last_seen_at
        boolean is_active
    }
    
    CHANNEL {
        uuid id PK
        enum type
        string name
        uuid creator_id FK
        uuid match_id FK
        timestamp created_at
        timestamp updated_at
    }
    
    CHANNEL_MEMBERS {
        uuid id PK
        uuid channel_id FK
        uuid user_id FK
        enum role
        timestamp joined_at
        timestamp left_at
        timestamp last_read_at
        boolean is_active
    }
    
    MESSAGE {
        uuid id PK
        uuid channel_id FK
        uuid sender_id FK
        text encrypted_content
        enum message_type
        enum status
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
        jsonb file_metadata
        jsonb edit_history
        jsonb metadata
    }
    
    USER_KEYS {
        uuid id PK
        uuid user_id FK
        text identity_public_key
        text identity_private_key_encrypted
        timestamp created_at
        timestamp expires_at
    }
    
    SIGNAL_IDENTITY {
        uuid id PK
        uuid user_id FK
        text identity_key_public
        text identity_key_private_encrypted
    }
    
    SIGNAL_SIGNED_PRE_KEY {
        uuid id PK
        uuid user_id FK
        int key_id
        text public_key
        text signature
        timestamp created_at
        timestamp expires_at
    }
    
    SIGNAL_PRE_KEY {
        uuid id PK
        uuid user_id FK
        int key_id
        text public_key
        timestamp created_at
    }
    
    SIGNAL_SESSION {
        uuid id PK
        uuid user_id FK
        uuid recipient_id FK
        text session_data
        timestamp created_at
        timestamp updated_at
    }
    
    MATCH {
        uuid id PK
        uuid user1_id FK
        uuid user2_id FK
        enum status
        timestamp matched_at
        timestamp expires_at
    }
    
    USER_BLOCKS {
        uuid id PK
        uuid blocker_id FK
        uuid blocked_id FK
        timestamp created_at
    }
    
    REPORT {
        uuid id PK
        uuid submitter_id FK
        uuid target_user_id FK
        uuid target_message_id FK
        text reported_content
        text reported_content_hash
        text screenshot_url
        uuid_array context_message_ids
        enum report_type
        text reason
        enum status
        timestamp created_at
        timestamp resolved_at
        uuid resolver_id FK
    }
    
    FCM_TOKEN {
        uuid id PK
        uuid user_id FK
        string token UK
        enum device_type
        string device_name
        string app_version
        timestamp created_at
        timestamp last_used_at
        boolean is_active
    }
    
    USER_NOTIFICATION_SETTINGS {
        uuid user_id PK
        boolean enabled
        boolean sound_enabled
        boolean vibration_enabled
        boolean show_preview
        boolean dnd_enabled
        time dnd_start_time
        time dnd_end_time
        int_array dnd_days
        timestamp created_at
        timestamp updated_at
    }
    
    CHANNEL_NOTIFICATION_SETTINGS {
        uuid id PK
        uuid channel_id FK
        uuid user_id FK
        boolean enabled
        boolean sound_enabled
        string sound_name
        boolean vibration_enabled
        int_array vibration_pattern
        timestamp muted_until
        timestamp created_at
        timestamp updated_at
    }
    
    MESSAGE_READS {
        uuid id PK
        uuid message_id FK
        uuid user_id FK
        timestamp read_at
    }
    
    KEY_ROTATION_HISTORY {
        uuid id PK
        uuid user_id FK
        enum key_type
        timestamp rotated_at
        text reason
    }
    
    SECURITY_AUDIT_LOG {
        uuid id PK
        uuid user_id FK
        enum event_type
        text event_details
        string ip_address
        timestamp created_at
    }
```

---

## 9. 전체 시스템 데이터 플로우

```mermaid
graph TB
    subgraph "클라이언트"
        Client[React Native Client]
    end
    
    subgraph "API 레이어"
        REST[REST API<br/>HTTP/HTTPS]
        WebSocket[WebSocket<br/>STOMP over SockJS]
    end
    
    subgraph "애플리케이션 레이어"
        Controllers[Controllers<br/>비즈니스 로직 제어]
        Services[Services<br/>핵심 비즈니스 로직]
    end
    
    subgraph "데이터 레이어"
        Repositories[Repositories<br/>데이터 접근]
        PostgreSQL[(PostgreSQL<br/>관계형 데이터)]
        MongoDB[(MongoDB<br/>메시지 데이터)]
        Redis[(Redis<br/>캐시/상태)]
    end
    
    subgraph "외부 서비스"
        Firebase[Firebase<br/>FCM 푸시]
        Storage[S3/Storage<br/>파일 저장]
    end
    
    Client -->|HTTP| REST
    Client -->|WebSocket| WebSocket
    REST --> Controllers
    WebSocket --> Controllers
    Controllers --> Services
    Services --> Repositories
    Repositories --> PostgreSQL
    Repositories --> MongoDB
    Services --> Redis
    Services --> Firebase
    Services --> Storage
    
    style Client fill:#e1f5ff
    style Controllers fill:#e3f2fd
    style Services fill:#fff3e0
    style PostgreSQL fill:#336791
    style MongoDB fill:#47A248
    style Redis fill:#DC382D
    style Firebase fill:#FFA000
```

---

## 10. 주요 컴포넌트 역할 요약

### 10.1 컨트롤러 (13개)

| 컨트롤러 | 역할 | 주요 기능 |
|---------|------|----------|
| `HealthController` | 시스템 상태 체크 | FCM, DB 연결 상태 확인 |
| `AuthController` | 인증 관리 | 회원가입, 로그인, 토큰 갱신 |
| `ProfileController` | 프로필 관리 | 프로필 이미지 업로드, 정보 조회/수정 |
| `ChannelController` | 채널 관리 | 채널 생성, 조회, 나가기, 멤버 추가 |
| `MessageController` | 메시지 관리 | 메시지 히스토리 조회 |
| `ChatController` | 실시간 채팅 | WebSocket 메시지 전송/수신 |
| `RealtimeWebSocketController` | 실시간 기능 | 타이핑, 읽음 표시, 온라인 상태 |
| `NotificationController` | 알림 설정 | FCM 토큰 등록, 알림 설정 관리 |
| `OnlineStatusController` | 온라인 상태 | 사용자 온라인 상태 조회 |
| `KeyExchangeController` | 키 교환 | Signal Protocol 키 생성/교환 |
| `AdminDashboardController` | 관리자 대시보드 | 시스템 통계 조회 |
| `AdminKeyRotationController` | 키 관리 | 관리자 키 교체 작업 |
| `SecurityAuditController` | 보안 감사 | 보안 이벤트 로그 조회 |

### 10.2 서비스 (15개)

| 서비스 | 역할 | 주요 기능 |
|--------|------|----------|
| `AuthService` | 인증 로직 | 사용자 인증, JWT 토큰 관리 |
| `UserService` | 사용자 관리 | 사용자 정보 CRUD |
| `ProfileImageService` | 프로필 이미지 | 이미지 업로드, 최적화, 다중 해상도 생성 |
| `ImageOptimizationService` | 이미지 최적화 | 리사이징, WebP 변환, BlurHash 생성 |
| `ChannelService` | 채널 비즈니스 로직 | 채널 생성/관리, 멤버 관리 |
| `MessageService` | 메시지 처리 | 메시지 저장, 암호화, 전송 |
| `EncryptionService` | 암호화 유틸 | 암호화 헬퍼 함수 |
| `SignalProtocolService` | Signal Protocol | E2E 암호화, 키 관리, 세션 관리 |
| `NotificationService` | 알림 처리 | 알림 설정 확인, 푸시 전송 로직 |
| `FcmDeliveryService` | FCM 전송 | Firebase 푸시 알림 전송 |
| `OnlineStatusService` | 온라인 상태 | 사용자 온라인/오프라인 상태 관리 |
| `TypingIndicatorService` | 타이핑 표시 | 타이핑 상태 전송/수신 |
| `ReadReceiptService` | 읽음 표시 | 읽음 상태 관리 |
| `MatchService` | 매칭 관리 | 매칭 생성/조회, 만료 처리 |
| `SecurityAuditLogger` | 보안 감사 | 보안 이벤트 로깅 |

### 10.3 리포지토리 (17개)

| 리포지토리 | 역할 | 데이터베이스 |
|-----------|------|------------|
| `UserRepository` | 사용자 데이터 | PostgreSQL |
| `ChannelRepository` | 채널 데이터 | PostgreSQL |
| `ChannelMembersRepository` | 채널 멤버 데이터 | PostgreSQL |
| `MessageRepository` | 메시지 데이터 | MongoDB |
| `MessageReadsRepository` | 읽음 표시 데이터 | PostgreSQL |
| `MatchRepository` | 매칭 데이터 | PostgreSQL |
| `UserBlocksRepository` | 차단 데이터 | PostgreSQL |
| `ReportRepository` | 신고 데이터 | PostgreSQL |
| `FcmTokenRepository` | FCM 토큰 데이터 | PostgreSQL |
| `UserNotificationSettingsRepository` | 사용자 알림 설정 | PostgreSQL |
| `ChannelNotificationSettingsRepository` | 채널 알림 설정 | PostgreSQL |
| `SignalIdentityRepository` | Signal Identity Key | PostgreSQL |
| `SignalPreKeyRepository` | Signal Pre Keys | PostgreSQL |
| `SignalSignedPreKeyRepository` | Signal Signed Pre Key | PostgreSQL |
| `SignalSessionRepository` | Signal 세션 | PostgreSQL |
| `UserKeysRepository` | 사용자 키 쌍 | PostgreSQL |
| `KeyRotationHistoryRepository` | 키 교체 이력 | PostgreSQL |
| `SecurityAuditLogRepository` | 보안 감사 로그 | PostgreSQL |

### 10.4 설정 클래스 (6개)

| 설정 클래스 | 역할 |
|------------|------|
| `SecurityConfig` | Spring Security 설정 (JWT, Rate Limit) |
| `WebSocketConfig` | WebSocket/STOMP 설정 |
| `WebMvcConfig` | Spring MVC 설정 |
| `FirebaseConfig` | Firebase 초기화 |
| `RedisConfig` | Redis 연결 설정 |
| `OpenApiConfig` | Swagger/OpenAPI 문서 설정 |

### 10.5 보안 컴포넌트

| 컴포넌트 | 역할 |
|---------|------|
| `JwtAuthenticationFilter` | JWT 토큰 검증 필터 |
| `RateLimitFilter` | API 요청 제한 필터 |
| `RateLimitService` | Rate Limit 로직 (Bucket4j) |
| `TokenBlacklistService` | 로그아웃된 토큰 관리 |
| `ConnectionInterceptor` | WebSocket 연결 인증 |
| `SecurityAuditLogger` | 보안 이벤트 로깅 |

### 10.6 WebSocket 컴포넌트

| 컴포넌트 | 역할 |
|---------|------|
| `WebSocketConfig` | WebSocket 설정 |
| `ConnectionInterceptor` | 연결 시 인증 처리 |
| `StompChannelInterceptor` | STOMP 메시지 검증 |
| `WebSocketEventListener` | 연결/해제 이벤트 처리 |
| `WebSocketMessageHandler` | 메시지 라우팅 |
| `WebSocketConnectionMonitor` | 연결 상태 모니터링 |

### 10.7 스케줄러

| 스케줄러 | 역할 | 실행 주기 |
|---------|------|----------|
| `KeyRotationScheduler` | Signal Protocol 키 자동 교체 | 주간 (Signed Pre Key), 지속적 (Pre Key) |

---

## 참고 사항

- 모든 다이어그램은 Mermaid 형식으로 작성되었습니다.
- GitHub, GitLab, Notion 등에서 바로 렌더링됩니다.
- 온라인 에디터: [https://mermaid.live/](https://mermaid.live/)

---

*최종 업데이트: 2025-01-26*

