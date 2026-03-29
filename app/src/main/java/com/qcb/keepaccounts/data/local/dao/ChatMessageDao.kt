package com.qcb.keepaccounts.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): ChatMessageEntity?

    @Query("UPDATE chat_messages SET content = :content, isReceipt = :isReceipt, transactionId = :transactionId WHERE id = :id")
    suspend fun updateMessage(
        id: Long,
        content: String,
        isReceipt: Boolean,
        transactionId: Long?,
    )

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<Long>)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()
}
