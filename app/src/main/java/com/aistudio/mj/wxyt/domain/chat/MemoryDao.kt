package com.aistudio.mj.wxyt.domain.chat

import androidx.room.*

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTop(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE key LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%' ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}
