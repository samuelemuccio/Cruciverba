package com.samuele.crossword.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WordListEntity::class, WordEntity::class, CrosswordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CrosswordDatabase : RoomDatabase() {
    abstract fun wordListDao(): WordListDao
    abstract fun crosswordDao(): CrosswordDao

    companion object {
        @Volatile private var INSTANCE: CrosswordDatabase? = null
        fun get(context: Context): CrosswordDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CrosswordDatabase::class.java,
                    "crossword.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
