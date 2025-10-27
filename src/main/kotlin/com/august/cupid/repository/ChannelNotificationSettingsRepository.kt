package com.august.cupid.repository

import com.august.cupid.model.entity.ChannelNotificationSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 채널별 알림 설정 Repository (PostgreSQL)
 * 채널별 세부 알림 설정 및 음소거 관리
 */
@Repository
interface ChannelNotificationSettingsRepository : JpaRepository<ChannelNotificationSettings, UUID> {

    /**
     * 채널 ID로 설정들 조회
     */
    fun findByChannelId(channelId: UUID): List<ChannelNotificationSettings>

    /**
     * 사용자 ID로 설정들 조회
     */
    fun findByUserId(userId: UUID): List<ChannelNotificationSettings>

    /**
     * 채널과 사용자로 설정 조회
     */
    fun findByChannelIdAndUserId(channelId: UUID, userId: UUID): ChannelNotificationSettings?

    /**
     * 채널의 활성 알림 설정들 조회
     */
    @Query("SELECT cns FROM ChannelNotificationSettings cns WHERE cns.channel.id = :channelId AND cns.enabled = true")
    fun findEnabledSettingsByChannelId(@Param("channelId") channelId: UUID): List<ChannelNotificationSettings>

    /**
     * 사용자의 활성 알림 설정들 조회
     */
    @Query("SELECT cns FROM ChannelNotificationSettings cns WHERE cns.user.id = :userId AND cns.enabled = true")
    fun findEnabledSettingsByUserId(@Param("userId") userId: UUID): List<ChannelNotificationSettings>

    /**
     * 음소거된 채널들 조회
     */
    @Query("SELECT cns FROM ChannelNotificationSettings cns WHERE cns.user.id = :userId AND cns.mutedUntil IS NOT NULL AND cns.mutedUntil > :now")
    fun findMutedChannelsByUserId(@Param("userId") userId: UUID, @Param("now") now: LocalDateTime): List<ChannelNotificationSettings>

    /**
     * 만료된 음소거 설정들 조회
     */
    @Query("SELECT cns FROM ChannelNotificationSettings cns WHERE cns.mutedUntil IS NOT NULL AND cns.mutedUntil <= :now")
    fun findExpiredMutedSettings(@Param("now") now: LocalDateTime): List<ChannelNotificationSettings>

    /**
     * 채널 알림 설정 존재 여부 확인
     */
    fun existsByChannelIdAndUserId(channelId: UUID, userId: UUID): Boolean

    /**
     * 채널 알림 활성화 상태 토글
     */
    @Query("UPDATE ChannelNotificationSettings cns SET cns.enabled = :enabled, cns.updatedAt = CURRENT_TIMESTAMP WHERE cns.channel.id = :channelId AND cns.user.id = :userId")
    fun toggleChannelNotification(@Param("channelId") channelId: UUID, @Param("userId") userId: UUID, @Param("enabled") enabled: Boolean): Int

    /**
     * 채널 음소거 설정
     */
    @Query("UPDATE ChannelNotificationSettings cns SET cns.mutedUntil = :mutedUntil, cns.updatedAt = CURRENT_TIMESTAMP WHERE cns.channel.id = :channelId AND cns.user.id = :userId")
    fun muteChannel(@Param("channelId") channelId: UUID, @Param("userId") userId: UUID, @Param("mutedUntil") mutedUntil: LocalDateTime): Int

    /**
     * 채널 음소거 해제
     */
    @Query("UPDATE ChannelNotificationSettings cns SET cns.mutedUntil = NULL, cns.updatedAt = CURRENT_TIMESTAMP WHERE cns.channel.id = :channelId AND cns.user.id = :userId")
    fun unmuteChannel(@Param("channelId") channelId: UUID, @Param("userId") userId: UUID): Int

    /**
     * 만료된 음소거 설정들 해제
     */
    @Query("UPDATE ChannelNotificationSettings cns SET cns.mutedUntil = NULL, cns.updatedAt = CURRENT_TIMESTAMP WHERE cns.mutedUntil IS NOT NULL AND cns.mutedUntil <= :now")
    fun unmuteExpiredChannels(@Param("now") now: LocalDateTime): Int

    /**
     * 소리 알림이 활성화된 채널 설정들 조회
     */
    @Query("SELECT cns FROM ChannelNotificationSettings cns WHERE cns.soundEnabled = true AND cns.enabled = true")
    fun findSettingsWithSoundEnabled(): List<ChannelNotificationSettings>

    /**
     * 진동 알림이 활성화된 채널 설정들 조회
     */
    @Query("SELECT cns FROM ChannelNotificationSettings cns WHERE cns.vibrationEnabled = true AND cns.enabled = true")
    fun findSettingsWithVibrationEnabled(): List<ChannelNotificationSettings>

    /**
     * 특정 소리 설정을 사용하는 채널들 조회
     */
    fun findBySoundNameAndEnabledTrue(soundName: String): List<ChannelNotificationSettings>

    /**
     * 채널별 알림 설정 통계 조회
     */
    @Query("SELECT COUNT(cns) FROM ChannelNotificationSettings cns WHERE cns.channel.id = :channelId AND cns.enabled = true")
    fun countEnabledSettingsByChannelId(@Param("channelId") channelId: UUID): Long

    @Query("SELECT COUNT(cns) FROM ChannelNotificationSettings cns WHERE cns.user.id = :userId AND cns.enabled = true")
    fun countEnabledSettingsByUserId(@Param("userId") userId: UUID): Long

    @Query("SELECT COUNT(cns) FROM ChannelNotificationSettings cns WHERE cns.soundEnabled = true")
    fun countSettingsWithSoundEnabled(): Long

    @Query("SELECT COUNT(cns) FROM ChannelNotificationSettings cns WHERE cns.vibrationEnabled = true")
    fun countSettingsWithVibrationEnabled(): Long

    /**
     * 사용자의 채널별 알림 설정 업데이트
     */
    @Query("""
        UPDATE ChannelNotificationSettings cns 
        SET cns.enabled = :enabled,
            cns.soundEnabled = :soundEnabled,
            cns.soundName = :soundName,
            cns.vibrationEnabled = :vibrationEnabled,
            cns.vibrationPattern = :vibrationPattern,
            cns.updatedAt = CURRENT_TIMESTAMP
        WHERE cns.channel.id = :channelId AND cns.user.id = :userId
    """)
    fun updateChannelNotificationSettings(
        @Param("channelId") channelId: UUID,
        @Param("userId") userId: UUID,
        @Param("enabled") enabled: Boolean,
        @Param("soundEnabled") soundEnabled: Boolean,
        @Param("soundName") soundName: String,
        @Param("vibrationEnabled") vibrationEnabled: Boolean,
        @Param("vibrationPattern") vibrationPattern: List<Int>
    ): Int
}
