package com.samuele.crossword.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.samuele.crossword.ui.components.GlassmorphicCard
import com.samuele.crossword.ui.components.PremiumButton
import com.samuele.crossword.ui.components.obsidianBackground
import com.samuele.crossword.ui.theme.RoyalIndigo
import com.samuele.crossword.ui.theme.RoyalIndigoDark
import com.samuele.crossword.ui.theme.SlateTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    vm: ResultViewModel,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    val s by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(s.savedToast) {
        s.savedToast?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.obsidianBackground(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cruciverba",
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
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = { vm.toggleSolution() },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("toggle_solution_button")
                        ) {
                            Icon(
                                if (s.showSolution) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (s.showSolution) "Nascondi soluzione" else "Mostra soluzione",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0C10).copy(alpha = 0.85f)
                )
            )
        },
        bottomBar = {
            if (s.variants.isNotEmpty() && s.current != null) {
                Surface(
                    color = Color(0xFF0E1018).copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PremiumButton(
                            text = "Salva",
                            icon = Icons.Default.BookmarkBorder,
                            onClick = { vm.saveCurrent() },
                            variant = ButtonVariant.Secondary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_result_button")
                        )

                        PremiumButton(
                            text = "Scarica",
                            icon = Icons.Default.Download,
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

                var dragAccumulator by remember { mutableFloatStateOf(0f) }

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .pointerInput(s.index, s.variants.size) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragAccumulator < -60f && s.hasNext) {
                                        vm.next()
                                    } else if (dragAccumulator > 60f && s.hasPrev) {
                                        vm.prev()
                                    }
                                    dragAccumulator = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragAccumulator += dragAmount
                                }
                            )
                        }
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(2.dp))

                    // Pillola galleggiante in vetro satinato per navigazione varianti
                    if (s.variants.size > 1) {
                        FloatingVariantNavigatorPill(
                            current = s.index + 1,
                            total = s.variants.size,
                            hasPrev = s.hasPrev,
                            hasNext = s.hasNext,
                            aestheticScore = s.currentReport?.aestheticScore,
                            onPrev = { vm.prev() },
                            onNext = { vm.next() }
                        )
                    }

                    // Cruciverba 2D con animazione fluida
                    FreeSpaceCrosswordViewer(
                        result = currentResult,
                        theme = s.theme,
                        showSolution = s.showSolution,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    // Card Parola Cardine Misteriosa (se presente nello schema)
                    if (currentResult.mysteryInfo != null) {
                        MysteryWordEnigmaCard(
                            result = currentResult,
                            showGlobalSolution = s.showSolution,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    // Sezione Definizioni con Segmented Control orizzontali/verticali
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
            }
        }
    }
}

@Composable
fun FloatingVariantNavigatorPill(
    current: Int,
    total: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    aestheticScore: com.samuele.crossword.engine.AestheticScore? = null,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassmorphicCard(
            shape = RoundedCornerShape(26.dp),
            backgroundColor = Color(0xFF141724).copy(alpha = 0.88f),
            elevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Freccia sinistra minimale semi-trasparente
                Surface(
                    onClick = onPrev,
                    enabled = hasPrev,
                    shape = CircleShape,
                    color = if (hasPrev) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                    border = if (hasPrev) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("prev_solution_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Variante precedente",
                            tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Centro: "Variante X di Y", metriche e dot pagination
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Variante $current di $total",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    val subtitle = if (aestheticScore != null) {
                        val densityPct = kotlin.math.round((aestheticScore.density * 100f)).toInt()
                        "${aestheticScore.crossings} incroci • ${densityPct}% densità"
                    } else {
                        "100% parole incrociate"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary
                    )

                    // Micro-indicatori di scorrimento (dot pagination con larghezza attiva dinamica)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val maxDots = total.coerceAtMost(8)
                        for (i in 0 until maxDots) {
                            val isActive = i == (current - 1).coerceAtMost(maxDots - 1)
                            val dotWidth by animateDpAsState(
                                targetValue = if (isActive) 16.dp else 5.dp,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                label = "dotWidth"
                            )
                            val dotColor by animateColorAsState(
                                targetValue = if (isActive) RoyalIndigo else Color.White.copy(alpha = 0.22f),
                                label = "dotColor"
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = dotWidth, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }
                }

                // Freccia destra minimale semi-trasparente
                Surface(
                    onClick = onNext,
                    enabled = hasNext,
                    shape = CircleShape,
                    color = if (hasNext) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                    border = if (hasNext) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("next_solution_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Variante successiva",
                            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

enum class ClueTab {
    ACROSS,
    DOWN
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

    var selectedTab by remember { mutableStateOf(ClueTab.ACROSS) }

    GlassmorphicCard(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.animateContentSize(
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // iOS Segmented Control in vetro satinato
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0D0F18).copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab Orizzontali
                    val isAcross = selectedTab == ClueTab.ACROSS
                    val acrossBg by animateColorAsState(
                        if (isAcross) RoyalIndigoDark else Color.Transparent,
                        label = "acrossBg"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(11.dp))
                            .background(acrossBg)
                            .clickable { selectedTab = ClueTab.ACROSS },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ORIZZONTALI (${across.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isAcross) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAcross) Color.White else SlateTextSecondary
                        )
                    }

                    // Tab Verticali
                    val isDown = selectedTab == ClueTab.DOWN
                    val downBg by animateColorAsState(
                        if (isDown) RoyalIndigoDark else Color.Transparent,
                        label = "downBg"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(11.dp))
                            .background(downBg)
                            .clickable { selectedTab = ClueTab.DOWN },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VERTICALI (${down.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isDown) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDown) Color.White else SlateTextSecondary
                        )
                    }
                }
            }

            // Lista quesiti con badge numerico circolare e 1px border
            val currentSlots = if (selectedTab == ClueTab.ACROSS) across else down
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentSlots.isEmpty()) {
                    Text(
                        text = "Nessuna definizione disponibile",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    currentSlots.forEach { slot ->
                        val wordClue = result.assignments[slot.id]
                        val clue = wordClue?.clue ?: "—"
                        val word = wordClue?.word ?: ""
                        val isMystery = (slot.id == result.mysteryInfo?.slotId)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Badge numerico circolare con fondo scuro semi-trasparente e bordo 1px
                            Surface(
                                shape = CircleShape,
                                color = if (isMystery) Color(0xFFF59E0B).copy(alpha = 0.22f) else Color(0xFF1E2336),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isMystery) Color(0xFFF59E0B).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.14f)
                                ),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${slot.number}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMystery) Color(0xFFFBBF24) else Color.White
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                if (isMystery) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    ) {
                                        Text(
                                            text = "🔑 PAROLA MISTERIOSA CARDINE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFBBF24),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = clue,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    lineHeight = 19.sp
                                )
                                if (showSolution && word.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Soluzione: $word",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMystery) Color(0xFFFBBF24) else RoyalIndigo
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            // Badge numero lettere
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.06f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Text(
                                    text = "${slot.length}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextSecondary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
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
            CircularProgressIndicator(
                color = RoyalIndigo,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Creazione cruciverba in corso...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "L'algoritmo di backtracking sta calcolando incroci al 100% per tutte le tue parole",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
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
        GlassmorphicCard(
            shape = RoundedCornerShape(22.dp),
            backgroundColor = Color(0xFF201318)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Nessuna combinazione",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateTextSecondary,
                    textAlign = TextAlign.Center
                )
                PremiumButton(
                    text = "Torna alle parole",
                    icon = Icons.Default.Refresh,
                    onClick = onBack,
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun MysteryWordEnigmaCard(
    result: CrosswordResult,
    showGlobalSolution: Boolean,
    modifier: Modifier = Modifier
) {
    val info = result.mysteryInfo ?: return
    var manuallyRevealed by remember(result) { mutableStateOf(false) }
    val isRevealed = showGlobalSolution || manuallyRevealed

    GlassmorphicCard(
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0xFF161520).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFF59E0B).copy(alpha = 0.7f),
                    Color(0xFFD97706).copy(alpha = 0.4f),
                    Color(0xFFFBBF24).copy(alpha = 0.7f)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mystery_enigma_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "PAROLA MISTERIOSA CARDINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = info.clue.ifBlank { "Risolvi gli incroci per dedurre la parola segreta!" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 2
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${info.revealedCount}/${info.totalLetters} incrociate",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Barra tessere geometriche squadrate (90° netti, fedeli all'estetica quadrata del cruciverba)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    info.word.forEachIndexed { index, char ->
                        val isIntersected = info.intersectedIndices.contains(index)
                        val shouldShowLetter = isRevealed || isIntersected

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when {
                                    isIntersected -> Color(0xFFFFFBEB)
                                    isRevealed -> Color(0xFF1E2234)
                                    else -> Color(0xFF12141F)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    if (isIntersected) Color(0xFFD97706) else Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (shouldShowLetter) {
                                        Text(
                                            text = char.toString(),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isIntersected) Color(0xFF92400E) else Color.White
                                            )
                                        )
                                    } else {
                                        Text(
                                            text = "?",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = SlateTextSecondary.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            }

                            // Micro-etichetta di stato
                            Text(
                                text = if (isIntersected) "incrocio" else "deduzione",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                color = if (isIntersected) Color(0xFFFBBF24) else SlateTextSecondary.copy(alpha = 0.7f),
                                fontWeight = if (isIntersected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Descrizione pedagogica della meccanica
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (info.revealedCount == info.totalLetters) {
                            "Tutte le lettere di '${info.word}' si incrociano con le altre parole dello schema!"
                        } else {
                            val missing = info.totalLetters - info.revealedCount
                            "${info.revealedCount} lettere si compongono dagli incroci. Le rimanenti $missing si deducono dalla definizione!"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
            }

            // Pulsante azione per svelare / nascondere
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { manuallyRevealed = !manuallyRevealed },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (manuallyRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (manuallyRevealed) "Nascondi lettere da dedurre" else "Svela parola segreta",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                }
            }
        }
    }
}

