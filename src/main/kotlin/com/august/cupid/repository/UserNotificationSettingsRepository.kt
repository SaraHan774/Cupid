package com.august.cupid.repository

import com.august.cupid.model.entity.UserNotificationSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalTime
import java.util.*

/**
 * 사용자 알림 설정 Repository (PostgreSQL)
 * 전역 알림 설정 및 방해금지 모드 관리
 */
@Repository
interface UserNotificationSettingsRepository : JpaRepository<UserNotificationSettings, UUID> {

    /**
     * 사용자 ID로 설정 조회
     */
    fun findByUserId(userId: UUID): UserNotificationSettings?

    /**
     * 사용자 설정 존재 여부 확인
     */
    fun existsByUserId(userId: UUID): Boolean

    /**
     * 알림이 활성화된 사용자들 조회
     */
    @Query("SELECT uns FROM UserNotificationSettings uns WHERE uns.enabled = true")
    fun findUsersWithEnabledNotifications(): List<UserNotificationSettings>

    /**
     * 방해금지 모드가 활성화된 사용자들 조회
     */
    @Query("SELECT uns FROM UserNotificationSettings uns WHERE uns.dndEnabled = true")
    fun findUsersWithDndEnabled(): List<UserNotificationSettings>

    /**
     * 현재 방해금지 시간인 사용자들 조회
     * TODO: PostgreSQL 배열 쿼리 문제로 인해 임시로 비활성화
     */
    // @Query("""
    //     SELECT uns FROM UserNotificationSettings uns 
    //     WHERE uns.dndEnabled = true 
    //     AND uns.enabled = true
    //     AND (
    //         (uns.dndStartTime <= uns.dndEndTime AND :currentTime >= uns.dndStartTime AND :currentTime <= uns.dndEndTime)
    //         OR (uns.dndStartTime > uns.dndEndTime AND (:currentTime >= uns.dndStartTime OR :currentTime <= uns.dndEndTime))
    //     )
    //     AND :dayOfWeek = ANY(uns.dndDays)
    // """)
    // fun findUsersInDndMode(@Param("currentTime") currentTime: LocalTime, @Param("dayOfWeek") dayOfWeek: Int): List<UserNotificationSettings>

    /**
     * 알림 미리보기가 활성화된 사용자들 조회
     */
    @Query("SELECT uns FROM UserNotificationSettings uns WHERE uns.showPreview = true AND uns.enabled = true")
    fun findUsersWithPreviewEnabled(): List<UserNotificationSettings>

    /**
     * 소리 알림이 활성화된 사용자들 조회
     */
    @Query("SELECT uns FROM UserNotificationSettings uns WHERE uns.soundEnabled = true AND uns.enabled = true")
    fun findUsersWithSoundEnabled(): List<UserNotificationSettings>

    /**
     * 진동 알림이 활성화된 사용자들 조회
     */
    @Query("SELECT uns FROM UserNotificationSettings uns WHERE uns.vibrationEnabled = true AND uns.enabled = true")
    fun findUsersWithVibrationEnabled(): List<UserNotificationSettings>

    /**
     * 알림 설정 업데이트
     */
    @Query("""
        UPDATE UserNotificationSettings uns 
        SET uns.enabled = :enabled, 
            uns.soundEnabled = :soundEnabled, 
            uns.vibrationEnabled = :vibrationEnabled, 
            uns.showPreview = :showPreview,
            uns.dndEnabled = :dndEnabled,
            uns.dndStartTime = :dndStartTime,
            uns.dndEndTime = :dndEndTime,
            uns.dndDays = :dndDays,
            uns.updatedAt = CURRENT_TIMESTAMP
        WHERE uns.userId = :userId
    """)
    fun updateNotificationSettings(
        @Param("userId") userId: UUID,
        @Param("enabled") enabled: Boolean,
        @Param("soundEnabled") soundEnabled: Boolean,
        @Param("vibrationEnabled") vibrationEnabled: Boolean,
        @Param("showPreview") showPreview: Boolean,
        @Param("dndEnabled") dndEnabled: Boolean,
        @Param("dndStartTime") dndStartTime: LocalTime,
        @Param("dndEndTime") dndEndTime: LocalTime,
        @Param("dndDays") dndDays: List<Int>
    ): Int

    /**
     * 알림 활성화 상태 토글
     */
    @Query("UPDATE UserNotificationSettings uns SET uns.enabled = :enabled, uns.updatedAt = CURRENT_TIMESTAMP WHERE uns.userId = :userId")
    fun toggleNotificationEnabled(@Param("userId") userId: UUID, @Param("enabled") enabled: Boolean): Int

    /**
     * 방해금지 모드 토글
     */
    @Query("UPDATE UserNotificationSettings uns SET uns.dndEnabled = :dndEnabled, uns.updatedAt = CURRENT_TIMESTAMP WHERE uns.userId = :userId")
    fun toggleDndMode(@Param("userId") userId: UUID, @Param("dndEnabled") dndEnabled: Boolean): Int

    /**
     * 알림 설정 통계 조회
     */
    @Query("SELECT COUNT(uns) FROM UserNotificationSettings uns WHERE uns.enabled = true")
    fun countUsersWithEnabledNotifications(): Long

    @Query("SELECT COUNT(uns) FROM UserNotificationSettings uns WHERE uns.dndEnabled = true")
    fun countUsersWithDndEnabled(): Long

    @Query("SELECT COUNT(uns) FROM UserNotificationSettings uns WHERE uns.soundEnabled = true")
    fun countUsersWithSoundEnabled(): Long

    @Query("SELECT COUNT(uns) FROM UserNotificationSettings uns WHERE uns.vibrationEnabled = true")
    fun countUsersWithVibrationEnabled(): Long
}
