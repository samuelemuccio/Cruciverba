package com.samuele.crossword.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuele.crossword.data.db.WordListEntity
import com.samuele.crossword.data.repository.WordListRepository
import com.samuele.crossword.domain.AnalysisResult
import com.samuele.crossword.domain.WordListAnalyzer
import com.samuele.crossword.engine.WordClue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class WordEntry(
    val id: String = UUID.randomUUID().toString(),
    val word: String,
    val clue: String
)

data class EditorState(
    val words: List<WordEntry> = listOf(
        WordEntry(word = "ROMA", clue = "Capitale d'Italia"),
        WordEntry(word = "MARE", clue = "Distesa d'acqua salata"),
        WordEntry(word = "SOLE", clue = "La stella al centro del nostro sistema"),
        WordEntry(word = "LUNA", clue = "Il nostro satellite naturale"),
        WordEntry(word = "ARTE", clue = "Espressione della creatività umana"),
        WordEntry(word = "PIZZA", clue = "Famoso piatto della tradizione napoletana"),
        WordEntry(word = "MUSICA", clue = "Arte dei suoni e del ritmo"),
        WordEntry(word = "GATTO", clue = "Felino domestico agile e curioso"),
        WordEntry(word = "CASA", clue = "Dimora in cui si abita"),
        WordEntry(word = "LIBRO", clue = "Volume stampato da leggere")
    ),
    val draftWord: String = "",
    val draftClue: String = "",
    val listName: String = "",
    val savedLists: List<WordListEntity> = emptyList(),
    val analysis: AnalysisResult = AnalysisResult(10, emptyList(), true)
)

class EditorViewModel(private val repo: WordListRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        EditorState().let { initial ->
            initial.copy(analysis = computeAnalysis(initial.words))
        }
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeLists().collect { lists ->
                _state.update { it.copy(savedLists = lists) }
            }
        }
    }

    private fun computeAnalysis(words: List<WordEntry>): AnalysisResult {
        return WordListAnalyzer.analyzeConnectability(
            words.map { WordClue(it.word, it.clue) }
        )
    }

    fun onDraftWordChange(s: String) = _state.update {
        it.copy(draftWord = s.uppercase().filter { c -> c.isLetter() })
    }

    fun onDraftClueChange(s: String) = _state.update {
        it.copy(draftClue = s)
    }

    fun addWord() {
        val s = _state.value
        val cleanWord = s.draftWord.trim()
        val cleanClue = s.draftClue.trim()
        if (cleanWord.length < 2 || cleanClue.isBlank()) return
        val newWords = s.words + WordEntry(word = cleanWord, clue = cleanClue)
        _state.update {
            it.copy(
                words = newWords,
                draftWord = "",
                draftClue = "",
                analysis = computeAnalysis(newWords)
            )
        }
    }

    fun removeWord(id: String) {
        val newWords = _state.value.words.filterNot { w -> w.id == id }
        _state.update {
            it.copy(words = newWords, analysis = computeAnalysis(newWords))
        }
    }

    fun clearAllWords() {
        _state.update {
            it.copy(words = emptyList(), analysis = computeAnalysis(emptyList()))
        }
    }

    fun onListNameChange(name: String) = _state.update {
        it.copy(listName = name)
    }

    fun saveList() {
        val s = _state.value
        if (s.words.isEmpty() || s.listName.isBlank()) return
        viewModelScope.launch {
            repo.createList(
                s.listName.trim(),
                s.words.map { WordClue(it.word, it.clue) }
            )
            _state.update { it.copy(listName = "") }
        }
    }

    fun loadList(id: Long) {
        viewModelScope.launch {
            val list = repo.getList(id) ?: return@launch
            val words = repo.loadWords(id)
            val newWords = words.map { w -> WordEntry(word = w.word, clue = w.clue) }
            _state.update {
                it.copy(
                    listName = list.name,
                    words = newWords,
                    analysis = computeAnalysis(newWords)
                )
            }
        }
    }

    fun deleteList(list: WordListEntity) {
        viewModelScope.launch { repo.delete(list) }
    }
}
