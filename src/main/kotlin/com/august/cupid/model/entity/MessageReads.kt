package com.august.cupid.model.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 읽음 표시 엔티티 (MongoDB)
 * 사용자별 메시지 읽음 상태 관리
 */
@Document(collection = "message_reads")
@CompoundIndexes(
    CompoundIndex(name = "message_user_unique", def = "{'message_id': 1, 'user_id': 1}", unique = true),
    CompoundIndex(name = "channel_user_read", def = "{'channel_id': 1, 'user_id': 1, 'read_at': -1}")
)
data class MessageReads(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Indexed
    @Field("message_id")
    val messageId: UUID,

    @Indexed
    @Field("channel_id")
    val channelId: UUID,

    @Indexed
    @Field("user_id")
    val userId: UUID,

    @Field("read_at")
    val readAt: LocalDateTime = LocalDateTime.now()
)
