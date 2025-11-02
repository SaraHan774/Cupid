# 로그인 모듈 분리 마이그레이션 계획

## 📋 목표
현재 통합된 인증(Auth) 모듈을 Chat 모듈에서 분리하여 독립적인 모듈로 구성

---

## 🎯 분리 전략

### 전략 비교

| 구분 | Option A: 완전 분리 | Option B: 부분 분리 (추천) |
|------|-------------------|------------------------|
| 스타일 | Microservice | Modular Monolith |
| User 엔티티 접근 | 완전 차단, UUID만 사용 | 읽기 전용 허용 |
| 성능 | N+1 쿼리 위험 | JOIN 가능, 성능 유지 |
| 독립성 | 완전 독립 | 부분 의존 |
| 마이그레이션 난이도 | 높음 (전면 수정) | 중간 (점진적 가능) |
| 추천 시점 | MSA 전환 시 | 지금 단계 |

**선택: Option B (부분 분리)** - 성능과 실용성의 균형

---

## 📦 현재 구조 vs 목표 구조

### 현재 구조 (통합형)

```
com.august.cupid/
├── service/
│   ├── MessageService.kt
│   │   └── userRepository 직접 의존 ❌
│   ├── ChannelService.kt
│   │   └── userRepository 직접 의존 ❌
│   └── UserService.kt
│
└── model/entity/
    ├── User.kt
    ├── Message.kt
    │   └── @ManyToOne sender: User ❌
    └── Channel.kt
        └── @ManyToOne creator: User ❌
```

**문제점:**
- Chat 관련 서비스가 User를 직접 생성/수정 가능
- 엔티티 간 강한 결합
- 책임 경계가 모호

### 목표 구조 (분리형)

```
com.august.cupid/
├── auth/                          # 인증 모듈 (독립)
│   ├── service/
│   │   └── UserService.kt
│   │       ├── existsById()
│   │       ├── getUserInfo()
│   │       └── isUserActive()
│   ├── entity/
│   │   └── User.kt
│   └── repository/
│       └── UserRepository.kt
│
├── chat/                          # 채팅 모듈
│   ├── service/
│   │   ├── MessageService.kt
│   │   │   └── userService 의존 ✅
│   │   └── ChannelService.kt
│   │       └── userService 의존 ✅
│   ├── entity/
│   │   ├── Message.kt
│   │   │   └── senderId: UUID ✅
│   │   └── Channel.kt
│   │       └── creatorId: UUID ✅
│   └── repository/
│       └── MessageRepository.kt
```

**개선점:**
- Chat 모듈은 UserService만 의존 (Facade 패턴)
- User 생성/수정은 Auth 모듈만 가능
- UUID 참조로 느슨한 결합

---

## 🔄 코드 변경 사항

### 1. Auth 모듈 API 정의

#### UserService.kt (Auth 모듈)

**Before:**
```kotlin
package com.august.cupid.service

@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun createUser(request: RegisterRequest): User { ... }
    fun updateUser(userId: UUID, request: UpdateUserRequest): User { ... }
    fun deleteUser(userId: UUID) { ... }
    fun findById(userId: UUID): User? { ... }
}
```

**After:**
```kotlin
package com.august.cupid.auth.service

@Service
class UserService(
    private val userRepository: UserRepository
) {
    // === 인증 관련 (Auth 모듈 전용) ===
    fun createUser(request: RegisterRequest): User { ... }
    fun updateUser(userId: UUID, request: UpdateUserRequest): User { ... }
    fun deleteUser(userId: UUID) { ... }

    // === 외부 모듈용 Read-Only API ===

    /**
     * User 존재 여부 확인 (경량 쿼리)
     * Chat, Encryption 모듈에서 사용
     */
    fun existsById(userId: UUID): Boolean {
        return userRepository.existsById(userId)
    }

    /**
     * User 활성 상태 확인
     */
    fun isUserActive(userId: UUID): Boolean {
        return userRepository.findById(userId)
            .map { it.isActive }
            .orElse(false)
    }

    /**
     * User 정보 조회 (DTO 반환)
     * 엔티티 노출 방지
     */
    fun getUserInfo(userId: UUID): UserInfoDto? {
        val user = userRepository.findById(userId).orElse(null) ?: return null
        return UserInfoDto(
            id = user.id!!,
            username = user.username,
            email = user.email,
            profileImageUrl = user.profileImageUrl,
            isActive = user.isActive
        )
    }

    /**
     * 여러 User 정보 일괄 조회
     */
    fun getUserInfos(userIds: List<UUID>): Map<UUID, UserInfoDto> {
        return userRepository.findAllById(userIds)
            .associate { user ->
                user.id!! to UserInfoDto(
                    id = user.id!!,
                    username = user.username,
                    email = user.email,
                    profileImageUrl = user.profileImageUrl,
                    isActive = user.isActive
                )
            }
    }
}
```

#### UserInfoDto.kt (Auth 모듈)

```kotlin
package com.august.cupid.auth.dto

/**
 * 외부 모듈에 노출되는 User 정보
 * 민감한 정보 제외 (password, 내부 메타데이터 등)
 */
data class UserInfoDto(
    val id: UUID,
    val username: String,
    val email: String,
    val profileImageUrl: String?,
    val isActive: Boolean
) {
    companion object {
        fun from(user: User): UserInfoDto {
            return UserInfoDto(
                id = user.id!!,
                username = user.username,
                email = user.email,
                profileImageUrl = user.profileImageUrl,
                isActive = user.isActive
            )
        }
    }
}
```

---

### 2. Chat 모듈 엔티티 변경

#### Message.kt

**Before:**
```kotlin
package com.august.cupid.model.entity

@Entity
@Table(name = "messages")
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "channel_id", nullable = false)
    val channelId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,  // ❌ User 엔티티 직접 참조

    @Column(columnDefinition = "TEXT")
    val encryptedContent: String,

    @Enumerated(EnumType.STRING)
    val messageType: MessageType = MessageType.TEXT,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
```

**After:**
```kotlin
package com.august.cupid.chat.entity

@Entity
@Table(name = "messages")
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "channel_id", nullable = false)
    val channelId: UUID,

    @Column(name = "sender_id", nullable = false)
    val senderId: UUID,  // ✅ UUID만 참조

    @Column(columnDefinition = "TEXT")
    val encryptedContent: String,

    @Enumerated(EnumType.STRING)
    val messageType: MessageType = MessageType.TEXT,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
```

**DB 마이그레이션 불필요:**
- 컬럼 이름 동일 (`sender_id`)
- 데이터 타입 동일 (UUID)
- FK 제약조건은 유지 가능

#### Channel.kt

**Before:**
```kotlin
package com.august.cupid.model.entity

@Entity
@Table(name = "channels")
data class Channel(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    val type: ChannelType,

    val name: String?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    val creator: User,  // ❌ User 엔티티 직접 참조

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    val match: Match?,

    var createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

**After:**
```kotlin
package com.august.cupid.chat.entity

@Entity
@Table(name = "channels")
data class Channel(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    val type: ChannelType,

    val name: String?,

    @Column(name = "creator_id", nullable = false)
    val creatorId: UUID,  // ✅ UUID만 참조

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    val match: Match?,

    var createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

#### ChannelMembers.kt

**Before:**
```kotlin
@Entity
@Table(name = "channel_members")
data class ChannelMembers(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    val channel: Channel,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,  // ❌

    // ...
)
```

**After:**
```kotlin
@Entity
@Table(name = "channel_members")
data class ChannelMembers(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    val channel: Channel,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,  // ✅

    // ...
)
```

---

### 3. Chat 모듈 Service 변경

#### MessageService.kt

**Before:**
```kotlin
package com.august.cupid.service

@Service
@Transactional
class MessageService(
    private val messageRepository: MessageRepository,
    private val messageReadsRepository: MessageReadsRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userRepository: UserRepository,  // ❌ 직접 의존
    private val messagingTemplate: SimpMessagingTemplate
) {
    fun sendMessage(request: SendMessageRequest, senderId: UUID): ApiResponse<MessageResponse> {
        return try {
            if (request.channelId == null) {
                return ApiResponse(false, message = "채널 ID는 필수입니다")
            }

            // 발신자 존재 확인
            val sender = userRepository.findById(senderId).orElse(null)
            if (sender == null) {
                return ApiResponse(false, message = "발신자를 찾을 수 없습니다")
            }

            // 메시지 생성
            val message = Message(
                channelId = request.channelId,
                senderId = senderId,
                encryptedContent = request.encryptedContent,
                messageType = MessageType.valueOf(request.messageType.uppercase())
            )

            val savedMessage = messageRepository.save(message)

            // WebSocket 브로드캐스트
            messagingTemplate.convertAndSend(
                "/topic/channel/${savedMessage.channelId}",
                savedMessage.toResponse()
            )

            ApiResponse(true, data = savedMessage.toResponse())
        } catch (e: Exception) {
            logger.error("메시지 전송 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 전송 중 오류가 발생했습니다")
        }
    }
}
```

**After:**
```kotlin
package com.august.cupid.chat.service

import com.august.cupid.auth.service.UserService  // ✅ UserService 의존

@Service
@Transactional
class MessageService(
    private val messageRepository: MessageRepository,
    private val messageReadsRepository: MessageReadsRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userService: UserService,  // ✅ UserService로 변경
    private val messagingTemplate: SimpMessagingTemplate
) {
    fun sendMessage(request: SendMessageRequest, senderId: UUID): ApiResponse<MessageResponse> {
        return try {
            if (request.channelId == null) {
                return ApiResponse(false, message = "채널 ID는 필수입니다")
            }

            // 발신자 존재 확인 - UserService 사용
            if (!userService.existsById(senderId)) {
                return ApiResponse(false, message = "발신자를 찾을 수 없습니다")
            }

            // 활성 사용자 여부 확인
            if (!userService.isUserActive(senderId)) {
                return ApiResponse(false, message = "비활성화된 사용자입니다")
            }

            // 메시지 생성
            val message = Message(
                channelId = request.channelId,
                senderId = senderId,  // UUID만 저장
                encryptedContent = request.encryptedContent,
                messageType = MessageType.valueOf(request.messageType.uppercase())
            )

            val savedMessage = messageRepository.save(message)

            // WebSocket 브로드캐스트
            messagingTemplate.convertAndSend(
                "/topic/channel/${savedMessage.channelId}",
                savedMessage.toResponse()
            )

            ApiResponse(true, data = savedMessage.toResponse())
        } catch (e: Exception) {
            logger.error("메시지 전송 실패: ${e.message}", e)
            ApiResponse(false, error = "메시지 전송 중 오류가 발생했습니다")
        }
    }

    /**
     * 발신자 정보를 포함한 메시지 조회
     */
    fun getMessageWithSenderInfo(messageId: UUID): MessageWithSenderDto? {
        val message = messageRepository.findById(messageId).orElse(null) ?: return null

        // UserService를 통해 발신자 정보 조회
        val senderInfo = userService.getUserInfo(message.senderId)

        return MessageWithSenderDto(
            id = message.id!!,
            channelId = message.channelId,
            senderId = message.senderId,
            senderUsername = senderInfo?.username ?: "Unknown User",
            senderProfileImage = senderInfo?.profileImageUrl,
            encryptedContent = message.encryptedContent,
            messageType = message.messageType.name,
            createdAt = message.createdAt,
            updatedAt = message.updatedAt
        )
    }
}
```

#### ChannelService.kt

**Before:**
```kotlin
package com.august.cupid.service

@Service
@Transactional
class ChannelService(
    private val channelRepository: ChannelRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userRepository: UserRepository,  // ❌ 직접 의존
    private val matchRepository: MatchRepository,
    private val entityManager: EntityManager,
    private val messagingTemplate: SimpMessagingTemplate
) {
    fun createChannel(request: CreateChannelRequest, creatorId: UUID): ApiResponse<ChannelResponse> {
        return try {
            // 생성자 존재 확인
            val creator = userRepository.findById(creatorId).orElse(null)
            if (creator == null) {
                return ApiResponse(false, message = "생성자를 찾을 수 없습니다")
            }

            val channelType = ChannelType.valueOf(request.type.uppercase())

            // 채널 생성
            val channel = Channel(
                type = channelType,
                name = request.name,
                creator = creator,  // ❌ User 엔티티 전달
                match = match
            )

            val savedChannel = channelRepository.save(channel)

            // 생성자를 채널 멤버로 추가
            val channelMember = ChannelMembers(
                channel = savedChannel,
                user = creator,  // ❌ User 엔티티 전달
                role = ChannelRole.ADMIN,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )
            channelMembersRepository.save(channelMember)

            ApiResponse(true, data = savedChannel.toResponse())
        } catch (e: Exception) {
            logger.error("채널 생성 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 생성 중 오류가 발생했습니다")
        }
    }
}
```

**After:**
```kotlin
package com.august.cupid.chat.service

import com.august.cupid.auth.service.UserService  // ✅

@Service
@Transactional
class ChannelService(
    private val channelRepository: ChannelRepository,
    private val channelMembersRepository: ChannelMembersRepository,
    private val userService: UserService,  // ✅ UserService로 변경
    private val matchRepository: MatchRepository,
    private val entityManager: EntityManager,
    private val messagingTemplate: SimpMessagingTemplate
) {
    fun createChannel(request: CreateChannelRequest, creatorId: UUID): ApiResponse<ChannelResponse> {
        return try {
            // 생성자 존재 확인 - UserService 사용
            if (!userService.existsById(creatorId)) {
                return ApiResponse(false, message = "생성자를 찾을 수 없습니다")
            }

            if (!userService.isUserActive(creatorId)) {
                return ApiResponse(false, message = "비활성화된 사용자입니다")
            }

            val channelType = ChannelType.valueOf(request.type.uppercase())

            // 채널 생성
            val channel = Channel(
                type = channelType,
                name = request.name,
                creatorId = creatorId,  // ✅ UUID만 저장
                match = match
            )

            val savedChannel = channelRepository.save(channel)

            // 생성자를 채널 멤버로 추가
            val channelMember = ChannelMembers(
                channel = savedChannel,
                userId = creatorId,  // ✅ UUID만 저장
                role = ChannelRole.ADMIN,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )
            channelMembersRepository.save(channelMember)

            ApiResponse(true, data = savedChannel.toResponse())
        } catch (e: Exception) {
            logger.error("채널 생성 실패: ${e.message}", e)
            ApiResponse(false, error = "채널 생성 중 오류가 발생했습니다")
        }
    }

    /**
     * 채널 정보를 생성자 정보와 함께 조회
     */
    fun getChannelWithCreatorInfo(channelId: UUID): ChannelWithCreatorDto? {
        val channel = channelRepository.findById(channelId).orElse(null) ?: return null

        // UserService를 통해 생성자 정보 조회
        val creatorInfo = userService.getUserInfo(channel.creatorId)

        return ChannelWithCreatorDto(
            id = channel.id!!,
            name = channel.name,
            type = channel.type.name,
            creatorId = channel.creatorId,
            creatorUsername = creatorInfo?.username ?: "Unknown",
            createdAt = channel.createdAt
        )
    }
}
```

---

### 4. Repository 변경

#### MessageRepository.kt

**Before:**
```kotlin
package com.august.cupid.repository

interface MessageRepository : JpaRepository<Message, UUID> {
    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender
        WHERE m.channelId = :channelId
        AND m.status <> :status
        ORDER BY m.createdAt DESC
    """)
    fun findByChannelIdAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID,
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>
}
```

**After:**
```kotlin
package com.august.cupid.chat.repository

interface MessageRepository : JpaRepository<Message, UUID> {
    // JOIN FETCH 제거, senderId로 필터링
    fun findByChannelIdAndStatusNotOrderByCreatedAtDesc(
        channelId: UUID,
        status: MessageStatus,
        pageable: Pageable
    ): Page<Message>

    // 필요 시 발신자 정보는 Service Layer에서 UserService로 조회
}
```

---

### 5. DTO 변경

#### MessageWithSenderDto.kt (신규)

```kotlin
package com.august.cupid.chat.dto

/**
 * 발신자 정보가 포함된 메시지 DTO
 */
data class MessageWithSenderDto(
    val id: UUID,
    val channelId: UUID,
    val senderId: UUID,
    val senderUsername: String,
    val senderProfileImage: String?,
    val encryptedContent: String,
    val messageType: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
```

#### ChannelWithCreatorDto.kt (신규)

```kotlin
package com.august.cupid.chat.dto

/**
 * 생성자 정보가 포함된 채널 DTO
 */
data class ChannelWithCreatorDto(
    val id: UUID,
    val name: String?,
    val type: String,
    val creatorId: UUID,
    val creatorUsername: String,
    val createdAt: LocalDateTime
)
```

---

## 🗂️ 마이그레이션 단계

### Phase 1: Auth 모듈 API 정의 (1일)

#### Step 1.1: UserInfoDto 생성
- [ ] `auth/dto/UserInfoDto.kt` 생성
- [ ] 필요한 필드만 포함 (민감 정보 제외)
- [ ] `from(User)` 팩토리 메서드 추가

#### Step 1.2: UserService에 외부 API 추가
- [ ] `existsById(UUID)` 메서드 추가
- [ ] `isUserActive(UUID)` 메서드 추가
- [ ] `getUserInfo(UUID)` 메서드 추가 (DTO 반환)
- [ ] `getUserInfos(List<UUID>)` 메서드 추가 (일괄 조회)

#### Step 1.3: 테스트 작성
- [ ] UserService 외부 API 단위 테스트

**검증:**
```bash
./gradlew test --tests "UserServiceTest"
```

---

### Phase 2: Chat 엔티티 수정 (1일)

#### Step 2.1: Message 엔티티 수정
- [ ] `@ManyToOne sender: User` → `senderId: UUID` 변경
- [ ] `sender_id` 컬럼 이름 동일하게 유지 (마이그레이션 불필요)

#### Step 2.2: Channel 엔티티 수정
- [ ] `@ManyToOne creator: User` → `creatorId: UUID` 변경
- [ ] `creator_id` 컬럼 이름 유지

#### Step 2.3: ChannelMembers 엔티티 수정
- [ ] `@ManyToOne user: User` → `userId: UUID` 변경
- [ ] `user_id` 컬럼 이름 유지

#### Step 2.4: 컴파일 에러 확인
```bash
./gradlew compileKotlin
```

**예상 에러:**
- Service에서 `message.sender.username` 같은 코드 에러
- Repository에서 `JOIN FETCH` 에러

이 에러들은 다음 Phase에서 수정

---

### Phase 3: Chat Repository 수정 (0.5일)

#### Step 3.1: MessageRepository 수정
- [ ] `JOIN FETCH m.sender` 제거
- [ ] 단순 쿼리로 변경

#### Step 3.2: ChannelRepository 수정
- [ ] `JOIN FETCH c.creator` 제거
- [ ] 단순 쿼리로 변경

#### Step 3.3: ChannelMembersRepository 수정
- [ ] `JOIN FETCH cm.user` 제거
- [ ] 단순 쿼리로 변경

**검증:**
```bash
./gradlew compileKotlin
```

---

### Phase 4: Chat Service 수정 (2일)

#### Step 4.1: MessageService 수정
- [ ] `userRepository` → `userService` 의존성 변경
- [ ] `userRepository.findById()` → `userService.existsById()` 변경
- [ ] `MessageWithSenderDto` 반환 메서드 추가
- [ ] `toResponse()` 메서드 수정 (sender 정보 제거)

**수정 위치:**
- `sendMessage()` - 약 50줄
- `getMessageById()` - 약 158줄
- `editMessage()` - 약 181줄
- `deleteMessage()` - 약 224줄
- `markMessageAsRead()` - 약 256줄

#### Step 4.2: ChannelService 수정
- [ ] `userRepository` → `userService` 의존성 변경
- [ ] `createChannel()` 수정 - creator: User → creatorId: UUID
- [ ] `addUserToChannel()` 수정 - user: User → userId: UUID
- [ ] `ChannelWithCreatorDto` 반환 메서드 추가

**수정 위치:**
- `createChannel()` - 약 39줄
- `addUserToChannel()` - 약 152줄
- `removeUserFromChannel()` - 약 203줄

#### Step 4.3: 테스트 수정
- [ ] MessageServiceTest 수정
- [ ] ChannelServiceTest 수정
- [ ] Mock 설정 변경 (userRepository → userService)

**검증:**
```bash
./gradlew test --tests "*MessageServiceTest"
./gradlew test --tests "*ChannelServiceTest"
```

---

### Phase 5: Chat DTO 수정 (0.5일)

#### Step 5.1: MessageResponse 수정
- [ ] `sender: UserDto` 필드 제거 또는 Optional로 변경
- [ ] `senderId: UUID` 필드 추가

#### Step 5.2: 새 DTO 생성
- [ ] `MessageWithSenderDto.kt` 생성
- [ ] `ChannelWithCreatorDto.kt` 생성
- [ ] `ChannelWithMembersDto.kt` 생성

#### Step 5.3: Controller Response 변경
- [ ] 필요 시 `MessageWithSenderDto` 반환
- [ ] Frontend에서 sender 정보 필요한 경우만 사용

---

### Phase 6: Controller 수정 (1일)

#### Step 6.1: MessageController
- [ ] Response DTO 변경
- [ ] 필요 시 `getMessageWithSenderInfo()` 엔드포인트 추가

#### Step 6.2: ChannelController
- [ ] Response DTO 변경
- [ ] 필요 시 `getChannelWithCreatorInfo()` 엔드포인트 추가

#### Step 6.3: API 문서 업데이트
- [ ] Swagger/OpenAPI 스펙 확인
- [ ] API 변경사항 문서화

---

### Phase 7: Frontend (test-client) 수정 (1일)

#### Step 7.1: app.js 수정
- [ ] 메시지 표시 시 sender 정보 처리 변경
- [ ] 채널 표시 시 creator 정보 처리 변경

#### Step 7.2: 새 API 호출 추가
- [ ] 필요 시 `/messages/{id}/with-sender` 호출
- [ ] 필요 시 `/channels/{id}/with-creator` 호출

**변경 예시:**
```javascript
// Before
function displayMessage(message) {
    const senderName = message.sender.username;  // ❌
    // ...
}

// After
function displayMessage(message) {
    const senderName = message.senderUsername || 'Unknown';  // ✅
    // 또는 별도 API 호출
    // const senderInfo = await fetchUserInfo(message.senderId);
    // ...
}
```

---

### Phase 8: 통합 테스트 (1일)

#### Step 8.1: 전체 빌드
```bash
./gradlew clean build
```

#### Step 8.2: 수동 테스트 (test-client)
- [ ] 로그인/회원가입
- [ ] 채널 생성
- [ ] 메시지 전송
- [ ] 그룹 채팅
- [ ] 프로필 조회

#### Step 8.3: 성능 테스트
- [ ] N+1 쿼리 확인 (Hibernate 로그)
- [ ] 필요 시 배치 조회 추가

```yaml
# application.yml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

---

### Phase 9: Encryption 모듈 적용 (0.5일)

Encryption 모듈도 User를 참조하므로 동일하게 수정:

#### Step 9.1: SignalProtocolService 수정
- [ ] `userRepository` → `userService` 변경
- [ ] User 존재 확인만 수행

#### Step 9.2: KeyBackupService 수정
- [ ] User 검증 로직 UserService로 위임

---

### Phase 10: 문서화 및 배포 (0.5일)

#### Step 10.1: 문서 업데이트
- [ ] README.md 아키텍처 섹션 업데이트
- [ ] API 문서 업데이트
- [ ] 마이그레이션 가이드 작성

#### Step 10.2: Git 커밋 및 푸시
```bash
git add .
git commit -m "refactor: Auth 모듈 분리 (User 엔티티 간접 참조)"
git push origin master
```

---

## ⚠️ 주의사항

### 1. DB 마이그레이션 불필요

컬럼 이름이 동일하므로 **DB 스키마 변경 없음:**
- `sender_id` → `sender_id` (동일)
- `creator_id` → `creator_id` (동일)
- `user_id` → `user_id` (동일)

단지 JPA 매핑만 변경:
- `@ManyToOne` → `@Column`

### 2. N+1 쿼리 문제

**문제:**
```kotlin
// 메시지 100개 조회
val messages = messageRepository.findByChannelId(channelId)

// 각 메시지마다 UserService 호출
messages.forEach { message ->
    val senderInfo = userService.getUserInfo(message.senderId)  // N번 호출!
}
```

**해결책:**
```kotlin
// 일괄 조회
val messages = messageRepository.findByChannelId(channelId)
val senderIds = messages.map { it.senderId }.distinct()
val senderInfos = userService.getUserInfos(senderIds)  // 1번 호출

messages.map { message ->
    MessageWithSenderDto(
        // ...
        senderUsername = senderInfos[message.senderId]?.username ?: "Unknown"
    )
}
```

### 3. 트랜잭션 경계

Auth 모듈과 Chat 모듈이 다른 트랜잭션에 있을 수 있음:

```kotlin
@Transactional  // Chat 트랜잭션
fun sendMessage(...) {
    // UserService는 별도 트랜잭션일 수 있음
    userService.existsById(senderId)  // Auth 트랜잭션

    // 메시지 저장
    messageRepository.save(message)
}
```

**현재는 문제 없음** (같은 Spring Context)
**향후 MSA 전환 시 고려 필요**

### 4. 캐시 활용

자주 조회되는 User 정보는 캐시 사용 권장:

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository
) {
    @Cacheable("userInfo", key = "#userId")
    fun getUserInfo(userId: UUID): UserInfoDto? {
        // ...
    }
}
```

---

## 📊 예상 소요 시간

| Phase | 작업 내용 | 예상 시간 |
|-------|----------|----------|
| 1 | Auth 모듈 API 정의 | 1일 |
| 2 | Chat 엔티티 수정 | 1일 |
| 3 | Chat Repository 수정 | 0.5일 |
| 4 | Chat Service 수정 | 2일 |
| 5 | Chat DTO 수정 | 0.5일 |
| 6 | Controller 수정 | 1일 |
| 7 | Frontend 수정 | 1일 |
| 8 | 통합 테스트 | 1일 |
| 9 | Encryption 모듈 적용 | 0.5일 |
| 10 | 문서화 및 배포 | 0.5일 |
| **합계** | | **약 9일** |

---

## 🎯 마이그레이션 후 얻는 이점

### 1. 명확한 책임 분리
- Auth 모듈: User CRUD만 담당
- Chat 모듈: 채팅 기능만 담당

### 2. 테스트 용이성
```kotlin
// Before: User 엔티티 Mock 필요
@Test
fun `메시지 전송 테스트`() {
    val mockUser = mock<User>()
    whenever(userRepository.findById(any())).thenReturn(Optional.of(mockUser))
    // ...
}

// After: 간단한 boolean만 Mock
@Test
fun `메시지 전송 테스트`() {
    whenever(userService.existsById(any())).thenReturn(true)
    // ...
}
```

### 3. 독립적 배포 가능 (향후)
- Auth 모듈 변경 → Chat 모듈 영향 최소화
- Chat 모듈 변경 → Auth 모듈 영향 없음

### 4. 재사용성
Chat SDK를 다른 프로젝트에서 사용 시:
- UserService 인터페이스만 구현하면 됨
- User 엔티티 구조 무관

---

## 🚀 다음 단계 (마이그레이션 후)

### Option A: Gradle 멀티 모듈 전환

```
cupid-project/
├── cupid-auth/
│   └── build.gradle.kts
├── cupid-chat/
│   └── build.gradle.kts (dependency: cupid-auth)
├── cupid-encryption/
│   └── build.gradle.kts (dependency: cupid-auth)
└── cupid-app/
    └── build.gradle.kts (dependency: all)
```

### Option B: MSA 전환

```
Auth Service (Port 8081)
├── User 관리
└── JWT 발급

Chat Service (Port 8082)
├── 채팅 기능
└── Auth Service API 호출

Dating Service (Port 8083)
├── 매칭 로직
└── Auth/Chat Service 호출
```

---

## ✅ 시작 전 체크리스트

- [ ] 현재 코드 백업 완료
- [ ] Git 브랜치 생성: `feature/auth-module-separation`
- [ ] 팀원 공유 (해당되는 경우)
- [ ] 예상 일정 확보 (약 9일)
- [ ] DB 백업 완료 (만약을 위해)

---

## 📚 참고 자료

- [Martin Fowler - Modular Monolith](https://martinfowler.com/bliki/MonolithFirst.html)
- [DDD - Bounded Context](https://martinfowler.com/bliki/BoundedContext.html)
- [Spring Boot Multi-Module](https://spring.io/guides/gs/multi-module/)
- [JPA - Entity Relationships](https://docs.oracle.com/javaee/7/tutorial/persistence-intro.htm)

---

**준비되면 Phase 1부터 시작하세요!**
