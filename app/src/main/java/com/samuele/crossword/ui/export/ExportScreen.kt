package com.samuele.crossword.ui.export

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.samuele.crossword.engine.CrosswordResult
import java.io.File
import com.samuele.crossword.export.ExportFormat
import com.samuele.crossword.export.ExportManager
import com.samuele.crossword.ui.components.ButtonVariant
import com.samuele.crossword.ui.components.GlassmorphicCard
import com.samuele.crossword.ui.components.PremiumButton
import com.samuele.crossword.ui.components.obsidianBackground
import com.samuele.crossword.ui.theme.RoyalIndigo
import com.samuele.crossword.ui.theme.SlateTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(result: CrosswordResult, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var scale by remember { mutableFloatStateOf(4f) }
    var transparentCells by remember { mutableStateOf(true) }
    var includeSolutions by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("cruciverba") }
    var isSaving by remember { mutableStateOf(false) }
    var isSavedSuccess by remember { mutableStateOf(false) }
    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }
    var lastSavedPath by remember { mutableStateOf<String?>(null) }

    val baseCellPx = 80
    val estimatedWidth = (result.cols * baseCellPx * scale).toInt()
    val estimatedHeight = (result.rows * baseCellPx * scale).toInt()

    Scaffold(
        modifier = Modifier.obsidianBackground(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Scarica Cruciverba",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0C10).copy(alpha = 0.85f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Sezione Nome File
            GlassmorphicCard(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Nome file",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Nome del file...", color = SlateTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalIndigo) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalIndigo,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF121420),
                            unfocusedContainerColor = Color(0xFF121420)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_filename_field")
                    )
                }
            }

            // Selezione Formato
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Formato esportazione",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExportFormat.entries.forEach { f ->
                        val isSelected = format == f
                        FilterChip(
                            selected = isSelected,
                            onClick = { format = f },
                            label = {
                                Text(
                                    when (f) {
                                        ExportFormat.PNG -> "PNG (Trasparente)"
                                        ExportFormat.PDF -> "PDF"
                                        ExportFormat.SVG -> "SVG"
                                    },
                                    color = if (isSelected) Color.White else SlateTextSecondary
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = Color.White) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalIndigo,
                                containerColor = Color(0xFF141724)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.12f),
                                selectedBorderColor = RoyalIndigo
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("format_chip_${f.name.lowercase()}")
                        )
                    }
                }
            }

            // Opzioni specifiche PNG per ricalco su Canva
            if (format == ExportFormat.PNG) {
                GlassmorphicCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = RoyalIndigo
                            )
                            Text(
                                "Ottimizzato per Canva e Ricalco",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoyalIndigo
                            )
                        }
                        Text(
                            "Il PNG esporta SOLO i quadrati della griglia con i loro bordi e i rispettivi numeretti. Nessuno sfondo, nessuna casella nera esterna e nessuna scritta o definizione fuori dalla griglia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Switch Celle Trasparenti
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Celle trasparenti (consigliato)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    "Solo contorni neri e numeri, ideale da sovrapporre su Canva",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary
                                )
                            }
                            Switch(
                                checked = transparentCells,
                                onCheckedChange = { transparentCells = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RoyalIndigo,
                                    uncheckedThumbColor = SlateTextSecondary,
                                    uncheckedTrackColor = Color(0xFF1E2232)
                                ),
                                modifier = Modifier.testTag("transparent_cells_switch")
                            )
                        }
                    }
                }

                // Risoluzione con slider raffinato e feedback aptico
                GlassmorphicCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Risoluzione: ${scale.toInt()}x",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Surface(
                                color = RoyalIndigo.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "$estimatedWidth × $estimatedHeight px",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalIndigo
                                )
                            }
                        }

                        // Slider minimale da 4px in grigio scuro con riempimento in indaco vivo e step aptici discreti
                        Slider(
                            value = scale,
                            onValueChange = { newVal ->
                                val discrete = newVal.roundToInt().toFloat()
                                if (discrete != scale) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scale = discrete
                                }
                            },
                            valueRange = 2f..8f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = RoyalIndigo,
                                inactiveTrackColor = Color(0xFF1E2232),
                                activeTickColor = Color.White.copy(alpha = 0.5f),
                                inactiveTickColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("resolution_scale_slider")
                        )

                        Text(
                            when {
                                scale >= 6f -> "Risoluzione Ultra HD / 4K+: perfetta per ingrandimenti giganti e stampa nitida"
                                scale >= 4f -> "Alta Risoluzione (consigliata): linee e numeri definiti per Canva"
                                else -> "Risoluzione Standard per anteprima rapida"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )
                    }
                }
            }

            // Opzione soluzioni separate
            GlassmorphicCard(shape = RoundedCornerShape(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Checkbox(
                        checked = includeSolutions,
                        onCheckedChange = { includeSolutions = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = RoyalIndigo,
                            checkmarkColor = Color.White,
                            uncheckedColor = SlateTextSecondary
                        ),
                        modifier = Modifier.testTag("include_solutions_checkbox")
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Includi anche schema con lettere/soluzione",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            "Genera un secondo file con le lettere compilate",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // AZIONE PRINCIPALE: SALVA IN GALLERIA (Se PNG)
            if (format == ExportFormat.PNG) {
                PremiumButton(
                    text = if (isSaving) "Download in corso..." else "Scarica PNG nella Galleria",
                    icon = Icons.Default.Download,
                    isLoading = isSaving,
                    isSuccess = isSavedSuccess,
                    onClick = {
                        isSaving = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            try {
                                val uris = ExportManager.saveToGallery(
                                    context = context,
                                    result = result,
                                    name = name,
                                    pngScale = scale.toInt(),
                                    includeSolutions = includeSolutions,
                                    transparentCells = transparentCells
                                )
                                isSaving = false
                                if (uris.isNotEmpty()) {
                                    val savedUri = uris.first()
                                    lastSavedUri = savedUri
                                    isSavedSuccess = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    val res = snackbarHostState.showSnackbar(
                                        message = "Cruciverba salvato nella Galleria!",
                                        actionLabel = "APRI",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(savedUri, "image/png")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(openIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Nessuna app trovata per visualizzare l'immagine", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Errore durante il salvataggio in Galleria", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                isSaving = false
                                Toast.makeText(context, "Errore: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    variant = ButtonVariant.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_to_gallery_button")
                )
            }

            // AZIONE CONDIVIDI / ESPORTA
            PremiumButton(
                text = if (format == ExportFormat.PNG) "Condividi PNG" else "Esporta e Condividi",
                icon = Icons.Default.Share,
                onClick = {
                    val f = ExportManager.export(
                        context = context,
                        result = result,
                        format = format,
                        name = name,
                        pngScale = scale.toInt(),
                        includeSolutions = includeSolutions,
                        transparentCells = transparentCells
                    )
                    lastSavedPath = f.absolutePath

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        f
                    )
                    lastSavedUri = uri
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = when (format) {
                            ExportFormat.PNG -> "image/png"
                            ExportFormat.PDF -> "application/pdf"
                            ExportFormat.SVG -> "image/svg+xml"
                        }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Condividi cruciverba"))
                },
                variant = ButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_export_button")
            )

            // Informazioni e Azioni immediate sul file appena salvato
            if (lastSavedUri != null || lastSavedPath != null) {
                GlassmorphicCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RoyalIndigo)
                            Text(
                                "File salvato e pronto all'uso",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            "Il file è pronto per essere importato e ricalcato su Canva o modificato in qualsiasi software grafico.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (lastSavedUri != null) {
                                PremiumButton(
                                    text = "Apri Immagine",
                                    icon = Icons.Default.Visibility,
                                    onClick = {
                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(lastSavedUri, "image/png")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(openIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Nessun visualizzatore immagini disponibile", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    variant = ButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            PremiumButton(
                                text = "Condividi",
                                icon = Icons.Default.Share,
                                onClick = {
                                    val uriToShare = lastSavedUri ?: lastSavedPath?.let {
                                        FileProvider.getUriForFile(context, "${context.packageName}.provider", File(it))
                                    }
                                    if (uriToShare != null) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uriToShare)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Invia cruciverba"))
                                    }
                                },
                                variant = ButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
