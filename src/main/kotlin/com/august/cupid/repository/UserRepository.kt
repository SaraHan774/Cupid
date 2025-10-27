package com.august.cupid.repository

import com.august.cupid.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * 사용자 Repository (PostgreSQL)
 * 사용자 관련 데이터 접근 계층
 */
@Repository
interface UserRepository : JpaRepository<User, UUID> {

    /**
     * 사용자명으로 사용자 조회
     */
    fun findByUsername(username: String): User?

    /**
     * 이메일로 사용자 조회
     */
    fun findByEmail(email: String): User?

    /**
     * 사용자명 존재 여부 확인
     */
    fun existsByUsername(username: String): Boolean

    /**
     * 이메일 존재 여부 확인
     */
    fun existsByEmail(email: String): Boolean

    /**
     * 활성 사용자만 조회
     */
    fun findByIsActiveTrue(): List<User>

    /**
     * 사용자명으로 활성 사용자 조회
     */
    fun findByUsernameAndIsActiveTrue(username: String): User?

    /**
     * 이메일로 활성 사용자 조회
     */
    fun findByEmailAndIsActiveTrue(email: String): User?

    /**
     * 최근 접속한 사용자들 조회
     */
    @Query("SELECT u FROM User u WHERE u.lastSeenAt IS NOT NULL ORDER BY u.lastSeenAt DESC")
    fun findRecentlyActiveUsers(): List<User>

    /**
     * 특정 시간 이후 접속한 사용자들 조회
     */
    @Query("SELECT u FROM User u WHERE u.lastSeenAt >= :since ORDER BY u.lastSeenAt DESC")
    fun findUsersActiveSince(@Param("since") since: java.time.LocalDateTime): List<User>

    /**
     * 사용자명 검색 (부분 일치)
     */
    @Query("SELECT u FROM User u WHERE u.username LIKE %:username% AND u.isActive = true")
    fun findByUsernameContaining(@Param("username") username: String): List<User>

    /**
     * 프로필 이미지가 있는 사용자들 조회
     */
    @Query("SELECT u FROM User u WHERE u.profileImageUrl IS NOT NULL AND u.isActive = true")
    fun findUsersWithProfileImages(): List<User>

    /**
     * 사용자 통계 조회
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    fun countActiveUsers(): Long

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since AND u.isActive = true")
    fun countNewUsersSince(@Param("since") since: java.time.LocalDateTime): Long
}
