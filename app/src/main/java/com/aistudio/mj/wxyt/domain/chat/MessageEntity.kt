package com.aistudio.mj.wxyt.domain.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant"
    val content: String,
    val timestamp: Long
)
