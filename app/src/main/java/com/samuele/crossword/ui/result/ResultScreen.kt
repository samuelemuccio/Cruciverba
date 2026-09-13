package com.samuele.crossword.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.CrosswordTheme
import com.samuele.crossword.engine.Direction
import com.samuele.crossword.engine.WordClue
import com.samuele.crossword.ui.components.ButtonVariant
import com.samuele.crossword.ui.components.FreeSpaceCrosswordViewer
import com.samuele.crossword.ui.components.PremiumButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    vm: ResultViewModel,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStyleSheet by remember { mutableStateOf(false) }

    LaunchedEffect(s.savedToast) {
        s.savedToast?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Il tuo cruciverba", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.toggleSolution() },
                        modifier = Modifier.testTag("toggle_solution_button")
                    ) {
                        Icon(
                            if (s.showSolution) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (s.showSolution) "Nascondi soluzione" else "Mostra soluzione"
                        )
                    }
                    IconButton(
                        onClick = onExport,
                        enabled = s.current != null,
                        modifier = Modifier.testTag("export_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Esporta e condividi")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            if (s.variants.isNotEmpty() && s.current != null) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PremiumButton(
                            text = "Stile",
                            icon = Icons.Default.Palette,
                            onClick = { showStyleSheet = true },
                            variant = ButtonVariant.Secondary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_style_sheet_button")
                        )

                        PremiumButton(
                            text = "Salva",
                            icon = Icons.Default.Save,
                            onClick = { vm.saveCurrent() },
                            variant = ButtonVariant.Secondary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_result_button")
                        )

                        PremiumButton(
                            text = "Esporta",
                            icon = Icons.Default.Share,
                            onClick = onExport,
                            variant = ButtonVariant.Primary,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("bottom_export_button")
                        )
                    }
                }
            }
        }
    ) { padding ->
        when {
            s.loading -> LoadingView(Modifier.padding(padding))
            s.error != null && s.variants.isEmpty() -> ErrorView(
                error = s.error!!,
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            s.variants.isNotEmpty() && s.current != null -> {
                val currentResult = s.current!!

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header con Navigatore Varianti e chip di selezione rapida
                    VariantNavigatorBar(
                        current = s.index + 1,
                        total = s.variants.size,
                        hasPrev = s.hasPrev,
                        hasNext = s.hasNext,
                        selectedIndex = s.index,
                        aestheticScore = s.currentReport?.aestheticScore,
                        onSelect = { vm.jumpTo(it) },
                        onPrev = { vm.prev() },
                        onNext = { vm.next() }
                    )

                    // Spazio libero per il cruciverba rettangolare con Zoom, Pan e rendering grafico
                    FreeSpaceCrosswordViewer(
                        result = currentResult,
                        theme = s.theme,
                        showSolution = s.showSolution,
                        onToggleSolution = { vm.toggleSolution() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    // Sezione Definizioni con elenco completo delle parole inserite
                    ClueSection(
                        result = currentResult,
                        theme = s.theme,
                        showSolution = s.showSolution,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(24.dp))
                }

                if (showStyleSheet) {
                    StyleSheet(
                        currentTheme = s.theme,
                        showSolution = s.showSolution,
                        cellScale = s.cellScale,
                        onThemeChange = { vm.changeTheme(it) },
                        onFontChange = { vm.changeFont(it) },
                        onCellScaleChange = { vm.changeCellScale(it) },
                        onToggleSolution = { vm.toggleSolution() },
                        onDismiss = { showStyleSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
fun VariantNavigatorBar(
    current: Int,
    total: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    selectedIndex: Int,
    aestheticScore: com.samuele.crossword.engine.AestheticScore? = null,
    onSelect: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val chipListState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        chipListState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onPrev,
                    enabled = hasPrev,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.size(36.dp).testTag("prev_solution_button")
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Variante precedente")
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Variante $current di $total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    val subtitle = if (aestheticScore != null) {
                        val densityPct = kotlin.math.round((aestheticScore.density * 100f)).toInt()
                        val symPct = kotlin.math.round((aestheticScore.symmetry * 100f)).toInt()
                        if (selectedIndex == 0) {
                            "★ Top Schema: ${aestheticScore.crossings} incroci • ${densityPct}% densità • ${symPct}% simm."
                        } else {
                            "Schema compatto: ${aestheticScore.crossings} incroci • ${densityPct}% densità"
                        }
                    } else {
                        if (selectedIndex == 0) "Schema a massima compattezza (100% parole)" else "Schema incrociato compatto (100% parole)"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FilledIconButton(
                    onClick = onNext,
                    enabled = hasNext,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.size(36.dp).testTag("next_solution_button")
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Variante successiva")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Chip scrollabili per selezionare direttamente la variante
            LazyRow(
                state = chipListState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(total) { idx ->
                    val isSelected = idx == selectedIndex
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(idx) },
                        label = { Text("Schema ${idx + 1}", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ClueSection(
    result: CrosswordResult,
    theme: CrosswordTheme,
    showSolution: Boolean,
    modifier: Modifier = Modifier
) {
    val across = remember(result) {
        result.slots.filter { it.direction == Direction.ACROSS && it.number > 0 }
            .sortedBy { it.number }
    }
    val down = remember(result) {
        result.slots.filter { it.direction == Direction.DOWN && it.number > 0 }
            .sortedBy { it.number }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (across.isNotEmpty()) {
            ClueCard(
                title = "ORIZZONTALI",
                slots = across,
                result = result,
                theme = theme,
                showSolution = showSolution
            )
        }
        if (down.isNotEmpty()) {
            ClueCard(
                title = "VERTICALI",
                slots = down,
                result = result,
                theme = theme,
                showSolution = showSolution
            )
        }
    }
}

@Composable
fun ClueCard(
    title: String,
    slots: List<com.samuele.crossword.engine.Slot>,
    result: CrosswordResult,
    theme: CrosswordTheme,
    showSolution: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${slots.size} parole",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            slots.forEach { slot ->
                val wordClue = result.assignments[slot.id]
                val clue = wordClue?.clue ?: "—"
                val word = wordClue?.word ?: ""

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${slot.number}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = clue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (showSolution && word.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Soluzione: $word",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${slot.length}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text(
                text = "Creazione cruciverba in corso...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "L'algoritmo di backtracking sta calcolando incroci al 100% per tutte le tue parole",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun ErrorView(
    error: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Nessuna combinazione",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Torna alle parole")
                }
            }
        }
    }
}
