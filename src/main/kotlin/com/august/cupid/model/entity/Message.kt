package com.august.cupid.model.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime
import java.util.*

/**
 * 메시지 엔티티 (MongoDB)
 * E2E 암호화된 메시지 데이터 저장
 */
@Document(collection = "messages")
data class Message(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Indexed
    @Field("channel_id")
    val channelId: UUID,

    @Indexed
    @Field("sender_id")
    val senderId: UUID,

    // E2E 암호화된 내용
    @Field("encrypted_content")
    val encryptedContent: String,

    @Field("message_type")
    val messageType: MessageType = MessageType.TEXT,

    @Field("status")
    val status: MessageStatus = MessageStatus.SENT,

    @Field("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Field("updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Field("deleted_at")
    val deletedAt: LocalDateTime? = null,

    // 파일/이미지 메타데이터 (암호화되지 않음)
    @Field("file_metadata")
    val fileMetadata: FileMetadata? = null,

    // 메시지 수정 이력 (E2E 암호화)
    @Field("edit_history")
    val editHistory: List<EditHistory> = emptyList(),

    // 추가 메타데이터
    @Field("metadata")
    val metadata: Map<String, Any>? = null
)

/**
 * 메시지 타입 열거형
 */
enum class MessageType {
    TEXT,   // 텍스트
    IMAGE,  // 이미지
    FILE    // 파일
}

/**
 * 메시지 상태 열거형
 */
enum class MessageStatus {
    SENT,      // 전송됨
    DELIVERED, // 전달됨
    DELETED    // 삭제됨
}

/**
 * 파일 메타데이터
 */
data class FileMetadata(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val encryptedFileUrl: String,  // 암호화된 파일 저장 경로
    val thumbnailUrl: String? = null  // 썸네일 (선택)
)

/**
 * 메시지 수정 이력
 */
data class EditHistory(
    val encryptedContent: String,
    val editedAt: LocalDateTime
)
