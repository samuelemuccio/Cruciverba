package com.samuele.crossword.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onGenerate: (rows: Int, cols: Int, mask: Array<BooleanArray>?, words: List<com.samuele.crossword.engine.WordClue>) -> Unit
) {
    val s by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Editor Cruciverba") })
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.saveList() },
                        enabled = s.parsedWords.isNotEmpty() && s.listName.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Salva lista") }
                    Button(
                        onClick = {
                            val mask = if (s.shape == GridShape.CUSTOM) s.mask else null
                            onGenerate(s.rows, s.cols, mask, s.parsedWords)
                        },
                        enabled = s.parsedWords.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Genera") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = s.listName,
                    onValueChange = vm::onListNameChange,
                    label = { Text("Nome lista") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text("Parole e definizioni (una per riga, formato 'parola : definizione')",
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = s.text,
                    onValueChange = vm::onTextChange,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("ROMA : Capitale d'Italia\nMARE : Distesa d'acqua salata") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
                if (s.parseErrors.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    s.parseErrors.forEach {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Parole valide: ${s.parsedWords.size}",
                    style = MaterialTheme.typography.labelMedium)
            }
            item {
                Text("Forma griglia", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.shape == GridShape.RECT,
                        onClick = { vm.onShapeChange(GridShape.RECT) },
                        label = { Text("Rettangolo") }
                    )
                    FilterChip(
                        selected = s.shape == GridShape.SQUARE,
                        onClick = { vm.onShapeChange(GridShape.SQUARE) },
                        label = { Text("Quadrato") }
                    )
                    FilterChip(
                        selected = s.shape == GridShape.CUSTOM,
                        onClick = { vm.onShapeChange(GridShape.CUSTOM) },
                        label = { Text("Custom") }
                    )
                }
            }
            if (s.shape != GridShape.SQUARE) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = s.rows.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { vm.onSizeChange(it, s.cols) } },
                            label = { Text("Righe") },
                            modifier = Modifier.width(120.dp)
                        )
                        OutlinedTextField(
                            value = s.cols.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { vm.onSizeChange(s.rows, it) } },
                            label = { Text("Colonne") },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
            if (s.shape == GridShape.CUSTOM) {
                item {
                    Text("Tocca o trascina per disegnare celle nere/bianche",
                        style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    MaskEditor(
                        rows = s.rows,
                        cols = s.cols,
                        mask = s.mask,
                        onMaskChange = vm::onMaskChange
                    )
                }
            }
            item {
                Text("Liste salvate", style = MaterialTheme.typography.titleSmall)
            }
            items(s.savedLists) { list ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { vm.loadList(list.id) }) { Text("Apri") }
                        IconButton(onClick = { vm.deleteList(list) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina")
                        }
                    }
                }
            }
        }
    }
}
