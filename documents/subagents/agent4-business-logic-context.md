# Agent 4: Business Logic & Dating Features Expert 💝

**역할**: 소개팅 앱 비즈니스 로직 전문가  
**담당 작업**: Task 4 - 매칭 해제 시 채팅방 처리, Task 5 - 채널 삭제 모드, Task 7 - 그룹 채팅 인원 제한

---

## 📋 프로젝트 개요

**프로젝트명**: Cupid - 소개팅 앱 채팅 SDK  
**기술 스택**: Kotlin + Spring Boot 3.5.7  
**데이터베이스**: PostgreSQL  
**비즈니스 컨텍스트**: 소개팅 앱 (24시간 매칭 만료)  
**현재 단계**: Phase 1 MVP 완성을 위한 기능 보완

---

## ✅ 현재 구현 상태

### 완료된 기능
- ✅ `Match` 엔티티 존재 (status: ACTIVE, EXPIRED, CANCELLED 등)
- ✅ `Channel.match` 관계 존재
- ✅ `ChannelService.leaveChannel` 기본 구현 존재
- ✅ 채널 생성 기능 존재 (`targetUserIds`로 멤버 초대 가능)

### 미구현 기능
- ❌ 매칭 해제 시 채팅방 처리 로직 미구현
- ❌ Config 기반 삭제 모드 미구현
- ❌ 1:1 채널 전체 삭제 모드 미구현
- ❌ 그룹 채팅 최대 인원 제한 로직 미구현
- ❌ 스케줄러 기반 매칭 만료 체크 미구현

---

## 🔑 핵심 엔티티 및 구조

### Match Entity
```kotlin
package com.august.cupid.model.entity

@Entity
@Table(name = "matches")
data class Match(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    val user1: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    val user2: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: MatchStatus = MatchStatus.ACTIVE,

    @Column(name = "matched_at", nullable = false)
    val matchedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null
)

enum class MatchStatus {
    ACTIVE,    // 활성
    ACCEPTED,  // 수락됨
    REJECTED,  // 거부됨
    ENDED,     // 종료됨
    EXPIRED,   // 만료
    CANCELLED  // 취소
}
```

### Channel Entity
```kotlin
@Entity
@Table(name = "channels")
data class Channel(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: ChannelType,

    @Column(name = "name", length = 255)
    val name: String?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    val creator: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    val match: Match?,

    @Version
    @Column(name = "version")
    val version: Long? = null,  // 낙관적 락

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null
)

enum class ChannelType {
    DIRECT,  // 1:1 채팅
    GROUP    // 그룹 채팅
}
```

### ChannelMembers Entity
```kotlin
@Entity
@Table(
    name = "channel_members",
    uniqueConstraints = [
        UniqueConstraint(name = "unique_channel_user", columnNames = ["channel_id", "user_id"])
    ]
)
data class ChannelMembers(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    val channel: Channel,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "last_read_at")
    val lastReadAt: LocalDateTime? = null,

    @Version
    @Column(name = "version")
    val version: Long? = null
)
```

---

## 📦 의존성 (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 스케줄러는 Spring Boot 기본 포함
}
```

---

## 🗄️ 데이터베이스 스키마

### matches 테이블
```sql
CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id UUID NOT NULL REFERENCES users(id),
    user2_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    metadata JSONB,
    
    INDEX idx_matches_expires_at (expires_at),
    INDEX idx_matches_status (status)
);
```

### channels 테이블
```sql
CREATE TABLE channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL,
    name VARCHAR(255),
    creator_id UUID NOT NULL REFERENCES users(id),
    match_id UUID REFERENCES matches(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT,
    metadata JSONB,
    
    INDEX idx_channels_match (match_id),
    FOREIGN KEY (match_id) REFERENCES matches(id)
);
```

---

## 🎯 구현해야 할 작업

### Task 4: 매칭 해제 시 채팅방 처리 (2-3시간) ⭐ 비즈니스 로직 필수

**요구사항**:
- [ ] `MatchExpirationService` 생성
- [ ] Config 모드에 따른 채널 처리:
  - **Mode 1: DELETE** - 채널 및 멤버 완전 삭제
  - **Mode 2: READ_ONLY** - 채널을 읽기 전용으로 전환 (`is_active = false`)
  - **Mode 3: ARCHIVE** - 일정 기간 후 자동 삭제 (선택사항)
- [ ] 매칭 만료 체크 스케줄러 (주기적 실행, 예: 5분마다)
- [ ] WebSocket으로 사용자에게 알림

**구현 예시**:
```kotlin
@Service
class MatchExpirationService(
    private val matchRepository: MatchRepository,
    private val channelService: ChannelService,
    private val matchReleaseMode: MatchReleaseMode = MatchReleaseMode.READ_ONLY
) {
    @Scheduled(fixedRate = 300000) // 5분마다
    fun checkExpiredMatches() {
        val expiredMatches = matchRepository.findExpiredMatches(LocalDateTime.now())
        
        expiredMatches.forEach { match ->
            when (matchReleaseMode) {
                MatchReleaseMode.DELETE -> {
                    // 채널 및 메시지 완전 삭제
                    channelService.deleteChannelByMatch(match.id)
                }
                MatchReleaseMode.READ_ONLY -> {
                    // 읽기 전용으로 전환
                    channelService.setChannelReadOnlyByMatch(match.id)
                }
                MatchReleaseMode.ARCHIVE -> {
                    // 아카이브 처리
                    // ...
                }
            }
            
            // 매칭 상태 업데이트
            match.status = MatchStatus.EXPIRED
            matchRepository.save(match)
        }
    }
}

enum class MatchReleaseMode {
    DELETE,    // 완전 삭제
    READ_ONLY, // 읽기 전용
    ARCHIVE    // 아카이브
}
```

### Task 5: 채널 삭제 모드 구현 (1-2시간)

**요구사항**:
- [ ] `ChannelDeleteMode` Enum 생성 (INDIVIDUAL, DELETE_ALL)
- [ ] `ChannelService.leaveChannel` 로직 확장
- [ ] DIRECT 채널에서 DELETE_ALL 모드인 경우 상대방도 채널에서 제거
- [ ] Config 설정 추가 (application.yml 또는 환경변수)

**구현 예시**:
```kotlin
enum class ChannelDeleteMode {
    INDIVIDUAL,  // 개별 삭제 (멤버십만 비활성화)
    DELETE_ALL   // 전체 삭제 (채널 및 모든 멤버십 삭제)
}

@Service
class ChannelService(
    private val channelDeleteMode: ChannelDeleteMode = ChannelDeleteMode.INDIVIDUAL
) {
    fun leaveChannel(channelId: UUID, userId: UUID) {
        val channel = channelRepository.findById(channelId).orElseThrow()
        
        when (channelDeleteMode) {
            ChannelDeleteMode.INDIVIDUAL -> {
                // 멤버십만 비활성화
                val member = channelMembersRepository.findByChannelIdAndUserId(channelId, userId)
                member.isActive = false
                channelMembersRepository.save(member)
            }
            ChannelDeleteMode.DELETE_ALL -> {
                if (channel.type == ChannelType.DIRECT) {
                    // DIRECT 채널: 모든 멤버 제거 후 채널 삭제
                    channelMembersRepository.deleteAllByChannelId(channelId)
                    channelRepository.delete(channel)
                } else {
                    // GROUP 채널: 개별 삭제만
                    // ...
                }
            }
        }
    }
}
```

### Task 7: 그룹 채팅 최대 인원 제한 (1시간)

**요구사항**:
- [ ] `ChannelService.createChannel`에서 그룹 채널 인원 체크
- [ ] 기본값: 3명 (Config 설정 가능)
- [ ] 최대 인원 초과 시 에러 반환

**구현 예시**:
```kotlin
@Service
class ChannelService(
    @Value("\${chat.max-group-size:3}") 
    private val maxGroupSize: Int = 3
) {
    fun createChannel(request: CreateChannelRequest, creatorId: UUID): Channel {
        if (request.type == ChannelType.GROUP) {
            val totalMembers = (request.targetUserIds?.size ?: 0) + 1 // 생성자 포함
            require(totalMembers <= maxGroupSize) {
                "그룹 채팅은 최대 ${maxGroupSize}명까지 가능합니다"
            }
        }
        
        // 채널 생성 로직...
    }
}
```

---

## 📝 기존 코드 패턴

### Service 패턴
```kotlin
@Service
class ChannelService(
    private val channelRepository: ChannelRepository,
    private val channelMembersRepository: ChannelMembersRepository
) {
    fun createChannel(request: CreateChannelRequest, creatorId: UUID): Channel {
        // 비즈니스 로직
        val channel = Channel(...)
        return channelRepository.save(channel)
    }
    
    fun leaveChannel(channelId: UUID, userId: UUID) {
        // 기존 구현...
    }
}
```

### Scheduled Task 패턴
```kotlin
@Component
class ScheduledTasks {
    @Scheduled(fixedRate = 300000) // 5분마다
    fun performTask() {
        // 작업 수행
    }
    
    @Scheduled(cron = "0 0 * * * *") // 매 시간
    fun performHourlyTask() {
        // 작업 수행
    }
}
```

---

## 🔧 설정 파일 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/chatsdk
    username: postgres
    password: postgres

# 채팅 설정 (추가 필요)
chat:
  max-group-size: 3  # 그룹 채팅 최대 인원
  match-release-mode: READ_ONLY  # DELETE, READ_ONLY, ARCHIVE
  channel-delete-mode: INDIVIDUAL  # INDIVIDUAL, DELETE_ALL
```

---

## 📚 참고 문서

1. **스펙 문서**: `documents/specifications/chat-sdk-spec.md` 섹션 1.2, 2.1
2. **데이터베이스 스키마**: `documents/specifications/database-schema.md` 시나리오 10
3. **작업 목록**: `documents/tasks/today-tasks.md` - Task 4, 5, 7

---

## 💡 구현 가이드

### 매칭 만료 처리 구현 순서
1. `MatchExpirationService` 생성
2. Config 모드 Enum 생성 (`MatchReleaseMode`)
3. 스케줄러 메서드 구현 (`@Scheduled`)
4. 만료된 매칭 조회 로직
5. 모드별 채널 처리 로직
6. WebSocket 알림 통합
7. 테스트 케이스 작성

### 채널 삭제 모드 구현 순서
1. `ChannelDeleteMode` Enum 생성
2. Config에 설정 추가
3. `ChannelService.leaveChannel` 로직 확장
4. DIRECT vs GROUP 채널 처리 분기
5. 테스트 케이스 작성

### 그룹 채팅 인원 제한 구현 순서
1. Config에 `maxGroupSize` 설정 추가
2. `createChannel` 메서드에 검증 로직 추가
3. 에러 메시지 정의
4. 테스트 케이스 작성

---

## 🎯 MEGA PROMPT (시작 시 사용)

```
You are implementing match expiration handling for a dating app chat system.

Context:
- Dating app where matches expire after 24 hours
- Existing: Match entity with status and expires_at fields
- Need: Automated handling when matches expire

Requirements:
1. MatchExpirationService with configurable modes:
   - DELETE: Complete removal of channel and messages
   - READ_ONLY: Convert to read-only (no new messages)
   - ARCHIVE: Move to archive after period
2. Scheduled job to check expired matches
3. Config-based mode selection
4. Cleanup of associated data

Please provide:
1. MatchExpirationService.kt with different handling modes
2. ScheduledTask for periodic checking (@Scheduled)
3. Configuration enum and properties
4. Database operations for each mode
5. WebSocket notifications to users
6. Soft delete vs hard delete strategy
7. Test cases for each mode

Consider:
- Running every 5 minutes vs hourly?
- Batch processing for performance
- What if users are mid-conversation?
- Timezone considerations
```

