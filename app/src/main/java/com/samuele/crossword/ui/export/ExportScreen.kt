package com.samuele.crossword.ui.export

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.export.ExportFormat
import com.samuele.crossword.export.ExportManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(result: CrosswordResult, onBack: () -> Unit) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var scale by remember { mutableStateOf(4f) }
    var includeSolutions by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("cruciverba") }
    var lastFile by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Esporta") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Indietro") } })
    }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Nome file") }, modifier = Modifier.fillMaxWidth())

            Text("Formato", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(selected = format == f, onClick = { format = f },
                        label = { Text(f.name) })
                }
            }

            if (format == ExportFormat.PNG) {
                Text("Scala PNG: ${scale.toInt()}x", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = 1f..8f,
                    steps = 6
                )
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = includeSolutions, onCheckedChange = { includeSolutions = it })
                Text("Includi soluzioni separate")
            }

            Button(
                onClick = {
                    val f = ExportManager.export(
                        context = context,
                        result = result,
                        format = format,
                        name = name,
                        pngScale = scale.toInt(),
                        includeSolutions = includeSolutions
                    )
                    lastFile = f.absolutePath
                    // Share
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        f
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = when (format) {
                            ExportFormat.PNG -> "image/png"
                            ExportFormat.PDF -> "application/pdf"
                            ExportFormat.SVG -> "image/svg+xml"
                        }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Condividi"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Esporta e condividi") }

            lastFile?.let {
                Text("File salvato in: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
