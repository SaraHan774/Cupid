package com.august.cupid.repository

import com.august.cupid.model.entity.ChannelMembers
import com.august.cupid.model.entity.ChannelRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 채널 멤버 Repository (PostgreSQL)
 * 채널 참여자 및 읽음 상태 관리
 */
@Repository
interface ChannelMembersRepository : JpaRepository<ChannelMembers, UUID> {

    /**
     * 채널 ID로 멤버들 조회
     */
    fun findByChannelId(channelId: UUID): List<ChannelMembers>

    /**
     * 사용자 ID로 참여 채널들 조회
     */
    fun findByUserId(userId: UUID): List<ChannelMembers>

    /**
     * 채널과 사용자로 멤버십 조회
     */
    fun findByChannelIdAndUserId(channelId: UUID, userId: UUID): ChannelMembers?

    /**
     * 활성 멤버들만 조회
     */
    fun findByChannelIdAndIsActiveTrue(channelId: UUID): List<ChannelMembers>

    fun findByUserIdAndIsActiveTrue(userId: UUID): List<ChannelMembers>

    /**
     * 채널 멤버십 존재 여부 확인
     */
    fun existsByChannelIdAndUserIdAndIsActiveTrue(channelId: UUID, userId: UUID): Boolean

    /**
     * 채널의 활성 멤버 수 조회
     */
    @Query("SELECT COUNT(cm) FROM ChannelMembers cm WHERE cm.channel.id = :channelId AND cm.isActive = true")
    fun countActiveMembersByChannelId(@Param("channelId") channelId: UUID): Long

    /**
     * 사용자가 참여한 활성 채널 수 조회
     */
    @Query("SELECT COUNT(cm) FROM ChannelMembers cm WHERE cm.user.id = :userId AND cm.isActive = true")
    fun countActiveChannelsByUserId(@Param("userId") userId: UUID): Long

    /**
     * 채널의 관리자들 조회
     */
    @Query("SELECT cm FROM ChannelMembers cm WHERE cm.channel.id = :channelId AND cm.role = 'ADMIN' AND cm.isActive = true")
    fun findAdminsByChannelId(@Param("channelId") channelId: UUID): List<ChannelMembers>

    /**
     * 사용자가 관리자인 채널들 조회
     */
    @Query("SELECT cm FROM ChannelMembers cm WHERE cm.user.id = :userId AND cm.role = 'ADMIN' AND cm.isActive = true")
    fun findAdminChannelsByUserId(@Param("userId") userId: UUID): List<ChannelMembers>

    /**
     * 마지막 읽음 시간 업데이트
     */
    @Query("UPDATE ChannelMembers cm SET cm.lastReadAt = :readAt WHERE cm.channel.id = :channelId AND cm.user.id = :userId")
    fun updateLastReadAt(@Param("channelId") channelId: UUID, @Param("userId") userId: UUID, @Param("readAt") readAt: LocalDateTime): Int

    /**
     * 채널에서 사용자 제거 (soft delete)
     */
    @Query("UPDATE ChannelMembers cm SET cm.isActive = false, cm.leftAt = :leftAt WHERE cm.channel.id = :channelId AND cm.user.id = :userId")
    fun removeUserFromChannel(@Param("channelId") channelId: UUID, @Param("userId") userId: UUID, @Param("leftAt") leftAt: LocalDateTime): Int

    /**
     * 읽지 않은 메시지가 있는 채널들 조회
     */
    @Query("""
        SELECT cm FROM ChannelMembers cm 
        WHERE cm.user.id = :userId 
        AND cm.isActive = true 
        AND (cm.lastReadAt IS NULL OR cm.lastReadAt < :since)
        ORDER BY cm.channel.updatedAt DESC
    """)
    fun findChannelsWithUnreadMessages(@Param("userId") userId: UUID, @Param("since") since: LocalDateTime): List<ChannelMembers>

    /**
     * 특정 시간 이후 참여한 멤버들 조회
     */
    @Query("SELECT cm FROM ChannelMembers cm WHERE cm.channel.id = :channelId AND cm.joinedAt >= :since AND cm.isActive = true")
    fun findRecentMembersByChannelId(@Param("channelId") channelId: UUID, @Param("since") since: LocalDateTime): List<ChannelMembers>

    /**
     * 채널 멤버 역할 변경
     */
    @Query("UPDATE ChannelMembers cm SET cm.role = :role WHERE cm.channel.id = :channelId AND cm.user.id = :userId")
    fun updateMemberRole(@Param("channelId") channelId: UUID, @Param("userId") userId: UUID, @Param("role") role: ChannelRole): Int
}
