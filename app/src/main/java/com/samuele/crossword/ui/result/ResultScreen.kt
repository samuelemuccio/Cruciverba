package com.samuele.crossword.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samuele.crossword.engine.Direction
import com.samuele.crossword.ui.components.CrosswordGridView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    vm: ResultViewModel,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    val s by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risultato") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Indietro") }
                },
                actions = {
                    IconButton(onClick = { vm.toggleSolution() }) {
                        Icon(
                            if (s.showSolution) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Mostra/nascondi soluzione"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (s.result != null) {
                Surface(tonalElevation = 4.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Text("Esporta")
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            s.loading -> Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
            }
            s.error != null && s.result == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) { Text(s.error!!, color = MaterialTheme.colorScheme.error) }
            s.result != null -> {
                val r = s.result!!
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (s.error != null) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Text(s.error!!, Modifier.padding(12.dp))
                        }
                    }
                    CrosswordGridView(
                        result = r,
                        modifier = Modifier.fillMaxWidth(),
                        showSolution = s.showSolution
                    )
                    Text("Orizzontali", style = MaterialTheme.typography.titleMedium)
                    r.slots.filter { it.direction == Direction.ACROSS && it.number > 0 }
                        .sortedBy { it.number }
                        .forEach { slot ->
                            val clue = r.assignments[slot.id]?.clue ?: "—"
                            Text("${slot.number}. $clue (${slot.length})")
                        }
                    Text("Verticali", style = MaterialTheme.typography.titleMedium)
                    r.slots.filter { it.direction == Direction.DOWN && it.number > 0 }
                        .sortedBy { it.number }
                        .forEach { slot ->
                            val clue = r.assignments[slot.id]?.clue ?: "—"
                            Text("${slot.number}. $clue (${slot.length})")
                        }
                }
            }
        }
    }
}
