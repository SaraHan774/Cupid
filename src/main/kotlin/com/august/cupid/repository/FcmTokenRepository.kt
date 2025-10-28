package com.august.cupid.repository

import com.august.cupid.model.entity.notification.FcmToken
import com.august.cupid.model.entity.notification.DeviceType
import com.august.cupid.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * FCM 토큰 Repository (PostgreSQL)
 * 디바이스별 FCM 토큰 관리
 */
@Repository
interface FcmTokenRepository : JpaRepository<FcmToken, UUID> {

    /**
     * 사용자 ID로 토큰들 조회
     */
    fun findByUserId(user: User): List<FcmToken>

    /**
     * 사용자의 활성 토큰들 조회
     */
    fun findByUserAndIsActiveTrue(user: User): List<FcmToken>

    /**
     * 토큰으로 조회
     */
    fun findByToken(token: String): FcmToken?

    /**
     * 디바이스 타입별 토큰 조회
     */
    fun findByUserAndDeviceTypeAndIsActiveTrue(user: User, deviceType: DeviceType): List<FcmToken>

    /**
     * 토큰 존재 여부 확인
     */
    fun existsByToken(token: String): Boolean

    /**
     * 사용자의 토큰 존재 여부 확인
     */
    fun existsByUserAndIsActiveTrue(user: User): Boolean

    /**
     * 오래된 토큰들 조회 (30일 이상 미사용)
     */
    @Query("SELECT ft FROM FcmToken ft WHERE ft.lastUsedAt < :threshold")
    fun findOldTokens(@Param("threshold") threshold: LocalDateTime): List<FcmToken>

    /**
     * 오래된 토큰들 삭제
     */
    @Query("DELETE FROM FcmToken ft WHERE ft.lastUsedAt < :threshold")
    fun deleteOldTokens(@Param("threshold") threshold: LocalDateTime): Int

    /**
     * 사용자의 모든 토큰 비활성화
     */
    @Query("UPDATE FcmToken ft SET ft.isActive = false WHERE ft.user.id = :userId")
    fun deactivateAllTokensByUserId(@Param("userId") userId: UUID): Int

    /**
     * 특정 토큰 비활성화
     */
    @Query("UPDATE FcmToken ft SET ft.isActive = false WHERE ft.token = :token")
    fun deactivateToken(@Param("token") token: String): Int

    /**
     * 토큰 마지막 사용 시간 업데이트
     */
    @Query("UPDATE FcmToken ft SET ft.lastUsedAt = :lastUsedAt WHERE ft.token = :token")
    fun updateLastUsedAt(@Param("token") token: String, @Param("lastUsedAt") lastUsedAt: LocalDateTime): Int

    /**
     * 사용자별 활성 토큰 개수 조회
     */
    @Query("SELECT COUNT(ft) FROM FcmToken ft WHERE ft.user.id = :userId AND ft.isActive = true")
    fun countActiveTokensByUserId(@Param("userId") userId: UUID): Long

    /**
     * 디바이스 타입별 토큰 개수 조회
     */
    @Query("SELECT COUNT(ft) FROM FcmToken ft WHERE ft.deviceType = :deviceType AND ft.isActive = true")
    fun countActiveTokensByDeviceType(@Param("deviceType") deviceType: DeviceType): Long

    /**
     * 특정 앱 버전의 토큰들 조회
     */
    fun findByAppVersionAndIsActiveTrue(appVersion: String): List<FcmToken>

    /**
     * 사용자의 특정 디바이스 토큰 조회
     */
    fun findByUserAndDeviceNameAndIsActiveTrue(user: User, deviceName: String): List<FcmToken>

    /**
     * 토큰 중복 확인 (같은 사용자, 같은 토큰)
     */
    fun existsByUserAndToken(user: User, token: String): Boolean
}
