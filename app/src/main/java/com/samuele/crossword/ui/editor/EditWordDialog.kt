package com.samuele.crossword.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuele.crossword.engine.WordClue
import com.samuele.crossword.ui.theme.IosIndigo

@Composable
fun EditWordDialog(
    wordEntry: WordEntry,
    onDismiss: () -> Unit,
    onConfirm: (newWord: String, newClue: String) -> Unit
) {
    var word by remember { mutableStateOf(wordEntry.word) }
    var clue by remember { mutableStateOf(wordEntry.clue) }

    val cleanWord = remember(word) { WordClue.normalize(word) }
    val isValid = cleanWord.length >= 2 && clue.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Modifica Parola",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("Parola") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = clue,
                    onValueChange = { clue = it },
                    label = { Text("Definizione") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (cleanWord.isNotEmpty()) {
                    Text(
                        text = "Lettere: ${cleanWord.length} (${cleanWord})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(cleanWord, clue.trim()) },
                enabled = isValid,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosIndigo)
            ) {
                Text("Salva", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
