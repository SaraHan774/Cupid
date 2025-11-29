package com.august.cupid.repository

import com.august.cupid.model.entity.EditHistory
import com.august.cupid.model.entity.MessageStatus
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 Repository 커스텀 인터페이스
 * MongoTemplate을 사용한 복잡한 업데이트 작업 정의
 */
interface MessageRepositoryCustom {

    /**
     * 메시지 상태 업데이트
     *
     * @param messageId 메시지 ID
     * @param status 새로운 상태
     * @return 업데이트 성공 여부
     */
    fun updateMessageStatus(messageId: UUID, status: MessageStatus): Boolean

    /**
     * 메시지 삭제 (soft delete)
     *
     * @param messageId 메시지 ID
     * @return 삭제 성공 여부
     */
    fun softDeleteMessage(messageId: UUID): Boolean

    /**
     * 메시지 내용 업데이트 (수정)
     *
     * @param messageId 메시지 ID
     * @param newEncryptedContent 새로운 암호화된 내용
     * @param previousContent 이전 내용 (수정 이력용)
     * @return 업데이트 성공 여부
     */
    fun updateMessageContent(
        messageId: UUID,
        newEncryptedContent: String,
        previousContent: String
    ): Boolean
}
