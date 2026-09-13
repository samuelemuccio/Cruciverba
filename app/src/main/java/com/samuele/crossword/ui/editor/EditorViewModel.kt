package com.samuele.crossword.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuele.crossword.data.db.WordListEntity
import com.samuele.crossword.data.repository.WordListRepository
import com.samuele.crossword.domain.WordListParser
import com.samuele.crossword.engine.WordClue
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class GridShape { RECT, SQUARE, CUSTOM }

data class EditorState(
    val text: String = "",
    val parsedWords: List<WordClue> = emptyList(),
    val parseErrors: List<String> = emptyList(),
    val shape: GridShape = GridShape.SQUARE,
    val rows: Int = 11,
    val cols: Int = 11,
    val mask: Array<BooleanArray> = Array(11) { BooleanArray(11) { true } },
    val listName: String = "",
    val savedLists: List<WordListEntity> = emptyList()
)

class EditorViewModel(private val repo: WordListRepository) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeLists().collect { lists ->
                _state.update { it.copy(savedLists = lists) }
            }
        }
    }

    fun onTextChange(t: String) {
        val parsed = WordListParser.parse(t)
        _state.update {
            it.copy(text = t, parsedWords = parsed.words, parseErrors = parsed.errors)
        }
    }

    fun onShapeChange(s: GridShape) {
        _state.update {
            when (s) {
                GridShape.SQUARE -> it.copy(shape = s, rows = it.cols,
                    mask = Array(it.cols) { _ -> BooleanArray(it.cols) { true } })
                else -> it.copy(shape = s)
            }
        }
    }

    fun onSizeChange(rows: Int, cols: Int) {
        _state.update {
            it.copy(rows = rows, cols = cols,
                mask = Array(rows) { _ -> BooleanArray(cols) { true } })
        }
    }

    fun onMaskChange(mask: Array<BooleanArray>) {
        _state.update { it.copy(mask = mask) }
    }

    fun onListNameChange(name: String) = _state.update { it.copy(listName = name) }

    fun saveList() {
        val s = _state.value
        if (s.parsedWords.isEmpty() || s.listName.isBlank()) return
        viewModelScope.launch {
            repo.createList(s.listName, s.parsedWords)
        }
    }

    fun loadList(id: Long) {
        viewModelScope.launch {
            val list = repo.getList(id) ?: return@launch
            val words = repo.loadWords(id)
            val text = words.joinToString("\n") { "${it.word} : ${it.clue}" }
            _state.update { it.copy(listName = list.name, text = text, parsedWords = words) }
        }
    }

    fun deleteList(list: WordListEntity) {
        viewModelScope.launch { repo.delete(list) }
    }
}
