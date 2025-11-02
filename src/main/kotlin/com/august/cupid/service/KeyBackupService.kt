package com.august.cupid.service

import com.august.cupid.model.dto.KeyBackupListItem
import com.august.cupid.model.dto.KeyBackupListResponse
import com.august.cupid.model.dto.KeyBackupRequest
import com.august.cupid.model.dto.KeyBackupResponse
import com.august.cupid.model.dto.KeyBackupRestoreRequest
import com.august.cupid.model.dto.KeyRegistrationRequest
import com.august.cupid.model.entity.KeyBackup
import com.august.cupid.model.entity.User
import com.august.cupid.repository.KeyBackupRepository
import com.august.cupid.repository.UserKeysRepository
import com.august.cupid.repository.UserRepository
import com.august.cupid.service.SecurityAuditLogger
import com.august.cupid.util.KeyEncryptionUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*

/**
 * 키 백업 서비스
 * 
 * Signal Protocol 키를 백업하고 복구하는 기능을 제공합니다.
 * 
 * SECURITY:
 * - 백업 데이터는 별도의 백업 비밀번호로 암호화 (사용자 비밀번호와 분리)
 * - AES-256-GCM 암호화 사용
 * - SHA-256 해시로 무결성 검증
 * - 백업 만료 시간 설정 가능 (기본 90일)
 * - 복구 시 백업 사용 표시
 */
@Service
@Transactional
class KeyBackupService(
    private val userRepository: UserRepository,
    private val userKeysRepository: UserKeysRepository,
    private val keyBackupRepository: KeyBackupRepository,
    private val keyEncryptionUtil: KeyEncryptionUtil,
    private val encryptionService: EncryptionService,
    private val securityAuditLogger: SecurityAuditLogger,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 키 백업 생성
     * 
     * @param userId 사용자 ID
     * @param request 백업 요청 (백업 비밀번호, 만료 기간 등)
     * @return 백업 응답
     * @throws IllegalArgumentException 사용자 키가 없거나 잘못된 입력
     * @throws SecurityException 백업 생성 중 보안 오류
     */
    fun createBackup(userId: UUID, request: KeyBackupRequest): KeyBackupResponse {
        val startTime = System.currentTimeMillis()
        return try {
            logger.info("키 백업 생성 시작: 사용자 $userId")

            // 1. 사용자 확인
            val user = userRepository.findById(userId).orElseThrow {
                IllegalArgumentException("사용자를 찾을 수 없습니다: $userId")
            }

            // 2. 사용자 키 확인
            val userKeys = userKeysRepository.findActiveKeysByUserId(userId, LocalDateTime.now())
                ?: throw IllegalArgumentException("사용자 키가 존재하지 않습니다. 먼저 키를 생성해주세요.")

            // 3. 백업 데이터 구성 (JSON 형식)
            val backupData = mapOf(
                "user_id" to userId.toString(),
                "identity_public_key" to userKeys.identityPublicKey,
                "identity_private_key_encrypted" to userKeys.identityPrivateKeyEncrypted,
                "device_id" to userKeys.deviceId,
                "registration_id" to userKeys.registrationId,
                "signed_pre_key" to userKeys.signedPreKey,
                "pre_key_signature" to userKeys.preKeySignature,
                "created_at" to userKeys.createdAt.toString(),
                "backup_version" to "1.0"
            )

            val backupDataJson = objectMapper.writeValueAsString(backupData)

            // 4. 백업 데이터 해시 생성 (무결성 검증용)
            val backupHash = calculateHash(backupDataJson)

            // 5. 백업 데이터 암호화 (백업 비밀번호로)
            val encryptedBackupData = keyEncryptionUtil.encryptPrivateKey(
                backupDataJson.toByteArray(Charsets.UTF_8),
                request.backupPassword
            )

            // 6. 만료 시간 계산
            val expirationDays = request.expirationDays ?: 90
            val expiresAt = LocalDateTime.now().plusDays(expirationDays.toLong())

            // 7. 백업 저장
            val backup = KeyBackup(
                user = user,
                encryptedBackupData = encryptedBackupData,
                backupHash = backupHash,
                expiresAt = expiresAt,
                metadata = request.metadata
            )
            val savedBackup = keyBackupRepository.save(backup)

            logger.info("키 백업 생성 완료: 백업 ID ${savedBackup.id}, 사용자 $userId")

            // 보안 감사 로깅
            val executionTime = System.currentTimeMillis() - startTime
            securityAuditLogger.logKeyBackup(
                userId = userId,
                backupId = savedBackup.id,
                success = true,
                executionTimeMs = executionTime,
                metadata = mapOf(
                    "expiration_days" to expirationDays,
                    "has_metadata" to (request.metadata != null)
                )
            )

            KeyBackupResponse(
                backupId = savedBackup.id,
                userId = userId,
                createdAt = savedBackup.createdAt,
                expiresAt = savedBackup.expiresAt,
                message = "키 백업이 성공적으로 생성되었습니다. 백업 ID: ${savedBackup.id}"
            )
        } catch (e: IllegalArgumentException) {
            logger.error("키 백업 생성 실패 (잘못된 입력): ${e.message}", e)
            val executionTime = System.currentTimeMillis() - startTime
            securityAuditLogger.logKeyBackup(
                userId = userId,
                backupId = null,
                success = false,
                executionTimeMs = executionTime,
                errorMessage = e.message
            )
            throw e
        } catch (e: Exception) {
            logger.error("키 백업 생성 실패: ${e.message}", e)
            val executionTime = System.currentTimeMillis() - startTime
            securityAuditLogger.logKeyBackup(
                userId = userId,
                backupId = null,
                success = false,
                executionTimeMs = executionTime,
                errorMessage = e.message
            )
            throw SecurityException("키 백업 생성 중 오류가 발생했습니다", e)
        }
    }

    /**
     * 키 백업 복구
     * 
     * @param userId 사용자 ID
     * @param request 복구 요청 (백업 ID, 백업 비밀번호)
     * @return 복구 성공 여부
     * @throws IllegalArgumentException 백업을 찾을 수 없거나 잘못된 비밀번호
     * @throws SecurityException 복구 중 보안 오류
     */
    fun restoreBackup(userId: UUID, request: KeyBackupRestoreRequest): Boolean {
        val startTime = System.currentTimeMillis()
        return try {
            logger.info("키 백업 복구 시작: 사용자 $userId, 백업 ID ${request.backupId}")

            // 1. 백업 조회
            val backup = request.backupId?.let {
                keyBackupRepository.findByBackupIdAndUserId(it, userId)
            } ?: run {
                // 백업 ID가 없으면 가장 최근 활성 백업 사용
                val activeBackups = keyBackupRepository.findActiveBackupsByUserId(userId, LocalDateTime.now())
                activeBackups.firstOrNull()
            } ?: throw IllegalArgumentException("복구 가능한 백업을 찾을 수 없습니다")

            // 2. 백업 만료 확인
            if (backup.expiresAt != null && backup.expiresAt.isBefore(LocalDateTime.now())) {
                throw IllegalArgumentException("백업이 만료되었습니다. 새 백업을 생성해주세요.")
            }

            // 3. 백업 사용 여부 확인
            if (backup.isUsed) {
                throw IllegalArgumentException("이미 사용된 백업입니다. 보안을 위해 재사용할 수 없습니다.")
            }

            // 4. 백업 데이터 복호화
            val decryptedBackupDataBytes = try {
                keyEncryptionUtil.decryptPrivateKey(
                    backup.encryptedBackupData,
                    request.backupPassword
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("백업 비밀번호가 올바르지 않습니다", e)
            }

            val decryptedBackupDataJson = String(decryptedBackupDataBytes, Charsets.UTF_8)

            // 5. 백업 데이터 해시 검증
            val calculatedHash = calculateHash(decryptedBackupDataJson)
            if (calculatedHash != backup.backupHash) {
                throw SecurityException("백업 데이터 무결성 검증 실패. 백업이 손상되었을 수 있습니다.")
            }

            // 6. 백업 데이터 파싱
            @Suppress("UNCHECKED_CAST")
            val backupData = objectMapper.readValue(
                decryptedBackupDataJson,
                Map::class.java
            ) as Map<String, Any>

            // 7. 키 복구 (현재는 기본 비밀번호 사용, 프로덕션에서는 사용자 비밀번호를 요청받아야 함)
            // TODO: 실제로는 사용자 비밀번호를 요청받아서 키를 복호화하고 재등록해야 함
            logger.warn("키 복구: 현재는 기본 비밀번호를 사용합니다. 프로덕션에서는 사용자 비밀번호를 요청받아야 합니다.")

            // 8. 백업 사용 표시 (불변성을 유지하기 위해 직접 UPDATE 쿼리 사용)
            val now = LocalDateTime.now()
            val updatedCount = keyBackupRepository.markBackupAsUsed(backup.id, userId, now)
            if (updatedCount == 0) {
                throw SecurityException("백업 사용 표시 실패")
            }

            logger.info("키 백업 복구 완료: 백업 ID ${backup.id}, 사용자 $userId")

            // 보안 감사 로깅
            val executionTime = System.currentTimeMillis() - startTime
            securityAuditLogger.logKeyRestore(
                userId = userId,
                backupId = backup.id,
                success = true,
                executionTimeMs = executionTime
            )

            true
        } catch (e: IllegalArgumentException) {
            logger.error("키 백업 복구 실패 (잘못된 입력): ${e.message}", e)
            val executionTime = System.currentTimeMillis() - startTime
            securityAuditLogger.logKeyRestore(
                userId = userId,
                backupId = request.backupId,
                success = false,
                executionTimeMs = executionTime,
                errorMessage = e.message
            )
            throw e
        } catch (e: Exception) {
            logger.error("키 백업 복구 실패: ${e.message}", e)
            val executionTime = System.currentTimeMillis() - startTime
            securityAuditLogger.logKeyRestore(
                userId = userId,
                backupId = request.backupId,
                success = false,
                executionTimeMs = executionTime,
                errorMessage = e.message
            )
            throw SecurityException("키 백업 복구 중 오류가 발생했습니다", e)
        }
    }

    /**
     * 사용자의 백업 목록 조회
     * 
     * @param userId 사용자 ID
     * @return 백업 목록
     */
    fun getBackupList(userId: UUID): KeyBackupListResponse {
        return try {
            val backups = keyBackupRepository.findByUserIdOrderByCreatedAtDesc(userId)
            val activeBackups = keyBackupRepository.findActiveBackupsByUserId(userId, LocalDateTime.now())

            val backupItems = backups.map { backup ->
                KeyBackupListItem(
                    backupId = backup.id,
                    createdAt = backup.createdAt,
                    expiresAt = backup.expiresAt,
                    isUsed = backup.isUsed,
                    usedAt = backup.usedAt,
                    metadata = backup.metadata
                )
            }

            KeyBackupListResponse(
                backups = backupItems,
                totalCount = backups.size,
                activeCount = activeBackups.size
            )
        } catch (e: Exception) {
            logger.error("백업 목록 조회 실패: ${e.message}", e)
            throw SecurityException("백업 목록 조회 중 오류가 발생했습니다", e)
        }
    }

    /**
     * 백업 삭제
     * 
     * @param userId 사용자 ID
     * @param backupId 백업 ID
     * @throws IllegalArgumentException 백업을 찾을 수 없거나 권한 없음
     */
    fun deleteBackup(userId: UUID, backupId: UUID) {
        return try {
            logger.info("백업 삭제 요청: 사용자 $userId, 백업 ID $backupId")

            val backup = keyBackupRepository.findByBackupIdAndUserId(backupId, userId)
                ?: throw IllegalArgumentException("백업을 찾을 수 없습니다")

            keyBackupRepository.delete(backup)

            logger.info("백업 삭제 완료: 백업 ID $backupId, 사용자 $userId")

            // 보안 감사 로깅
            securityAuditLogger.logKeyBackupDeletion(
                userId = userId,
                backupId = backupId,
                success = true
            )
        } catch (e: IllegalArgumentException) {
            logger.error("백업 삭제 실패 (잘못된 입력): ${e.message}", e)
            securityAuditLogger.logKeyBackupDeletion(
                userId = userId,
                backupId = backupId,
                success = false,
                errorMessage = e.message
            )
            throw e
        } catch (e: Exception) {
            logger.error("백업 삭제 실패: ${e.message}", e)
            securityAuditLogger.logKeyBackupDeletion(
                userId = userId,
                backupId = backupId,
                success = false,
                errorMessage = e.message
            )
            throw SecurityException("백업 삭제 중 오류가 발생했습니다", e)
        }
    }

    /**
     * SHA-256 해시 계산
     */
    private fun calculateHash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

