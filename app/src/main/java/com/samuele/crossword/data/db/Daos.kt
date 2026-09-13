package com.samuele.crossword.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordListDao {
    @Query("SELECT * FROM word_lists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WordListEntity>>

    @Query("SELECT * FROM word_lists WHERE id = :id")
    suspend fun getById(id: Long): WordListEntity?

    @Insert suspend fun insert(list: WordListEntity): Long
    @Delete suspend fun delete(list: WordListEntity)
    @Update suspend fun update(list: WordListEntity)

    @Query("SELECT * FROM words WHERE listId = :listId ORDER BY id ASC")
    suspend fun wordsOf(listId: Long): List<WordEntity>

    @Insert suspend fun insertWords(words: List<WordEntity>)
    @Query("DELETE FROM words WHERE listId = :listId")
    suspend fun clearWords(listId: Long)
}

@Dao
interface CrosswordDao {
    @Query("SELECT * FROM crosswords ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CrosswordEntity>>

    @Insert suspend fun insert(c: CrosswordEntity): Long
    @Query("SELECT * FROM crosswords WHERE id = :id")
    suspend fun getById(id: Long): CrosswordEntity?
    @Delete suspend fun delete(c: CrosswordEntity)
}
