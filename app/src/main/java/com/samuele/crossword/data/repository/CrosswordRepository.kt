package com.samuele.crossword.data.repository

import com.samuele.crossword.data.db.CrosswordDao
import com.samuele.crossword.data.db.CrosswordEntity
import kotlinx.coroutines.flow.Flow

class CrosswordRepository(private val dao: CrosswordDao) {
    fun observeAll(): Flow<List<CrosswordEntity>> = dao.observeAll()
    suspend fun save(entity: CrosswordEntity): Long = dao.insert(entity)
    suspend fun get(id: Long) = dao.getById(id)
    suspend fun delete(entity: CrosswordEntity) = dao.delete(entity)
}
