package com.aistudio.mj.wxyt.domain.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val category: String = "general",
    val importance: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0
)
