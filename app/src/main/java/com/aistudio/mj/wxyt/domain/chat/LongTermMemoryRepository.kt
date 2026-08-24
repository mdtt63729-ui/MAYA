package com.aistudio.mj.wxyt.domain.chat

import android.content.Context
import java.util.UUID

/** Persistent, on-device long-term memory for MAYA.
 * Stores explicit user facts/preferences locally and retrieves only relevant memories.
 */
class LongTermMemoryRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).memoryDao()

    suspend fun remember(key: String, value: String, category: String = "general", importance: Int = 7) {
        val normalizedKey = key.trim().lowercase()
        val existing = dao.search(normalizedKey, 1).firstOrNull { it.key == normalizedKey }
        dao.upsert(
            MemoryEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                key = normalizedKey,
                value = value.trim(),
                category = category,
                importance = importance.coerceIn(1, 10),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                accessCount = existing?.accessCount ?: 0
            )
        )
    }

    suspend fun relevantContext(query: String, limit: Int = 10): String {
        val words = query.lowercase().split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .take(6)
        val results = linkedMapOf<String, MemoryEntity>()
        dao.getTop(8).forEach { results[it.id] = it }
        words.forEach { word -> dao.search(word, 4).forEach { results[it.id] = it } }
        return results.values.take(limit).joinToString("\n") { "- ${it.key}: ${it.value}" }
    }

    suspend fun clear() = dao.clearAll()
}
