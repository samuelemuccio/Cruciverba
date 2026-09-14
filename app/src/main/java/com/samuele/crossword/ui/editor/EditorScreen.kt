package com.samuele.crossword.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuele.crossword.data.db.WordListEntity
import com.samuele.crossword.engine.WordClue
import com.samuele.crossword.ui.components.ButtonVariant
import com.samuele.crossword.ui.components.GlassmorphicCard
import com.samuele.crossword.ui.components.PremiumButton
import com.samuele.crossword.ui.components.obsidianBackground
import com.samuele.crossword.ui.theme.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onGenerate: (words: List<WordClue>, mysteryWord: WordClue?) -> Unit
) {
    val s by vm.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var showAiSheet by remember { mutableStateOf(false) }
    var showSavedListsSheet by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(s.toastMessage) {
        s.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearToast()
        }
    }

    if (showAiSheet) {
        AiPasteBottomSheet(
            onDismiss = { showAiSheet = false },
            onImport = { words, replaceExisting ->
                vm.importWords(words, replaceExisting)
            }
        )
    }

    if (s.editingWord != null) {
        EditWordDialog(
            wordEntry = s.editingWord!!,
            onDismiss = { vm.setEditingWord(null) },
            onConfirm = { word, clue ->
                vm.updateWord(s.editingWord!!.id, word, clue)
            }
        )
    }

    if (showSavedListsSheet) {
        SavedListsBottomSheet(
            savedLists = s.savedLists,
            currentListName = s.listName,
            onListNameChange = vm::onListNameChange,
            onSave = { vm.saveList() },
            onLoad = { id ->
                vm.loadList(id)
                showSavedListsSheet = false
            },
            onDelete = { vm.deleteList(it) },
            onDismiss = { showSavedListsSheet = false }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Svuotare la lista?") },
            text = { Text("Tutte le ${s.words.size} parole inserite verranno rimosse.") },
            shape = RoundedCornerShape(20.dp),
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearAllWords()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = IosRed)
                ) {
                    Text("Svuota tutto", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.obsidianBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            IosEditorTopBar(
                wordCount = s.words.size,
                onOpenAi = { showAiSheet = true },
                onOpenSaved = { showSavedListsSheet = true },
                onClearAll = {
                    if (s.words.isNotEmpty()) showClearConfirmDialog = true
                }
            )
        },
        bottomBar = {
            IosGenerateBottomDock(
                wordCount = s.words.size,
                isMysteryMode = s.mysteryWordEnabled,
                mysteryWord = s.mysteryWord,
                onGenerate = {
                    focusManager.clearFocus()
                    val mystery = if (s.mysteryWordEnabled && s.mysteryWord.length >= 2) {
                        WordClue(s.mysteryWord, s.mysteryClue.ifBlank { "Parola misteriosa da svelare" })
                    } else null
                    onGenerate(s.words.map { WordClue(it.word, it.clue) }, mystery)
                }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero AI Smart Paste Card
            item(key = "ai_hero_card") {
                IosAiHeroCard(
                    onOpenAiSheet = { showAiSheet = true }
                )
            }

            // Parola Cardine Misteriosa Card (richiesta dall'utente per comporre il cruciverba attorno ad essa)
            item(key = "mystery_word_card") {
                IosMysteryWordCard(
                    enabled = s.mysteryWordEnabled,
                    mysteryWord = s.mysteryWord,
                    mysteryClue = s.mysteryClue,
                    onToggle = vm::toggleMysteryMode,
                    onWordChange = vm::onMysteryWordChange,
                    onClueChange = vm::onMysteryClueChange
                )
            }

            // Word Input Card
            item(key = "word_input_card") {
                IosWordInputCard(
                    draftWord = s.draftWord,
                    draftClue = s.draftClue,
                    onWordChange = vm::onDraftWordChange,
                    onClueChange = vm::onDraftClueChange,
                    onAdd = {
                        vm.addWord()
                        focusManager.clearFocus()
                    }
                )
            }

            // Orphan words warning
            if (s.analysis.orphanWords.isNotEmpty()) {
                item(key = "orphan_warning") {
                    IosOrphanWarningCard(orphanWords = s.analysis.orphanWords)
                }
            }

            // Word List Section Header
            item(key = "words_header") {
                IosWordsListHeader(
                    count = s.words.size,
                    onShuffle = { vm.shuffleWords() },
                    onOpenSavedLists = { showSavedListsSheet = true }
                )
            }

            // Word List Items with Long-Press Drag & Drop and native Swipe-to-Dismiss
            itemsIndexed(
                items = s.words,
                key = { _, item -> item.id }
            ) { index, wordEntry ->
                IosWordCard(
                    modifier = Modifier.animateItem(),
                    index = index,
                    totalCount = s.words.size,
                    entry = wordEntry,
                    onMove = { from, to -> vm.moveWord(from, to) },
                    onEdit = { vm.setEditingWord(wordEntry) },
                    onDelete = {
                        val removedIndex = index
                        val removedItem = wordEntry
                        vm.removeWord(wordEntry.id)
                        scope.launch {
                            val res = snackbarHostState.showSnackbar(
                                message = "Parola \"${removedItem.word}\" eliminata",
                                actionLabel = "Annulla",
                                duration = SnackbarDuration.Short
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                vm.restoreWord(removedIndex, removedItem)
                            }
                        }
                    }
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosEditorTopBar(
    wordCount: Int,
    onOpenAi: () -> Unit,
    onOpenSaved: () -> Unit,
    onClearAll: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Cruciverba",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )

                Surface(
                    shape = CircleShape,
                    color = IosIndigo.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$wordCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = IosIndigo,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        },
        actions = {
            // AI Action Button
            IconButton(
                onClick = onOpenAi,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Incolla da AI",
                    tint = IosIndigo
                )
            }

            // Saved Lists Button
            IconButton(
                onClick = onOpenSaved,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    Icons.Default.BookmarkBorder,
                    contentDescription = "Liste salvate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Clear All Button
            if (wordCount > 0) {
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Svuota lista",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun IosAiHeroCard(
    onOpenAiSheet: () -> Unit
) {
    Surface(
        onClick = onOpenAiSheet,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    IosIndigo.copy(alpha = 0.4f),
                    IosPurple.copy(alpha = 0.3f),
                    IosTeal.copy(alpha = 0.2f)
                )
            )
        ),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_smart_paste_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = IosIndigo.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = IosIndigo,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Formatta con AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = IosGreen.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "SMART",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = IosGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Hai già domande e risposte? L'AI le formatta in un istante per il cruciverba!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun IosWordInputCard(
    draftWord: String,
    draftClue: String,
    onWordChange: (String) -> Unit,
    onClueChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    val canAdd = draftWord.length >= 2 && draftClue.trim().isNotBlank()

    GlassmorphicCard(
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nuova Parola",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (draftWord.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = RoyalIndigo.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${draftWord.length} lettere",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Input Fields impilati verticalmente per evitare troncamenti
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Word Box (auto uppercase, full width)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10121C),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    BasicTextField(
                        value = draftWord,
                        onValueChange = onWordChange,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(RoyalIndigo),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                            .testTag("input_draft_word"),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draftWord.isEmpty()) {
                                    Text(
                                        "PAROLA (es. ROMA)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SlateTextSecondary.copy(alpha = 0.5f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                // Clue Box (riga dedicata per definizione, senza troncamenti)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10121C),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    BasicTextField(
                        value = draftClue,
                        onValueChange = onClueChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            lineHeight = 18.sp
                        ),
                        cursorBrush = SolidColor(RoyalIndigo),
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (canAdd) ImeAction.Done else ImeAction.Default
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (canAdd) onAdd() }
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("input_draft_clue"),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.TopStart) {
                                if (draftClue.isEmpty()) {
                                    Text(
                                        "Definizione della parola...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SlateTextSecondary.copy(alpha = 0.5f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }

            // Aggiungi Button
            PremiumButton(
                text = "Aggiungi alla lista",
                icon = Icons.Default.Add,
                onClick = onAdd,
                enabled = canAdd,
                variant = ButtonVariant.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_word_button")
            )
        }
    }
}

@Composable
private fun IosWordsListHeader(
    count: Int,
    onShuffle: () -> Unit,
    onOpenSavedLists: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "LE TUE PAROLE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Shuffle
            TextButton(
                onClick = onShuffle,
                enabled = count > 1,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mischia", style = MaterialTheme.typography.labelSmall)
            }

            // Saved Lists
            TextButton(
                onClick = onOpenSavedLists,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Liste", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosWordCard(
    modifier: Modifier = Modifier,
    index: Int,
    totalCount: Int,
    entry: WordEntry,
    onMove: (from: Int, to: Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 10f else 1f,
        label = "dragElevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        label = "dragScale"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissVal ->
            if (dismissVal == SwipeToDismissBoxValue.EndToStart || dismissVal == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFDC2626).copy(alpha = 0.9f))
                    .padding(horizontal = 20.dp)
                    .testTag("remove_word_${entry.word}"),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Rimuovi ${entry.word}",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDragging) Color(0xFF1B1E2C) else Color(0xFF131622),
            border = BorderStroke(
                1.dp,
                if (isDragging) RoyalIndigo.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f)
            ),
            shadowElevation = elevation.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .scale(scale)
                .pointerInput(index, totalCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        try {
                            withTimeout(320L) {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    onEdit()
                                }
                            }
                        } catch (e: TimeoutCancellationException) {
                            // Tieni premuto: Drag & Drop abilitato sulla card intera
                            isDragging = true
                            dragAccumulator = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUp()) {
                                    break
                                }
                                val dragAmount = change.positionChange()
                                change.consume()
                                dragAccumulator += dragAmount.y
                                val threshold = 55f
                                if (dragAccumulator > threshold && index < totalCount - 1) {
                                    onMove(index, index + 1)
                                    dragAccumulator = 0f
                                } else if (dragAccumulator < -threshold && index > 0) {
                                    onMove(index, index - 1)
                                    dragAccumulator = 0f
                                }
                            }
                            isDragging = false
                            dragAccumulator = 0f
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pillola Parola a dimensione fissa (108dp) per simmetria geometrica e allineamento
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RoyalIndigo.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .width(108.dp)
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = entry.word,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = if (entry.word.length > 7) 12.sp else 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = RoyalIndigo.copy(alpha = 0.35f)
                        ) {
                            Text(
                                text = "${entry.word.length}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Testo Definizione: 2 righe max con ellipsis dentro altezza fissa
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = entry.clue,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 15.sp,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IosOrphanWarningCard(orphanWords: List<WordClue>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = IosOrange.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, IosOrange.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = IosOrange,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Lettere di incrocio mancanti",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = IosOrange
                )
                Text(
                    text = "Queste parole non hanno lettere in comune con le altre: ${orphanWords.joinToString { it.word }}. Prova ad aggiungere parole con lettere compatibili.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun IosMysteryWordCard(
    enabled: Boolean,
    mysteryWord: String,
    mysteryClue: String,
    onToggle: (Boolean) -> Unit,
    onWordChange: (String) -> Unit,
    onClueChange: (String) -> Unit
) {
    GlassmorphicCard(
        shape = RoundedCornerShape(22.dp),
        backgroundColor = if (enabled) Color(0xFF181720).copy(alpha = 0.95f) else Color(0xFF141724).copy(alpha = 0.85f),
        border = BorderStroke(
            1.2.dp,
            if (enabled) Brush.horizontalGradient(
                listOf(Color(0xFFF59E0B).copy(alpha = 0.75f), Color(0xFFD97706).copy(alpha = 0.5f), Color(0xFFFBBF24).copy(alpha = 0.75f))
            ) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mystery_word_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        color = if (enabled) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = if (enabled) Color(0xFFF59E0B) else SlateTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Parola Misteriosa",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (enabled) Color(0xFFFBBF24) else Color.White
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (enabled) Color(0xFFF59E0B).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = if (enabled) "ATTIVA" else "OPZIONALE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) Color(0xFFFBBF24) else SlateTextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Cruciverba imperniato su una parola cardine",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = SlateTextSecondary
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFF59E0B),
                        uncheckedThumbColor = SlateTextSecondary,
                        uncheckedTrackColor = Color(0xFF222634)
                    ),
                    modifier = Modifier.testTag("toggle_mystery_mode")
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Inserisci la parola o nome cardine (senza limite di lunghezza, es. DEMON SALVATORE): lo schema si comporrà incrociando le sue lettere. Le lettere che non si incrociano dovranno essere dedotte con l'indizio!",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    // Campo Parola Cardine
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10121C),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        BasicTextField(
                            value = mysteryWord,
                            onValueChange = onWordChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBBF24),
                                letterSpacing = 1.2.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFFF59E0B)),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("input_mystery_word"),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (mysteryWord.isEmpty()) {
                                        Text(
                                            "Parola o nome segreto (es. DEMON SALVATORE, ITALIA)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SlateTextSecondary.copy(alpha = 0.5f)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }

                    // Campo Indizio o Enigma
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10121C),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        BasicTextField(
                            value = mysteryClue,
                            onValueChange = onClueChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                lineHeight = 18.sp
                            ),
                            cursorBrush = SolidColor(RoyalIndigo),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("input_mystery_clue"),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (mysteryClue.isEmpty()) {
                                        Text(
                                            "Indizio o enigma misterioso da risolvere...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SlateTextSecondary.copy(alpha = 0.5f)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }

                    // Anteprima geometrica netta e squadrata delle lettere della parola misteriosa
                    if (mysteryWord.isNotBlank()) {
                        val letterCount = mysteryWord.count { it in 'A'..'Z' }
                        val rawWords = mysteryWord.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                        val scrollState = rememberScrollState()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Composizione tessere:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextSecondary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${letterCount} lettere" + if (rawWords.size > 1) " • ${rawWords.size} parole" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Riga tessere orizzontale scorrevole (supporta qualsiasi lunghezza senza tagli)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState)
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                mysteryWord.forEach { char ->
                                    if (char == ' ') {
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .height(28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(Color(0xFFF59E0B).copy(alpha = 0.5f), CircleShape)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f)),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = char.toString(),
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFFFBBF24)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosGenerateBottomDock(
    wordCount: Int,
    isMysteryMode: Boolean,
    mysteryWord: String,
    onGenerate: () -> Unit
) {
    val canGenerate = if (isMysteryMode) {
        wordCount >= 2 && mysteryWord.length >= 2
    } else {
        wordCount >= 2
    }

    val buttonText = when {
        isMysteryMode && mysteryWord.length < 2 -> "Inserisci la parola misteriosa"
        isMysteryMode -> "Genera con Parola Cardine ($wordCount parole)"
        wordCount < 2 -> "Aggiungi almeno 2 parole"
        else -> "Genera Cruciverba ($wordCount parole)"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            PremiumButton(
                text = buttonText,
                icon = if (isMysteryMode) Icons.Default.Key else Icons.Default.AutoAwesome,
                onClick = onGenerate,
                enabled = canGenerate,
                variant = ButtonVariant.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate_crossword_button")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedListsBottomSheet(
    savedLists: List<WordListEntity>,
    currentListName: String,
    onListNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: (Long) -> Unit,
    onDelete: (WordListEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Libreria Liste",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi")
                }
            }

            // Salva nuova lista
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentListName,
                    onValueChange = onListNameChange,
                    placeholder = { Text("Nome nuova lista...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onSave,
                    enabled = currentListName.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosIndigo)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Salva")
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            if (savedLists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna lista salvata",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Liste salvate (${savedLists.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                savedLists.forEach { list ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = IosIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = list.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { onLoad(list.id) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Carica", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { onDelete(list) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Elimina",
                                    tint = IosRed.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
