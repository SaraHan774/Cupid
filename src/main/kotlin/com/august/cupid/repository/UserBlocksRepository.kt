package com.august.cupid.repository

import com.august.cupid.model.entity.UserBlocks
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * 사용자 차단 Repository (PostgreSQL)
 * 사용자 간 차단 관계 관리
 */
@Repository
interface UserBlocksRepository : JpaRepository<UserBlocks, UUID> {

    /**
     * 차단한 사용자 ID로 차단 목록 조회
     */
    fun findByBlockerId(blockerId: UUID): List<UserBlocks>

    /**
     * 차단당한 사용자 ID로 차단 목록 조회
     */
    fun findByBlockedId(blockedId: UUID): List<UserBlocks>

    /**
     * 차단 관계 조회
     */
    fun findByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): UserBlocks?

    /**
     * 차단 관계 존재 여부 확인
     */
    fun existsByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Boolean

    /**
     * 사용자가 차단한 사용자 수 조회
     */
    fun countByBlockerId(blockerId: UUID): Long

    /**
     * 사용자를 차단한 사용자 수 조회
     */
    fun countByBlockedId(blockedId: UUID): Long

    /**
     * 두 사용자 간 차단 관계 확인 (양방향)
     */
    @Query("""
        SELECT COUNT(ub) > 0 FROM UserBlocks ub 
        WHERE (ub.blocker.id = :user1Id AND ub.blocked.id = :user2Id) 
        OR (ub.blocker.id = :user2Id AND ub.blocked.id = :user1Id)
    """)
    fun existsBlockBetweenUsers(@Param("user1Id") user1Id: UUID, @Param("user2Id") user2Id: UUID): Boolean

    /**
     * 사용자가 차단한 사용자들 조회 (사용자 정보 포함)
     */
    @Query("""
        SELECT ub FROM UserBlocks ub 
        JOIN FETCH ub.blocked 
        WHERE ub.blocker.id = :blockerId 
        ORDER BY ub.createdAt DESC
    """)
    fun findBlockedUsersWithDetails(@Param("blockerId") blockerId: UUID): List<UserBlocks>

    /**
     * 사용자를 차단한 사용자들 조회 (사용자 정보 포함)
     */
    @Query("""
        SELECT ub FROM UserBlocks ub 
        JOIN FETCH ub.blocker 
        WHERE ub.blocked.id = :blockedId 
        ORDER BY ub.createdAt DESC
    """)
    fun findBlockingUsersWithDetails(@Param("blockedId") blockedId: UUID): List<UserBlocks>

    /**
     * 차단 관계 삭제
     */
    fun deleteByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Long

    /**
     * 사용자가 차단한 모든 관계 삭제
     */
    fun deleteByBlockerId(blockerId: UUID): Long

    /**
     * 사용자를 차단한 모든 관계 삭제
     */
    fun deleteByBlockedId(blockedId: UUID): Long

    /**
     * 차단 통계 조회
     */
    @Query("SELECT COUNT(ub) FROM UserBlocks ub")
    fun countAllBlocks(): Long

    /**
     * 특정 시간 이후의 차단들 조회
     */
    @Query("SELECT ub FROM UserBlocks ub WHERE ub.createdAt >= :since ORDER BY ub.createdAt DESC")
    fun findRecentBlocks(@Param("since") since: java.time.LocalDateTime): List<UserBlocks>

    /**
     * 사용자별 차단 통계 조회
     */
    @Query("""
        SELECT ub.blocker.id, COUNT(ub) 
        FROM UserBlocks ub 
        GROUP BY ub.blocker.id 
        ORDER BY COUNT(ub) DESC
    """)
    fun findBlockingStatistics(): List<Array<Any>>

    /**
     * 차단당한 사용자별 통계 조회
     */
    @Query("""
        SELECT ub.blocked.id, COUNT(ub) 
        FROM UserBlocks ub 
        GROUP BY ub.blocked.id 
        ORDER BY COUNT(ub) DESC
    """)
    fun findBlockedStatistics(): List<Array<Any>>
}
