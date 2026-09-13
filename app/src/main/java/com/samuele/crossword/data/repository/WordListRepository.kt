package com.samuele.crossword.data.repository

import com.samuele.crossword.data.db.*
import com.samuele.crossword.engine.WordClue
import kotlinx.coroutines.flow.Flow

class WordListRepository(private val dao: WordListDao) {
    fun observeLists(): Flow<List<WordListEntity>> = dao.observeAll()

    suspend fun createList(name: String, words: List<WordClue>): Long {
        val id = dao.insert(WordListEntity(name = name))
        dao.insertWords(words.map { WordEntity(listId = id, word = it.word, clue = it.clue) })
        return id
    }

    suspend fun updateList(id: Long, name: String, words: List<WordClue>) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name))
        dao.clearWords(id)
        dao.insertWords(words.map { WordEntity(listId = id, word = it.word, clue = it.clue) })
    }

    suspend fun loadWords(id: Long): List<WordClue> =
        dao.wordsOf(id).map { WordClue(it.word, it.clue) }

    suspend fun delete(list: WordListEntity) = dao.delete(list)
    suspend fun getList(id: Long) = dao.getById(id)
}
