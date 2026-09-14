package com.samuele.crossword.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuele.crossword.data.db.CrosswordEntity
import com.samuele.crossword.data.repository.CrosswordRepository
import com.samuele.crossword.engine.BuildReport
import com.samuele.crossword.engine.CrosswordBuilder
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.CrosswordTheme
import com.samuele.crossword.engine.WordClue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ResultViewModel(
    private val builder: CrosswordBuilder = CrosswordBuilder(),
    private val crosswordRepo: CrosswordRepository? = null
) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val reports: List<BuildReport> = emptyList(),
        val variants: List<CrosswordResult> = emptyList(),
        val index: Int = 0,
        val theme: CrosswordTheme = CrosswordTheme.Paper,
        val cellScale: Float = 1.0f,
        val showSolution: Boolean = false,
        val error: String? = null,
        val savedToast: String? = null
    ) {
        val current: CrosswordResult? get() = variants.getOrNull(index)
        val currentReport: BuildReport? get() = reports.getOrNull(index)
        val hasPrev get() = index > 0
        val hasNext get() = index < variants.lastIndex
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun generate(words: List<WordClue>, mysteryWord: WordClue? = null) {
        viewModelScope.launch {
            _state.value = State(loading = true, theme = _state.value.theme)
            val reports = builder.buildVariationsStrict(words, mysteryWord = mysteryWord, maxSolutions = 30, requireAllWords = true)
            val validResults = reports.mapNotNull { it.result }

            _state.value = if (validResults.isEmpty()) {
                val errorMsg = if (mysteryWord != null) {
                    "Impossibile formare incroci con la parola misteriosa '${mysteryWord.word}'.\nSuggerimento: assicurati che le altre parole abbiano lettere in comune con la parola misteriosa."
                } else {
                    "Nessuna combinazione possibile trovata.\nSuggerimento: per formare incroci, le parole devono avere almeno qualche lettera in comune (es. vocali A, E, I, O o consonanti frequenti)."
                }
                State(
                    error = errorMsg,
                    theme = _state.value.theme
                )
            } else {
                State(
                    reports = reports,
                    variants = validResults,
                    index = 0,
                    theme = _state.value.theme
                )
            }
        }
    }

    fun prev() = _state.update { if (it.hasPrev) it.copy(index = it.index - 1) else it }
    fun next() = _state.update { if (it.hasNext) it.copy(index = it.index + 1) else it }
    fun jumpTo(i: Int) = _state.update { it.copy(index = i.coerceIn(0, it.variants.lastIndex)) }
    fun changeTheme(t: CrosswordTheme) = _state.update { it.copy(theme = t.copy(fontFamily = it.theme.fontFamily)) }
    fun changeFont(font: String) = _state.update { it.copy(theme = it.theme.copy(fontFamily = font)) }
    fun changeCellScale(scale: Float) = _state.update { it.copy(cellScale = scale) }
    fun toggleSolution() = _state.update { it.copy(showSolution = !it.showSolution) }

    fun saveCurrent() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            if (crosswordRepo != null) {
                val entity = CrosswordEntity(
                    listId = 0,
                    name = "Cruciverba (${cur.rows}x${cur.cols})",
                    rows = cur.rows,
                    cols = cur.cols,
                    maskJson = cur.whiteCells.map { row -> row.map { if (it) 1 else 0 } }.toString(),
                    lettersJson = cur.letters.map { row -> String(row) }.toString(),
                    slotsJson = cur.slots.map { "${it.id}:${it.number}:${it.direction}:${it.row}:${it.col}" }.toString()
                )
                crosswordRepo.save(entity)
                _state.update { it.copy(savedToast = "Cruciverba salvato con successo!") }
            } else {
                _state.update { it.copy(savedToast = "Cruciverba pronto per essere esportato!") }
            }
        }
    }

    fun clearToast() = _state.update { it.copy(savedToast = null) }
}
