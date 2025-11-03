package com.august.cupid.auth.dto

import com.august.cupid.model.entity.User
import java.util.UUID

/**
 * 외부 모듈에 노출되는 User 정보 DTO
 * 민감한 정보 제외 (password, 내부 메타데이터 등)
 *
 * Chat, Encryption 등 다른 모듈에서 User 정보가 필요할 때 사용
 */
data class UserInfoDto(
    val id: UUID,
    val username: String,
    val email: String?,
    val profileImageUrl: String?,
    val isActive: Boolean
) {
    companion object {
        /**
         * User 엔티티로부터 DTO 생성
         */
        fun from(user: User): UserInfoDto {
            return UserInfoDto(
                id = user.id!!,
                username = user.username,
                email = user.email,
                profileImageUrl = user.profileImageUrl,
                isActive = user.isActive
            )
        }

        /**
         * User 엔티티 리스트로부터 Map 생성
         * Key: userId, Value: UserInfoDto
         */
        fun mapFrom(users: List<User>): Map<UUID, UserInfoDto> {
            return users.associate { user ->
                user.id!! to from(user)
            }
        }
    }
}
