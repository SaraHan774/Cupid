package com.august.cupid.repository

import com.august.cupid.model.entity.EditHistory
import com.august.cupid.model.entity.Message
import com.august.cupid.model.entity.MessageStatus
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 Repository 커스텀 구현
 * MongoTemplate을 사용한 복잡한 업데이트 작업 구현
 */
@Repository
class MessageRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : MessageRepositoryCustom {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun updateMessageStatus(messageId: UUID, status: MessageStatus): Boolean {
        val query = Query(Criteria.where("_id").`is`(messageId))
        val update = Update()
            .set("status", status)
            .set("updated_at", LocalDateTime.now())

        val result = mongoTemplate.updateFirst(query, update, Message::class.java)

        if (result.modifiedCount > 0) {
            logger.debug("메시지 상태 업데이트 완료: messageId={}, status={}", messageId, status)
        } else {
            logger.warn("메시지 상태 업데이트 실패 (메시지 없음): messageId={}", messageId)
        }

        return result.modifiedCount > 0
    }

    override fun softDeleteMessage(messageId: UUID): Boolean {
        val now = LocalDateTime.now()
        val query = Query(Criteria.where("_id").`is`(messageId))
        val update = Update()
            .set("status", MessageStatus.DELETED)
            .set("deleted_at", now)
            .set("updated_at", now)

        val result = mongoTemplate.updateFirst(query, update, Message::class.java)

        if (result.modifiedCount > 0) {
            logger.debug("메시지 삭제(soft) 완료: messageId={}", messageId)
        } else {
            logger.warn("메시지 삭제 실패 (메시지 없음): messageId={}", messageId)
        }

        return result.modifiedCount > 0
    }

    override fun updateMessageContent(
        messageId: UUID,
        newEncryptedContent: String,
        previousContent: String
    ): Boolean {
        val now = LocalDateTime.now()
        val editHistory = EditHistory(
            encryptedContent = previousContent,
            editedAt = now
        )

        val query = Query(Criteria.where("_id").`is`(messageId))
        val update = Update()
            .set("encrypted_content", newEncryptedContent)
            .set("updated_at", now)
            .push("edit_history", editHistory)

        val result = mongoTemplate.updateFirst(query, update, Message::class.java)

        if (result.modifiedCount > 0) {
            logger.debug("메시지 내용 업데이트 완료: messageId={}", messageId)
        } else {
            logger.warn("메시지 내용 업데이트 실패 (메시지 없음): messageId={}", messageId)
        }

        return result.modifiedCount > 0
    }
}
