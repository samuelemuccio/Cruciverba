package com.samuele.crossword.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuele.crossword.domain.GenerateCrosswordUseCase
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.SolveOutcome
import com.samuele.crossword.engine.WordClue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class ResultState(
    val loading: Boolean = false,
    val result: CrosswordResult? = null,
    val error: String? = null,
    val showSolution: Boolean = false
)

class ResultViewModel(
    private val useCase: GenerateCrosswordUseCase = GenerateCrosswordUseCase()
) : ViewModel() {

    private val _state = MutableStateFlow(ResultState())
    val state: StateFlow<ResultState> = _state.asStateFlow()

    fun generate(rows: Int, cols: Int, mask: Array<BooleanArray>?, words: List<WordClue>) {
        viewModelScope.launch {
            _state.value = ResultState(loading = true)
            when (val outcome = useCase(rows, cols, mask, words, 30.seconds)) {
                is SolveOutcome.Success -> _state.value = ResultState(result = outcome.result)
                is SolveOutcome.Partial -> _state.value = ResultState(
                    result = outcome.result,
                    error = "Soluzione parziale: ${outcome.result.unsolvedSlotIds.size} slot non risolti"
                )
                SolveOutcome.NoSolution -> _state.value = ResultState(error = "Nessuna soluzione trovata")
                SolveOutcome.Timeout -> _state.value = ResultState(error = "Timeout")
                is SolveOutcome.InvalidInput -> _state.value = ResultState(error = outcome.reason)
            }
        }
    }

    fun toggleSolution() {
        _state.value = _state.value.copy(showSolution = !_state.value.showSolution)
    }
}
