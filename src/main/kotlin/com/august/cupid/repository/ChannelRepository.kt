package com.august.cupid.repository

import com.august.cupid.model.entity.Channel
import com.august.cupid.model.entity.ChannelType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * 채널 Repository (PostgreSQL)
 * 채팅방 관련 데이터 접근 계층
 */
@Repository
interface ChannelRepository : JpaRepository<Channel, UUID> {

    /**
     * 채널 타입별 조회
     */
    fun findByType(type: ChannelType): List<Channel>

    /**
     * 생성자별 채널 조회
     */
    fun findByCreatorId(creatorId: UUID): List<Channel>

    /**
     * 매칭 ID로 채널 조회
     */
    fun findByMatchId(matchId: UUID): Channel?

    /**
     * 매칭 ID로 채널 존재 여부 확인
     */
    fun existsByMatchId(matchId: UUID): Boolean

    /**
     * 사용자가 참여한 채널들 조회 (ChannelMembers와 조인)
     */
    @Query("""
        SELECT c FROM Channel c 
        JOIN ChannelMembers cm ON c.id = cm.channel.id 
        WHERE cm.user.id = :userId AND cm.isActive = true
        ORDER BY c.updatedAt DESC
    """)
    fun findChannelsByUserId(@Param("userId") userId: UUID): List<Channel>

    /**
     * 두 사용자 간의 1:1 채널 조회
     */
    @Query("""
        SELECT c FROM Channel c 
        JOIN ChannelMembers cm1 ON c.id = cm1.channel.id 
        JOIN ChannelMembers cm2 ON c.id = cm2.channel.id 
        WHERE c.type = 'DIRECT' 
        AND cm1.user.id = :user1Id AND cm1.isActive = true
        AND cm2.user.id = :user2Id AND cm2.isActive = true
    """)
    fun findDirectChannelBetweenUsers(
        @Param("user1Id") user1Id: UUID, 
        @Param("user2Id") user2Id: UUID
    ): Channel?

    /**
     * 채널명으로 검색 (그룹 채널)
     */
    @Query("SELECT c FROM Channel c WHERE c.type = 'GROUP' AND c.name LIKE %:name%")
    fun findGroupChannelsByNameContaining(@Param("name") name: String): List<Channel>

    /**
     * 최근 업데이트된 채널들 조회
     */
    @Query("SELECT c FROM Channel c ORDER BY c.updatedAt DESC")
    fun findRecentlyUpdatedChannels(): List<Channel>

    /**
     * 특정 시간 이후 생성된 채널들 조회
     */
    @Query("SELECT c FROM Channel c WHERE c.createdAt >= :since ORDER BY c.createdAt DESC")
    fun findChannelsCreatedSince(@Param("since") since: java.time.LocalDateTime): List<Channel>

    /**
     * 채널 통계 조회
     */
    @Query("SELECT COUNT(c) FROM Channel c WHERE c.type = :type")
    fun countChannelsByType(@Param("type") type: ChannelType): Long

    @Query("SELECT COUNT(c) FROM Channel c")
    fun countAllChannels(): Long

    /**
     * 사용자가 참여한 채널 개수 조회
     */
    @Query("""
        SELECT COUNT(c) FROM Channel c 
        JOIN ChannelMembers cm ON c.id = cm.channel.id 
        WHERE cm.user.id = :userId AND cm.isActive = true
    """)
    fun countChannelsByUserId(@Param("userId") userId: UUID): Long
}
