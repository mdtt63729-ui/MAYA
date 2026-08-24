package com.aistudio.mj.wxyt.domain.chat

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT DISTINCT c.* FROM conversations c LEFT JOIN messages m ON c.id = m.conversationId WHERE c.title LIKE '%' || :query || '%' OR m.content LIKE '%' || :query || '%' ORDER BY c.updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, content: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)
    
    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: String): ConversationEntity?
}
