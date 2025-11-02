package com.august.cupid.scheduler

import com.august.cupid.model.dto.KeyReplenishmentRequest
import com.august.cupid.model.dto.OneTimePreKeyUploadDto
import com.august.cupid.model.entity.KeyRotationHistory
import com.august.cupid.model.entity.RotationType
import com.august.cupid.repository.KeyRotationHistoryRepository
import com.august.cupid.repository.SignalPreKeyRepository
import com.august.cupid.repository.SignalSignedPreKeyRepository
import com.august.cupid.repository.UserKeysRepository
import com.august.cupid.service.EncryptionService
import com.august.cupid.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.whispersystems.libsignal.util.KeyHelper
import java.time.LocalDateTime
import java.util.*
import java.util.Base64

/**
 * 키 회전 스케줄러
 * 
 * 기능:
 * 1. 주간 Signed Pre-Key 회전 (매주 일요일 새벽 3시)
 * 2. One-Time Pre-Keys 자동 보충 (하루에 한 번, 개수가 임계값 이하일 때)
 * 3. 키 회전 이력 저장
 * 4. 사용자에게 알림 전송
 * 
 * SECURITY NOTE:
 * - 현재는 DEFAULT_TEMP_PASSWORD를 사용하고 있음
 * - 프로덕션에서는 마스터 키 관리 시스템을 구축해야 함
 * - 사용자 비밀번호 해시에서 파생된 키를 사용하거나
 * - 시스템 마스터 키를 별도로 안전하게 관리해야 함
 */
@Component
class KeyRotationScheduler(
    private val encryptionService: EncryptionService,
    private val userKeysRepository: UserKeysRepository,
    private val signalPreKeyRepository: SignalPreKeyRepository,
    private val signalSignedPreKeyRepository: SignalSignedPreKeyRepository,
    private val keyRotationHistoryRepository: KeyRotationHistoryRepository,
    private val notificationService: NotificationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // 임계값 설정
        private const val PRE_KEY_MINIMUM_THRESHOLD = 20 // One-Time Pre-Key 개수 임계값
        private const val ONE_TIME_PRE_KEY_BATCH_SIZE = 100 // 보충 시 생성할 개수
        
        // 회전 주기 설정
        private const val SIGNED_PRE_KEY_ROTATION_DAYS = 7 // Signed Pre-Key 회전 주기 (일)
        private const val SIGNED_PRE_KEY_EXPIRY_DAYS = 30 // Signed Pre-Key 만료 기간
        
        // SECURITY: 프로덕션에서는 마스터 키 관리 시스템 필요
        // 현재는 개발/테스트 목적으로 임시 비밀번호 사용
        private const val TEMP_PASSWORD = "DEFAULT_TEMP_PASSWORD"
    }

    /**
     * 주간 Signed Pre-Key 회전 스케줄러
     * 매주 일요일 새벽 3시에 실행
     * 
     * Cron 표현식: "0 0 3 * * SUN"
     * - 초: 0
     * - 분: 0
     * - 시: 3
     * - 일: *
     * - 월: *
     * - 요일: SUN (일요일)
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    fun rotateSignedPreKeysWeekly() {
        logger.info("주간 Signed Pre-Key 회전 작업 시작")
        val startTime = System.currentTimeMillis()
        
        var successCount = 0
        var failureCount = 0
        
        try {
            // 1. 만료 예정인 Signed Pre-Key를 가진 모든 사용자 조회
            val expiryThreshold = LocalDateTime.now().plusDays(SIGNED_PRE_KEY_ROTATION_DAYS.toLong())
            val expiringKeys = signalSignedPreKeyRepository.findExpiringSoon(expiryThreshold)
            
            logger.info("회전 대상 Signed Pre-Keys: ${expiringKeys.size}개")
            
            // 2. 각 사용자에 대해 회전 수행
            val processedUsers = mutableSetOf<UUID>()
            expiringKeys.forEach { signedPreKey ->
                val userId = signedPreKey.userId
                
                // 중복 처리 방지
                if (processedUsers.contains(userId)) {
                    return@forEach
                }
                processedUsers.add(userId)
                
                val executionStartTime = System.currentTimeMillis()
                try {
                    
                    // 3. 이전 키 ID 저장
                    val previousKeyId = signedPreKey.signedPreKeyId
                    
                    // 4. Signed Pre-Key 회전
                    // SECURITY WARNING: 프로덕션에서는 마스터 키 사용 필요
                    val newKeyId = encryptionService.rotateSignedPreKey(userId, TEMP_PASSWORD)
                    
                    val executionTime = System.currentTimeMillis() - executionStartTime
                    
                    // 5. 회전 이력 저장
                    val history = KeyRotationHistory(
                        userId = userId,
                        rotationType = RotationType.SIGNED_PRE_KEY,
                        previousKeyId = previousKeyId,
                        newKeyId = newKeyId,
                        success = true,
                        executionTimeMs = executionTime
                    )
                    keyRotationHistoryRepository.save(history)
                    
                    successCount++
                    
                    logger.info("Signed Pre-Key 회전 완료: userId={}, previousKeyId={}, newKeyId={}, executionTime={}ms",
                        userId, previousKeyId, newKeyId, executionTime)
                    
                    // 6. 사용자에게 알림 전송 (선택적)
                    try {
                        sendKeyRotationNotification(userId, "Signed Pre-Key가 자동으로 회전되었습니다.")
                    } catch (e: Exception) {
                        logger.warn("키 회전 알림 전송 실패: userId={}", userId, e)
                        // 알림 실패는 전체 작업을 중단하지 않음
                    }
                    
                } catch (e: Exception) {
                    failureCount++
                    val executionTime = System.currentTimeMillis() - executionStartTime
                    
                    logger.error("Signed Pre-Key 회전 실패: userId={}", userId, e)
                    
                    // 실패 이력 저장
                    val history = KeyRotationHistory(
                        userId = userId,
                        rotationType = RotationType.SIGNED_PRE_KEY,
                        success = false,
                        errorMessage = e.message ?: "Unknown error",
                        executionTimeMs = executionTime
                    )
                    keyRotationHistoryRepository.save(history)
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            logger.info("주간 Signed Pre-Key 회전 작업 완료: 성공={}, 실패={}, 총 소요 시간={}ms",
                successCount, failureCount, totalTime)
            
        } catch (e: Exception) {
            logger.error("주간 Signed Pre-Key 회전 작업 중 오류 발생", e)
        }
    }

    /**
     * One-Time Pre-Keys 자동 보충 스케줄러
     * 매일 새벽 4시에 실행하여 개수가 임계값 이하인 사용자의 키를 보충
     * 
     * Cron 표현식: "0 0 4 * * *"
     * - 초: 0
     * - 분: 0
     * - 시: 4
     * - 일: *
     * - 월: *
     * - 요일: * (매일)
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    fun replenishOneTimePreKeysDaily() {
        logger.info("One-Time Pre-Keys 자동 보충 작업 시작")
        val startTime = System.currentTimeMillis()
        
        var successCount = 0
        var failureCount = 0
        var replenishedCount = 0
        
        try {
            // 1. 모든 활성 사용자 키 조회
            val now = LocalDateTime.now()
            val allUserKeys = userKeysRepository.findAll().filter { 
                it.expiresAt == null || it.expiresAt.isAfter(now)
            }
            
            logger.info("활성 사용자 키 개수: ${allUserKeys.size}")
            
            // 2. 각 사용자에 대해 One-Time Pre-Key 개수 확인
            allUserKeys.forEach { userKeys ->
                val userId = userKeys.user.id
                
                try {
                    // 3. 현재 사용 가능한 One-Time Pre-Key 개수 확인
                    val availableCount = signalPreKeyRepository.countAvailablePreKeys(userId, now)
                    
                    if (availableCount >= PRE_KEY_MINIMUM_THRESHOLD) {
                        // 임계값 이상이면 보충 불필요
                        logger.debug("One-Time Pre-Keys 개수 충분: userId={}, count={}", userId, availableCount)
                        return@forEach
                    }
                    
                    logger.info("One-Time Pre-Keys 보충 필요: userId={}, currentCount={}, threshold={}",
                        userId, availableCount, PRE_KEY_MINIMUM_THRESHOLD)
                    
                    val executionStartTimePreKeys = System.currentTimeMillis()
                    
                    // 4. 새 One-Time Pre-Keys 생성
                    // SECURITY WARNING: 프로덕션에서는 마스터 키 사용 필요
                    // 현재는 generateIdentityKeys를 다시 호출하여 키를 생성하지만,
                    // 실제로는 기존 Identity Key를 사용하여 새 Pre-Keys만 생성해야 함
                    
                    // 임시 해결책: KeyReplenishmentRequest를 생성하여 보충
                    // 실제 구현에서는 EncryptionService에 키 생성만 하는 메서드가 필요할 수 있음
                    val maxPreKeyId = signalPreKeyRepository.findMaxPreKeyId(userId)
                    val newPreKeys = KeyHelper.generatePreKeys(maxPreKeyId + 1, ONE_TIME_PRE_KEY_BATCH_SIZE)
                    
                    val preKeyUploads = newPreKeys.map { preKeyRecord ->
                        OneTimePreKeyUploadDto(
                            keyId = preKeyRecord.id,
                            publicKey = Base64.getEncoder().encodeToString(preKeyRecord.keyPair.publicKey.serialize())
                        )
                    }
                    
                    val replenishmentRequest = KeyReplenishmentRequest(
                        oneTimePreKeys = preKeyUploads
                    )
                    
                    // 5. Pre-Keys 보충
                    val keysAdded = encryptionService.replenishPreKeys(userId, replenishmentRequest)
                    
                    val executionTime = System.currentTimeMillis() - executionStartTimePreKeys
                    
                    // 6. 보충 이력 저장
                    val history = KeyRotationHistory(
                        userId = userId,
                        rotationType = RotationType.ONE_TIME_PRE_KEYS,
                        keysAdded = keysAdded,
                        success = true,
                        executionTimeMs = executionTime
                    )
                    keyRotationHistoryRepository.save(history)
                    
                    successCount++
                    replenishedCount += keysAdded
                    
                    logger.info("One-Time Pre-Keys 보충 완료: userId={}, keysAdded={}, executionTime={}ms",
                        userId, keysAdded, executionTime)
                    
                    // 7. 사용자에게 알림 전송 (선택적 - 너무 빈번할 수 있으므로 주석 처리)
                    // try {
                    //     sendKeyRotationNotification(userId, "One-Time Pre-Keys가 자동으로 보충되었습니다.")
                    // } catch (e: Exception) {
                    //     logger.warn("키 회전 알림 전송 실패: userId={}", userId, e)
                    // }
                    
                } catch (e: Exception) {
                    failureCount++
                    // executionStartTimePreKeys는 try 블록 내에서만 정의되므로, 여기서는 현재 시간을 사용
                    val executionTime = 0L
                    
                    logger.error("One-Time Pre-Keys 보충 실패: userId={}", userId, e)
                    
                    // 실패 이력 저장
                    val history = KeyRotationHistory(
                        userId = userId,
                        rotationType = RotationType.ONE_TIME_PRE_KEYS,
                        success = false,
                        errorMessage = e.message ?: "Unknown error",
                        executionTimeMs = executionTime
                    )
                    keyRotationHistoryRepository.save(history)
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            logger.info("One-Time Pre-Keys 자동 보충 작업 완료: 성공={}, 실패={}, 총 보충={}, 총 소요 시간={}ms",
                successCount, failureCount, replenishedCount, totalTime)
            
        } catch (e: Exception) {
            logger.error("One-Time Pre-Keys 자동 보충 작업 중 오류 발생", e)
        }
    }

    /**
     * 키 회전 알림 전송
     * 사용자에게 키가 회전되었음을 알림
     * 
     * @param userId 사용자 ID
     * @param message 알림 메시지
     */
    private fun sendKeyRotationNotification(userId: UUID, message: String) {
        try {
            // 사용자가 설정한 알림 설정 확인
            // 현재는 간단히 로그만 남기고, 실제로는 NotificationService를 통해 알림 전송
            logger.info("키 회전 알림: userId={}, message={}", userId, message)
            
            // TODO: NotificationService를 통해 실제 알림 전송
            // notificationService.sendSystemNotification(userId, "키 회전", message)
            
        } catch (e: Exception) {
            logger.error("키 회전 알림 전송 실패: userId={}", userId, e)
            // 알림 실패는 치명적이지 않음
        }
    }

    /**
     * 수동 키 회전 트리거 (관리자용)
     * 스케줄러에 의해 자동 실행되지 않고, 관리자 엔드포인트에서 호출 가능
     * 
     * @param userId 회전할 사용자 ID (null이면 모든 사용자)
     * @return 회전 결과 요약
     */
    fun manualRotateSignedPreKey(userId: UUID?): KeyRotationResult {
        logger.info("수동 Signed Pre-Key 회전 요청: userId={}", userId)
        
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var failureCount = 0
        
        try {
            val usersToProcess = if (userId != null) {
                listOf(userId)
            } else {
                // 모든 활성 사용자
                val now = LocalDateTime.now()
                userKeysRepository.findAll()
                    .filter { it.expiresAt == null || it.expiresAt.isAfter(now) }
                    .map { it.user.id }
            }
            
            usersToProcess.forEach { targetUserId ->
                try {
                    val executionStartTime = System.currentTimeMillis()
                    
                    // 이전 키 ID 확인
                    val previousKey = signalSignedPreKeyRepository.findByUserIdAndIsActiveTrue(targetUserId)
                    val previousKeyId = previousKey?.signedPreKeyId
                    
                    // 회전 수행
                    val newKeyId = encryptionService.rotateSignedPreKey(targetUserId, TEMP_PASSWORD)
                    
                    val executionTime = System.currentTimeMillis() - executionStartTime
                    
                    // 이력 저장
                    val history = KeyRotationHistory(
                        userId = targetUserId,
                        rotationType = RotationType.SIGNED_PRE_KEY,
                        previousKeyId = previousKeyId,
                        newKeyId = newKeyId,
                        success = true,
                        executionTimeMs = executionTime
                    )
                    keyRotationHistoryRepository.save(history)
                    
                    successCount++
                    
                    logger.info("수동 Signed Pre-Key 회전 완료: userId={}, previousKeyId={}, newKeyId={}",
                        targetUserId, previousKeyId, newKeyId)
                    
                } catch (e: Exception) {
                    failureCount++
                    logger.error("수동 Signed Pre-Key 회전 실패: userId={}", targetUserId, e)
                    
                    val history = KeyRotationHistory(
                        userId = targetUserId,
                        rotationType = RotationType.SIGNED_PRE_KEY,
                        success = false,
                        errorMessage = e.message ?: "Unknown error"
                    )
                    keyRotationHistoryRepository.save(history)
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            
            return KeyRotationResult(
                success = failureCount == 0,
                successCount = successCount,
                failureCount = failureCount,
                executionTimeMs = totalTime,
                message = "Signed Pre-Key 회전 완료: 성공=$successCount, 실패=$failureCount"
            )
            
        } catch (e: Exception) {
            logger.error("수동 Signed Pre-Key 회전 작업 중 오류 발생", e)
            return KeyRotationResult(
                success = false,
                successCount = successCount,
                failureCount = failureCount,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "오류 발생: ${e.message}"
            )
        }
    }

    /**
     * 수동 One-Time Pre-Keys 보충 트리거 (관리자용)
     * 
     * @param userId 보충할 사용자 ID (null이면 모든 사용자)
     * @return 보충 결과 요약
     */
    fun manualReplenishPreKeys(userId: UUID?): KeyRotationResult {
        logger.info("수동 One-Time Pre-Keys 보충 요청: userId={}", userId)
        
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var failureCount = 0
        var totalKeysAdded = 0
        
        try {
            val usersToProcess = if (userId != null) {
                listOf(userId)
            } else {
                // 모든 활성 사용자
                val now = LocalDateTime.now()
                userKeysRepository.findAll()
                    .filter { it.expiresAt == null || it.expiresAt.isAfter(now) }
                    .map { it.user.id }
            }
            
            usersToProcess.forEach { targetUserId ->
                try {
                    val executionStartTime = System.currentTimeMillis()
                    
                    // 현재 개수 확인
                    val now = LocalDateTime.now()
                    val availableCount = signalPreKeyRepository.countAvailablePreKeys(targetUserId, now)
                    
                    // 새 Pre-Keys 생성
                    val maxPreKeyId = signalPreKeyRepository.findMaxPreKeyId(targetUserId)
                    val newPreKeys = KeyHelper.generatePreKeys(maxPreKeyId + 1, ONE_TIME_PRE_KEY_BATCH_SIZE)
                    
                    val preKeyUploads = newPreKeys.map { preKeyRecord ->
                        OneTimePreKeyUploadDto(
                            keyId = preKeyRecord.id,
                            publicKey = Base64.getEncoder().encodeToString(preKeyRecord.keyPair.publicKey.serialize())
                        )
                    }
                    
                    val replenishmentRequest = KeyReplenishmentRequest(
                        oneTimePreKeys = preKeyUploads
                    )
                    
                    val keysAdded = encryptionService.replenishPreKeys(targetUserId, replenishmentRequest)
                    
                    val executionTime = System.currentTimeMillis() - executionStartTime
                    
                    // 이력 저장
                    val history = KeyRotationHistory(
                        userId = targetUserId,
                        rotationType = RotationType.ONE_TIME_PRE_KEYS,
                        keysAdded = keysAdded,
                        success = true,
                        executionTimeMs = executionTime
                    )
                    keyRotationHistoryRepository.save(history)
                    
                    successCount++
                    totalKeysAdded += keysAdded
                    
                    logger.info("수동 One-Time Pre-Keys 보충 완료: userId={}, keysAdded={}",
                        targetUserId, keysAdded)
                    
                } catch (e: Exception) {
                    failureCount++
                    logger.error("수동 One-Time Pre-Keys 보충 실패: userId={}", targetUserId, e)
                    
                    val history = KeyRotationHistory(
                        userId = targetUserId,
                        rotationType = RotationType.ONE_TIME_PRE_KEYS,
                        success = false,
                        errorMessage = e.message ?: "Unknown error"
                    )
                    keyRotationHistoryRepository.save(history)
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            
            return KeyRotationResult(
                success = failureCount == 0,
                successCount = successCount,
                failureCount = failureCount,
                executionTimeMs = totalTime,
                message = "One-Time Pre-Keys 보충 완료: 성공=$successCount, 실패=$failureCount, 총 추가=$totalKeysAdded"
            )
            
        } catch (e: Exception) {
            logger.error("수동 One-Time Pre-Keys 보충 작업 중 오류 발생", e)
            return KeyRotationResult(
                success = false,
                successCount = successCount,
                failureCount = failureCount,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "오류 발생: ${e.message}"
            )
        }
    }
}

/**
 * 키 회전 결과 데이터 클래스
 */
data class KeyRotationResult(
    val success: Boolean,
    val successCount: Int,
    val failureCount: Int,
    val executionTimeMs: Long,
    val message: String
)

