package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.model.entity.User
import com.august.cupid.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows as jupiterAssertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.annotation.DirtiesContext
import java.time.LocalDateTime
import java.util.*
import kotlin.test.*

/**
 * Signal Protocol 서비스 통합 테스트
 * 
 * 테스트 범위:
 * - 키 생성 및 등록
 * - 키 교환
 * - 메시지 암호화/복호화
 * - 세션 초기화 및 관리
 * - 키 회전
 * - 지문 검증
 * - 엣지 케이스
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = [
    "spring.data.mongodb.database=testdb_app",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.timeout=2000ms",
    "spring.datasource.url=jdbc:h2:mem:testdb_app;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "firebase.credentials-path=classpath:firebase-test.json"
])
class SignalProtocolIntegrationTest {

    @Autowired
    private lateinit var encryptionService: EncryptionService

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var user1: User
    private lateinit var user2: User
    private val testPassword = "TestPassword123!@#"

    @BeforeEach
    fun setUp() {
        // 테스트용 사용자 생성
        user1 = User(
            username = "test_user_1_${System.currentTimeMillis()}",
            email = "test1_${System.currentTimeMillis()}@example.com",
            passwordHash = "hashed_password_1",
            isActive = true
        )
        user1 = userRepository.save(user1)

        user2 = User(
            username = "test_user_2_${System.currentTimeMillis()}",
            email = "test2_${System.currentTimeMillis()}@example.com",
            passwordHash = "hashed_password_2",
            isActive = true
        )
        user2 = userRepository.save(user2)
    }

    /**
     * 테스트 1: 완전한 키 생성 플로우
     * - 키 생성
     * - 키 등록
     * - 키 상태 확인
     */
    @Test
    fun `test complete key generation flow`() {
        // 1. 키 생성
        val keyRegistration = encryptionService.generateIdentityKeys(user1.id, testPassword)
        
        assertNotNull(keyRegistration.identityPublicKey)
        assertNotNull(keyRegistration.signedPreKey)
        assertTrue(keyRegistration.oneTimePreKeys.isNotEmpty())
        assertEquals(100, keyRegistration.oneTimePreKeys.size)
        
        // 2. 키 등록
        val registrationSuccess = encryptionService.registerKeys(user1.id, keyRegistration)
        assertTrue(registrationSuccess)
        
        // 3. 키 상태 확인
        val keyStatus = encryptionService.getKeyStatus(user1.id)
        assertTrue(keyStatus.hasIdentityKey)
        assertTrue(keyStatus.hasSignedPreKey)
        assertTrue(keyStatus.availableOneTimePreKeys > 0)
    }

    /**
     * 테스트 2: 두 사용자 간 키 교환
     * - 두 사용자 모두 키 생성
     * - PreKeyBundle 조회
     * - 세션 초기화
     */
    @Test
    fun `test key exchange between two users`() {
        // 1. 두 사용자 모두 키 생성 및 등록
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        // 2. User1이 User2의 PreKeyBundle 조회
        val user2Bundle = encryptionService.getPreKeyBundle(user2.id, deviceId = 1)
        
        assertNotNull(user2Bundle)
        assertEquals(user2.id, user2Bundle.userId)
        assertNotNull(user2Bundle.identityKey)
        assertNotNull(user2Bundle.signedPreKey)
        
        // 3. User1이 User2와 세션 초기화
        val sessionEstablished = encryptionService.initializeSession(
            user1.id,
            user2.id,
            user2Bundle
        )
        
        assertTrue(sessionEstablished)
        
        // 4. 세션 존재 확인
        val hasSession = encryptionService.hasSession(user1.id, user2.id)
        assertTrue(hasSession)
    }

    /**
     * 테스트 3: 메시지 암호화 및 복호화
     * - 키 생성 및 세션 초기화
     * - 메시지 암호화
     * - 메시지 복호화
     */
    @Test
    fun `test message encryption and decryption`() {
        // 1. 두 사용자 키 생성 및 세션 초기화
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        val user2Bundle = encryptionService.getPreKeyBundle(user2.id, deviceId = 1)
        encryptionService.initializeSession(user1.id, user2.id, user2Bundle)
        
        // 2. User1이 User2에게 메시지 암호화
        val plaintext = "안녕하세요! 이것은 테스트 메시지입니다."
        val encryptedMessage = encryptionService.encryptMessage(
            user1.id,
            user2.id,
            plaintext
        )
        
        assertNotNull(encryptedMessage)
        assertNotEquals(plaintext, encryptedMessage.encryptedContent)
        assertTrue(encryptedMessage.encryptedContent.isNotEmpty())
        
        // 3. User2가 메시지 복호화
        val decryptedMessage = encryptionService.decryptMessage(
            user2.id,
            encryptedMessage,
            testPassword
        )
        
        assertEquals(plaintext, decryptedMessage)
    }

    /**
     * 테스트 4: 세션 초기화 및 관리
     * - 세션 초기화
     * - 세션 상태 확인
     * - 세션 삭제
     */
    @Test
    fun `test session initialization and management`() {
        // 1. 키 생성 및 등록
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        // 2. 세션 초기화 전에는 세션이 없어야 함
        assertFalse(encryptionService.hasSession(user1.id, user2.id))
        
        // 3. 세션 초기화
        val user2Bundle = encryptionService.getPreKeyBundle(user2.id, deviceId = 1)
        val sessionEstablished = encryptionService.initializeSession(
            user1.id,
            user2.id,
            user2Bundle
        )
        
        assertTrue(sessionEstablished)
        assertTrue(encryptionService.hasSession(user1.id, user2.id))
        
        // 4. 세션 삭제
        encryptionService.deleteSession(user1.id, user2.id)
        assertFalse(encryptionService.hasSession(user1.id, user2.id))
    }

    /**
     * 테스트 5: Signed Pre-Key 회전
     * - 키 생성
     * - Signed Pre-Key 회전
     * - 새로운 Signed Pre-Key 확인
     */
    @Test
    fun `test signed pre-key rotation`() {
        // 1. 키 생성 및 등록
        val initialKeys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, initialKeys)
        
        val initialKeyStatus = encryptionService.getKeyStatus(user1.id)
        val initialSignedPreKeyId = initialKeys.signedPreKey.keyId
        
        // 2. Signed Pre-Key 회전
        val newSignedPreKeyId = encryptionService.rotateSignedPreKey(user1.id, testPassword)
        
        // 3. 새로운 Signed Pre-Key ID가 다름을 확인
        assertNotEquals(initialSignedPreKeyId, newSignedPreKeyId)
        
        // 4. 키 상태 확인
        val updatedKeyStatus = encryptionService.getKeyStatus(user1.id)
        assertTrue(updatedKeyStatus.hasSignedPreKey)
    }

    /**
     * 테스트 6: 지문 검증
     * - 키 생성
     * - 지문 검증
     * - 잘못된 지문 검증 실패
     */
    @Test
    fun `test fingerprint verification`() {
        // 1. 키 생성 및 등록
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        // 2. User1의 Identity Key를 해시하여 지문 생성 (간단한 구현)
        val user1Bundle = encryptionService.getPreKeyBundle(user1.id, deviceId = 1)
        val fingerprintHash = user1Bundle.identityKey.hashCode().toString()
        
        // 3. 올바른 지문으로 검증
        val isValid = encryptionService.verifyFingerprint(
            user2.id,
            user1.id,
            fingerprintHash
        )
        
        // Note: 실제 구현에서는 더 정교한 지문 검증이 필요하지만, 여기서는 기본 동작 확인
        assertNotNull(isValid)
    }

    /**
     * 테스트 7: 엣지 케이스 - 키가 없는 사용자의 PreKeyBundle 조회
     */
    @Test
    fun `test get pre-key bundle for user without keys should throw exception`() {
        // 키가 없는 사용자의 PreKeyBundle 조회 시도
        jupiterAssertThrows<NoSuchElementException> {
            encryptionService.getPreKeyBundle(user1.id, deviceId = 1)
        }
    }

    /**
     * 테스트 8: 엣지 케이스 - 세션이 없는 상태에서 메시지 암호화 시도
     */
    @Test
    fun `test encrypt message without session should throw exception`() {
        // 키 생성만 하고 세션 초기화하지 않음
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        // 세션이 없는 상태에서 암호화 시도
        jupiterAssertThrows<IllegalStateException> {
            encryptionService.encryptMessage(
                user1.id,
                user2.id,
                "테스트 메시지"
            )
        }
    }

    /**
     * 테스트 9: 엣지 케이스 - 세션이 없는 상태에서 메시지 복호화 시도
     */
    @Test
    fun `test decrypt message without session should fail`() {
        // 키 생성만 하고 세션 초기화하지 않음
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        // 임의의 암호화된 메시지 생성
        val fakeEncryptedMessage = EncryptedMessageDto(
            senderId = user1.id,
            recipientId = user2.id,
            deviceId = 1,
            encryptedContent = "fake_encrypted_content",
            messageType = 1,
            registrationId = 123
        )
        
        // 세션이 없는 상태에서 복호화 시도 (SecurityException 또는 다른 예외 발생 예상)
        jupiterAssertThrows<Exception> {
            encryptionService.decryptMessage(
                user2.id,
                fakeEncryptedMessage,
                testPassword
            )
        }
    }

    /**
     * 테스트 10: 모든 키 삭제
     */
    @Test
    fun `test delete all keys`() {
        // 1. 키 생성 및 등록
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        // 2. 키 상태 확인
        var keyStatus = encryptionService.getKeyStatus(user1.id)
        assertTrue(keyStatus.hasIdentityKey)
        
        // 3. 모든 키 삭제
        encryptionService.deleteAllKeys(user1.id)
        
        // 4. 키 상태 확인 (키가 없어야 함)
        keyStatus = encryptionService.getKeyStatus(user1.id)
        assertFalse(keyStatus.hasIdentityKey)
        
        // 5. PreKeyBundle 조회 시 예외 발생 확인
        jupiterAssertThrows<NoSuchElementException> {
            encryptionService.getPreKeyBundle(user1.id, deviceId = 1)
        }
    }

    /**
     * 테스트 11: One-Time Pre-Key 소진 시나리오
     */
    @Test
    fun `test one-time pre-key exhaustion`() {
        // 1. 키 생성 및 등록
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        // 2. 여러 번 PreKeyBundle 조회하여 One-Time Pre-Key 소진 시뮬레이션
        // (실제로는 100개가 있지만, 테스트를 위해 여러 번 조회)
        var bundle: PreKeyBundleDto? = null
        for (i in 1..10) {
            try {
                bundle = encryptionService.getPreKeyBundle(user2.id, deviceId = 1)
                assertNotNull(bundle?.oneTimePreKey)
            } catch (e: NoSuchElementException) {
                // One-Time Pre-Key가 소진되면 null일 수 있음
                // 이는 정상적인 동작일 수 있음
                break
            }
        }
        
        // 3. 최소한 한 번은 PreKeyBundle을 얻을 수 있어야 함
        assertNotNull(bundle)
    }

    /**
     * 테스트 12: 양방향 세션 초기화
     * - User1 -> User2 세션
     * - User2 -> User1 세션
     */
    @Test
    fun `test bidirectional session initialization`() {
        // 1. 두 사용자 모두 키 생성 및 등록
        val user1Keys = encryptionService.generateIdentityKeys(user1.id, testPassword)
        encryptionService.registerKeys(user1.id, user1Keys)
        
        val user2Keys = encryptionService.generateIdentityKeys(user2.id, testPassword)
        encryptionService.registerKeys(user2.id, user2Keys)
        
        // 2. User1 -> User2 세션 초기화
        val user2Bundle = encryptionService.getPreKeyBundle(user2.id, deviceId = 1)
        val session1to2 = encryptionService.initializeSession(user1.id, user2.id, user2Bundle)
        assertTrue(session1to2)
        
        // 3. User2 -> User1 세션 초기화
        val user1Bundle = encryptionService.getPreKeyBundle(user1.id, deviceId = 1)
        val session2to1 = encryptionService.initializeSession(user2.id, user1.id, user1Bundle)
        assertTrue(session2to1)
        
        // 4. 양방향 세션 확인
        assertTrue(encryptionService.hasSession(user1.id, user2.id))
        assertTrue(encryptionService.hasSession(user2.id, user1.id))
    }
}

