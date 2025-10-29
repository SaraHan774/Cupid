package com.august.cupid.util

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 키 암호화 유틸리티
 *
 * Signal Protocol 개인키를 안전하게 암호화/복호화하는 유틸리티
 *
 * 암호화 방식:
 * - Key Derivation: PBKDF2-SHA256 (Argon2id가 이상적이지만 Java 기본 라이브러리 사용)
 * - Encryption: AES-256-GCM
 * - Authentication: GCM 태그 (128-bit)
 *
 * 보안 고려사항:
 * - Salt는 무작위 생성 (16 bytes)
 * - IV는 무작위 생성 (12 bytes for GCM)
 * - Iteration count: 100,000+ (OWASP 권장)
 * - 타이밍 공격 방지를 위한 constant-time 비교
 */
@Component
class KeyEncryptionUtil {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        // 암호화 파라미터
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256 // bits
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val GCM_IV_LENGTH = 12 // bytes (96 bits, NIST 권장)
        private const val SALT_LENGTH = 16 // bytes
        private const val PBKDF2_ITERATIONS = 100_000 // OWASP 2023 권장

        // 키 유도 알고리즘
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    /**
     * 개인키 암호화
     *
     * @param privateKeyBytes 암호화할 개인키 바이트 배열
     * @param password 사용자 비밀번호 (키 유도에 사용)
     * @return Base64 인코딩된 암호화된 데이터 (salt || iv || ciphertext || tag)
     */
    fun encryptPrivateKey(privateKeyBytes: ByteArray, password: String): String {
        return try {
            // 1. Salt 생성 (무작위)
            val salt = ByteArray(SALT_LENGTH)
            secureRandom.nextBytes(salt)

            // 2. 비밀번호로부터 키 유도 (PBKDF2)
            val secretKey = deriveKey(password, salt)

            // 3. IV 생성 (GCM용 무작위 nonce)
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)

            // 4. AES-GCM 암호화
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val ciphertext = cipher.doFinal(privateKeyBytes)

            // 5. 결과 포맷: salt (16) || iv (12) || ciphertext + tag
            val result = ByteBuffer.allocate(salt.size + iv.size + ciphertext.size)
            result.put(salt)
            result.put(iv)
            result.put(ciphertext)

            // 6. Base64 인코딩
            Base64.getEncoder().encodeToString(result.array())
        } catch (e: Exception) {
            logger.error("개인키 암호화 실패: ${e.message}", e)
            throw RuntimeException("개인키 암호화 중 오류가 발생했습니다", e)
        }
    }

    /**
     * 개인키 복호화
     *
     * @param encryptedDataBase64 Base64 인코딩된 암호화 데이터
     * @param password 사용자 비밀번호
     * @return 복호화된 개인키 바이트 배열
     */
    fun decryptPrivateKey(encryptedDataBase64: String, password: String): ByteArray {
        return try {
            // 1. Base64 디코딩
            val encryptedData = Base64.getDecoder().decode(encryptedDataBase64)
            val buffer = ByteBuffer.wrap(encryptedData)

            // 2. Salt 추출
            val salt = ByteArray(SALT_LENGTH)
            buffer.get(salt)

            // 3. IV 추출
            val iv = ByteArray(GCM_IV_LENGTH)
            buffer.get(iv)

            // 4. Ciphertext + Tag 추출
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            // 5. 키 유도
            val secretKey = deriveKey(password, salt)

            // 6. AES-GCM 복호화
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            logger.error("개인키 복호화 실패: ${e.message}")
            throw RuntimeException("개인키 복호화 중 오류가 발생했습니다. 비밀번호가 올바른지 확인하세요.", e)
        }
    }

    /**
     * PBKDF2를 사용한 키 유도
     *
     * @param password 사용자 비밀번호
     * @param salt 솔트 (16 bytes)
     * @return SecretKeySpec (AES-256)
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * 비밀번호 강도 검증
     *
     * 최소 요구사항:
     * - 길이: 12자 이상
     * - 대문자 포함
     * - 소문자 포함
     * - 숫자 포함
     * - 특수문자 포함
     *
     * @param password 검증할 비밀번호
     * @return 검증 결과
     */
    fun validatePasswordStrength(password: String): PasswordValidationResult {
        val errors = mutableListOf<String>()

        if (password.length < 12) {
            errors.add("비밀번호는 최소 12자 이상이어야 합니다")
        }

        if (!password.any { it.isUpperCase() }) {
            errors.add("대문자를 최소 1개 포함해야 합니다")
        }

        if (!password.any { it.isLowerCase() }) {
            errors.add("소문자를 최소 1개 포함해야 합니다")
        }

        if (!password.any { it.isDigit() }) {
            errors.add("숫자를 최소 1개 포함해야 합니다")
        }

        if (!password.any { !it.isLetterOrDigit() }) {
            errors.add("특수문자를 최소 1개 포함해야 합니다")
        }

        return PasswordValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Constant-time 문자열 비교 (타이밍 공격 방지)
     *
     * @param a 비교할 문자열 1
     * @param b 비교할 문자열 2
     * @return 두 문자열이 동일하면 true
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }

        return result == 0
    }

    /**
     * 바이트 배열 Constant-time 비교
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) {
            return false
        }

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }

        return result == 0
    }

    /**
     * 무작위 솔트 생성
     *
     * @param length 바이트 길이
     * @return 무작위 바이트 배열
     */
    fun generateRandomSalt(length: Int = SALT_LENGTH): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * 무작위 IV 생성 (GCM용)
     *
     * @return 12 바이트 무작위 IV
     */
    fun generateRandomIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }
}

/**
 * 비밀번호 검증 결과
 */
data class PasswordValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)
