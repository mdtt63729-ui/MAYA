package com.aistudio.mj.wxyt.domain.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ConversationEntity::class, MessageEntity::class, MemoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS memories (id TEXT NOT NULL PRIMARY KEY, key TEXT NOT NULL, value TEXT NOT NULL, category TEXT NOT NULL DEFAULT 'general', importance INTEGER NOT NULL DEFAULT 5, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, accessCount INTEGER NOT NULL DEFAULT 0)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memories_key ON memories(key)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mj_chat_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
