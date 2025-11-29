package com.august.cupid.repository

import com.august.cupid.model.entity.Message
import com.august.cupid.model.entity.MessageStatus
import com.august.cupid.model.entity.MessageType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import com.mongodb.client.result.UpdateResult
import java.util.*

/**
 * MessageRepositoryImpl 단위 테스트
 *
 * 검증 항목:
 * - MongoTemplate을 사용한 업데이트 작업
 * - soft delete 동작
 * - 수정 이력 저장
 */
class MessageRepositoryCustomTest {

    private lateinit var repository: MessageRepositoryImpl
    private lateinit var mongoTemplate: MongoTemplate

    private val testMessageId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mongoTemplate = mock(MongoTemplate::class.java)
        repository = MessageRepositoryImpl(mongoTemplate)
    }

    // ============================================
    // updateMessageStatus 테스트
    // ============================================

    @Test
    fun `updateMessageStatus should return true when message is updated`() {
        // Given
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(1L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            any(Update::class.java),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        val result = repository.updateMessageStatus(testMessageId, MessageStatus.DELIVERED)

        // Then
        assertTrue(result)
        verify(mongoTemplate).updateFirst(any(Query::class.java), any(Update::class.java), eq(Message::class.java))
    }

    @Test
    fun `updateMessageStatus should return false when message not found`() {
        // Given
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(0L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            any(Update::class.java),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        val result = repository.updateMessageStatus(testMessageId, MessageStatus.DELIVERED)

        // Then
        assertFalse(result)
    }

    // ============================================
    // softDeleteMessage 테스트
    // ============================================

    @Test
    fun `softDeleteMessage should return true when message is deleted`() {
        // Given
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(1L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            any(Update::class.java),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        val result = repository.softDeleteMessage(testMessageId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `softDeleteMessage should return false when message not found`() {
        // Given
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(0L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            any(Update::class.java),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        val result = repository.softDeleteMessage(testMessageId)

        // Then
        assertFalse(result)
    }

    @Test
    fun `softDeleteMessage should set status to DELETED and set deletedAt`() {
        // Given
        val updateCaptor = ArgumentCaptor.forClass(Update::class.java)
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(1L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            updateCaptor.capture(),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        repository.softDeleteMessage(testMessageId)

        // Then
        val capturedUpdate = updateCaptor.value
        val updateObject = capturedUpdate.updateObject
        val setValues = updateObject["\$set"] as? org.bson.Document

        assertNotNull(setValues)
        assertEquals(MessageStatus.DELETED, setValues!!["status"])
        assertNotNull(setValues["deleted_at"])
        assertNotNull(setValues["updated_at"])
    }

    // ============================================
    // updateMessageContent 테스트
    // ============================================

    @Test
    fun `updateMessageContent should return true when content is updated`() {
        // Given
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(1L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            any(Update::class.java),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        val result = repository.updateMessageContent(
            testMessageId,
            "new encrypted content",
            "old encrypted content"
        )

        // Then
        assertTrue(result)
    }

    @Test
    fun `updateMessageContent should return false when message not found`() {
        // Given
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(0L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            any(Update::class.java),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        val result = repository.updateMessageContent(
            testMessageId,
            "new content",
            "old content"
        )

        // Then
        assertFalse(result)
    }

    @Test
    fun `updateMessageContent should update content and push edit history`() {
        // Given
        val updateCaptor = ArgumentCaptor.forClass(Update::class.java)
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(1L)
        `when`(mongoTemplate.updateFirst(
            any(Query::class.java),
            updateCaptor.capture(),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        repository.updateMessageContent(
            testMessageId,
            "new encrypted content",
            "old encrypted content"
        )

        // Then
        val capturedUpdate = updateCaptor.value
        val updateObject = capturedUpdate.updateObject

        // Check $set operations
        val setValues = updateObject["\$set"] as? org.bson.Document
        assertNotNull(setValues)
        assertEquals("new encrypted content", setValues!!["encrypted_content"])
        assertNotNull(setValues["updated_at"])

        // Check $push operation (edit history)
        val pushValues = updateObject["\$push"] as? org.bson.Document
        assertNotNull(pushValues)
        assertNotNull(pushValues!!["edit_history"])
    }

    // ============================================
    // Query 구성 검증
    // ============================================

    @Test
    fun `operations should query by message id`() {
        // Given
        val queryCaptor = ArgumentCaptor.forClass(Query::class.java)
        val updateResult = mock(UpdateResult::class.java)
        `when`(updateResult.modifiedCount).thenReturn(1L)
        `when`(mongoTemplate.updateFirst(
            queryCaptor.capture(),
            any(Update::class.java),
            eq(Message::class.java)
        )).thenReturn(updateResult)

        // When
        repository.updateMessageStatus(testMessageId, MessageStatus.DELIVERED)

        // Then
        val capturedQuery = queryCaptor.value
        val queryObject = capturedQuery.queryObject

        assertEquals(testMessageId, queryObject["_id"])
    }
}
