package com.august.cupid.repository

import com.august.cupid.model.entity.Match
import com.august.cupid.model.entity.MatchStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 매칭 Repository (PostgreSQL)
 * 소개팅 앱의 매칭 관리
 */
@Repository
interface MatchRepository : JpaRepository<Match, UUID> {

    /**
     * 사용자 ID로 매칭 조회 (user1 또는 user2)
     */
    @Query("SELECT m FROM Match m WHERE m.user1.id = :userId OR m.user2.id = :userId")
    fun findByUserId(@Param("userId") userId: UUID): List<Match>

    /**
     * 두 사용자 간의 매칭 조회
     */
    @Query("""
        SELECT m FROM Match m 
        WHERE (m.user1.id = :user1Id AND m.user2.id = :user2Id) 
        OR (m.user1.id = :user2Id AND m.user2.id = :user1Id)
    """)
    fun findMatchBetweenUsers(@Param("user1Id") user1Id: UUID, @Param("user2Id") user2Id: UUID): Match?

    /**
     * 활성 매칭들 조회
     */
    fun findByStatus(status: MatchStatus): List<Match>

    /**
     * 사용자의 활성 매칭들 조회
     */
    @Query("SELECT m FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) AND m.status = :status")
    fun findActiveMatchesByUserId(@Param("userId") userId: UUID, @Param("status") status: MatchStatus): List<Match>

    /**
     * 만료된 매칭들 조회
     */
    @Query("SELECT m FROM Match m WHERE m.status = 'ACTIVE' AND m.expiresAt IS NOT NULL AND m.expiresAt <= :now")
    fun findExpiredMatches(@Param("now") now: LocalDateTime): List<Match>

    /**
     * 매칭 존재 여부 확인
     */
    @Query("""
        SELECT COUNT(m) > 0 FROM Match m 
        WHERE (m.user1.id = :user1Id AND m.user2.id = :user2Id) 
        OR (m.user1.id = :user2Id AND m.user2.id = :user1Id)
        AND m.status = 'ACTIVE'
    """)
    fun existsActiveMatchBetweenUsers(@Param("user1Id") user1Id: UUID, @Param("user2Id") user2Id: UUID): Boolean

    /**
     * 사용자의 매칭 개수 조회
     */
    @Query("SELECT COUNT(m) FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) AND m.status = :status")
    fun countMatchesByUserIdAndStatus(@Param("userId") userId: UUID, @Param("status") status: MatchStatus): Long

    /**
     * 특정 시간 이후 생성된 매칭들 조회
     */
    @Query("SELECT m FROM Match m WHERE m.matchedAt >= :since ORDER BY m.matchedAt DESC")
    fun findMatchesCreatedSince(@Param("since") since: LocalDateTime): List<Match>

    /**
     * 매칭 상태 업데이트
     */
    @Query("UPDATE Match m SET m.status = :status WHERE m.id = :matchId")
    fun updateMatchStatus(@Param("matchId") matchId: UUID, @Param("status") status: MatchStatus): Int

    /**
     * 만료된 매칭들을 만료 상태로 업데이트
     */
    @Query("UPDATE Match m SET m.status = 'EXPIRED' WHERE m.status = 'ACTIVE' AND m.expiresAt IS NOT NULL AND m.expiresAt <= :now")
    fun expireMatches(@Param("now") now: LocalDateTime): Int

    /**
     * 사용자의 최근 매칭 조회
     */
    @Query("""
        SELECT m FROM Match m 
        WHERE (m.user1.id = :userId OR m.user2.id = :userId) 
        ORDER BY m.matchedAt DESC
    """)
    fun findRecentMatchesByUserId(@Param("userId") userId: UUID): List<Match>

    /**
     * 매칭 통계 조회
     */
    @Query("SELECT COUNT(m) FROM Match m WHERE m.status = :status")
    fun countMatchesByStatus(@Param("status") status: MatchStatus): Long

    @Query("SELECT COUNT(m) FROM Match m WHERE m.matchedAt >= :since")
    fun countMatchesSince(@Param("since") since: LocalDateTime): Long
}
