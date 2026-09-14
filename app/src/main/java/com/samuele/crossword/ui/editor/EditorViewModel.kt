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
    val analysis: AnalysisResult = AnalysisResult(10, emptyList(), true),
    val editingWord: WordEntry? = null,
    val toastMessage: String? = null
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
        it.copy(draftWord = WordClue.normalize(s))
    }

    fun onDraftClueChange(s: String) = _state.update {
        it.copy(draftClue = s)
    }

    fun addWord() {
        val s = _state.value
        val cleanWord = WordClue.normalize(s.draftWord)
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

    fun moveWord(fromIndex: Int, toIndex: Int) {
        val current = _state.value.words.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _state.update {
            it.copy(words = current, analysis = computeAnalysis(current))
        }
    }

    fun setEditingWord(entry: WordEntry?) = _state.update {
        it.copy(editingWord = entry)
    }

    fun updateWord(id: String, newWord: String, newClue: String) {
        val cleanWord = WordClue.normalize(newWord)
        val cleanClue = newClue.trim()
        if (cleanWord.length < 2 || cleanClue.isBlank()) return
        val newWords = _state.value.words.map {
            if (it.id == id) it.copy(word = cleanWord, clue = cleanClue) else it
        }
        _state.update {
            it.copy(
                words = newWords,
                analysis = computeAnalysis(newWords),
                editingWord = null
            )
        }
    }

    fun importWords(newWords: List<WordClue>, replaceExisting: Boolean) {
        if (newWords.isEmpty()) return
        val entries = newWords.map { WordEntry(word = it.normalized, clue = it.clue) }
        val merged = if (replaceExisting) entries else (_state.value.words + entries)
        _state.update {
            it.copy(
                words = merged,
                analysis = computeAnalysis(merged),
                toastMessage = "Importate con successo ${newWords.size} parole!"
            )
        }
    }

    fun shuffleWords() {
        val shuffled = _state.value.words.shuffled()
        _state.update {
            it.copy(words = shuffled, analysis = computeAnalysis(shuffled))
        }
    }

    fun removeWord(id: String) {
        val newWords = _state.value.words.filterNot { w -> w.id == id }
        _state.update {
            it.copy(words = newWords, analysis = computeAnalysis(newWords))
        }
    }

    fun restoreWord(index: Int, entry: WordEntry) {
        val current = _state.value.words.toMutableList()
        val targetIndex = index.coerceIn(0, current.size)
        current.add(targetIndex, entry)
        _state.update {
            it.copy(words = current, analysis = computeAnalysis(current))
        }
    }

    fun clearAllWords() {
        _state.update {
            it.copy(words = emptyList(), analysis = computeAnalysis(emptyList()))
        }
    }

    fun clearToast() = _state.update { it.copy(toastMessage = null) }

    fun showToast(msg: String) = _state.update { it.copy(toastMessage = msg) }

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
            _state.update {
                it.copy(
                    listName = "",
                    toastMessage = "Lista '${s.listName.trim()}' salvata!"
                )
            }
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
                    analysis = computeAnalysis(newWords),
                    toastMessage = "Caricata lista '${list.name}' (${newWords.size} parole)"
                )
            }
        }
    }

    fun deleteList(list: WordListEntity) {
        viewModelScope.launch {
            repo.delete(list)
            _state.update { it.copy(toastMessage = "Lista '${list.name}' eliminata") }
        }
    }
}
