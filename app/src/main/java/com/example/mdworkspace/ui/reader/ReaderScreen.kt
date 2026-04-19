package com.example.mdworkspace.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mdworkspace.MdWorkspaceApplication
import com.example.mdworkspace.R
import com.example.mdworkspace.domain.model.AnchorMatchType
import com.example.mdworkspace.domain.model.TextAnchorSelection
import com.example.mdworkspace.ui.workspace.FileTreePanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PANEL_EXIT_ANIMATION_MILLIS = 150L

private enum class TextNotePanelPhase {
    Entering,
    Visible,
    Exiting
}

@Composable
fun ReaderScreen(
    path: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocument: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as MdWorkspaceApplication
    val viewModel: ReaderViewModel = viewModel(
        key = "reader-$path",
        factory = ReaderViewModel.factory(path, app.container.repository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val readerScrollState = rememberScrollState()
    val readerSession by app.container.sessionStore.readerSessionFlow.collectAsStateWithLifecycle(
        initialValue = com.example.mdworkspace.core.datastore.ReaderSessionState()
    )
    val density = LocalDensity.current
    val drawerWidth = (LocalConfiguration.current.screenWidthDp * 0.75f).dp
    val noteWorkbenchWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    var focusedSourceOffset by remember { mutableStateOf<Int?>(null) }
    var notePanels by remember { mutableStateOf<List<TextNotePanelUi>>(emptyList()) }
    var panelAnchorYPx by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var activePanelId by remember { mutableStateOf<Long?>(null) }
    var nextPanelId by remember { mutableStateOf(1L) }
    var restoredScrollForPath by remember(path) { mutableStateOf(false) }
    var showNoteWorkbench by remember { mutableStateOf(false) }
    var pendingReaderScrollY by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.canUseNote) {
        if (!state.canUseNote) {
            showNoteWorkbench = false
        }
    }

    LaunchedEffect(pendingReaderScrollY) {
        val targetY = pendingReaderScrollY ?: return@LaunchedEffect
        readerScrollState.animateScrollTo(targetY.coerceAtLeast(0))
        pendingReaderScrollY = null
    }

    LaunchedEffect(path, state.hasDocument, readerSession.lastDocumentPath, readerSession.lastScrollY) {
        if (path.isBlank() || !state.hasDocument || restoredScrollForPath) return@LaunchedEffect
        if (path == readerSession.lastDocumentPath && readerSession.lastScrollY > 0) {
            delay(80)
            readerScrollState.scrollTo(readerSession.lastScrollY)
        } else {
            readerScrollState.scrollTo(0)
        }
        restoredScrollForPath = true
        app.container.sessionStore.saveReaderPosition(path, readerScrollState.value)
    }

    LaunchedEffect(path, state.hasDocument, restoredScrollForPath) {
        if (path.isBlank() || !state.hasDocument || !restoredScrollForPath) return@LaunchedEffect
        snapshotFlow { readerScrollState.value }
            .distinctUntilChanged()
            .debounce(500)
            .collect { scrollY ->
                app.container.sessionStore.saveReaderPosition(path, scrollY)
            }
    }

    fun removePanelAfterExit(panelId: Long) {
        scope.launch {
            delay(PANEL_EXIT_ANIMATION_MILLIS)
            notePanels = notePanels.filterNot { it.id == panelId }
            panelAnchorYPx = panelAnchorYPx - panelId
            if (activePanelId == panelId) {
                activePanelId = notePanels.lastOrNull { it.id != panelId && !it.isClosing }?.id
            }
        }
    }

    fun addPanel(content: TextNotePanelState, text: String) {
        val currentPanels = notePanels.filterNot { it.isClosing }
        val slot = (0..1).firstOrNull { candidate -> currentPanels.none { it.slot == candidate } }
            ?: currentPanels.firstOrNull()?.slot
            ?: 0
        val nextPanel = TextNotePanelUi(
            id = nextPanelId,
            slot = slot,
            content = content,
            noteText = text,
            showFullQuote = false,
            isClosing = false
        )
        nextPanelId += 1
        val panelToDismiss = if (currentPanels.size >= 2) currentPanels.first() else null
        val keptIds = (if (panelToDismiss != null) currentPanels.drop(1) else currentPanels)
            .map { it.id }
            .toSet()
        val nextPanels = notePanels.mapNotNull { panel ->
            when {
                panel.id == panelToDismiss?.id -> panel.copy(isClosing = true, showFullQuote = false)
                panel.isClosing || panel.id in keptIds -> panel
                else -> null
            }
        } + nextPanel
        notePanels = nextPanels
        panelAnchorYPx = panelAnchorYPx.filterKeys { id -> nextPanels.any { it.id == id } }
        activePanelId = nextPanel.id
        panelToDismiss?.let { removePanelAfterExit(it.id) }
    }

    fun updatePanel(panelId: Long, block: (TextNotePanelUi) -> TextNotePanelUi) {
        notePanels = notePanels.map { panel ->
            if (panel.id == panelId) block(panel) else panel
        }
    }

    fun closePanel(panelId: Long) {
        val nextPanels = notePanels.map { panel ->
            if (panel.id == panelId) panel.copy(isClosing = true, showFullQuote = false) else panel
        }
        notePanels = nextPanels
        if (activePanelId == panelId) {
            activePanelId = nextPanels.lastOrNull { it.id != panelId && !it.isClosing }?.id
        }
        removePanelAfterExit(panelId)
    }

    fun openExistingPanel(notes: List<ResolvedTextNoteUi>) {
        val first = notes.firstOrNull() ?: return
        val noteIds = notes.map { it.note.id }
        val preferredNoteId = first.note.id
        val exactPanel = notePanels.firstOrNull { panel ->
            !panel.isClosing &&
                (panel.content as? TextNotePanelState.Existing)?.selectedNoteId == preferredNoteId
        }
        if (exactPanel != null) {
            notePanels = notePanels.map { panel ->
                val existing = panel.content as? TextNotePanelState.Existing
                if (panel.id == exactPanel.id && existing != null) {
                    panel.copy(
                        content = existing.copy(noteIds = noteIds),
                        showFullQuote = false
                    )
                } else {
                    panel
                }
            }
            activePanelId = exactPanel.id
            return
        }

        val reusablePanel = notePanels.firstOrNull { panel ->
            val existing = panel.content as? TextNotePanelState.Existing
            if (panel.isClosing || existing == null) {
                false
            } else {
                preferredNoteId in existing.noteIds ||
                    existing.selectedNoteId in noteIds ||
                    existing.noteIds.any { it in noteIds }
            }
        }
        if (reusablePanel != null) {
            notePanels = notePanels.map { panel ->
                if (panel.id == reusablePanel.id) {
                    panel.copy(
                        content = TextNotePanelState.Existing(
                            noteIds = noteIds,
                            selectedNoteId = preferredNoteId
                        ),
                        noteText = first.note.note,
                        showFullQuote = false
                    )
                } else {
                    panel
                }
            }
            activePanelId = reusablePanel.id
            return
        }
        addPanel(
            content = TextNotePanelState.Existing(
                noteIds = noteIds,
                selectedNoteId = preferredNoteId
            ),
            text = first.note.note
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.width(drawerWidth)
            ) {
                FileTreePanel(
                    rootPath = state.config.normalizedRootPath,
                    onOpenDocument = { nextPath ->
                        scope.launch { drawerState.close() }
                        onOpenDocument(nextPath)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp)
            ) {
                ReaderTopBar(
                    title = state.supported?.title ?: "Markdown Workspace",
                    subtitle = state.supported?.let { "${it.docId}@${it.version}" } ?: "请选择文档",
                    canUseNote = state.canUseNote,
                    isFavorite = state.isFavorite,
                    onOpenTree = { scope.launch { drawerState.open() } },
                    onOpenNoteWorkbench = { showNoteWorkbench = true },
                    onOpenSettings = onOpenSettings,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRefresh = { viewModel.load(forceRefresh = true) }
                )

                state.supported?.let { snapshot ->
                    Text(
                        text = "${snapshot.projectCode} / ${snapshot.path}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.fromCache) {
                    Text(
                        text = "正在显示本地缓存内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                state.unsupported?.let {
                    Text(
                        text = "该文档缺少合法 frontmatter，无法使用本地版本笔记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                state.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(top = 5.dp, bottom = 6.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(readerScrollState)
                ) {
                    if (!state.hasDocument && !state.isLoading) {
                        EmptyReaderState(onOpenTree = { scope.launch { drawerState.open() } })
                    } else if (!state.hasDocument && state.isLoading) {
                        Text(
                            text = "正在打开文档…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val hasOpenQuote = notePanels.any { it.showFullQuote }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(hasOpenQuote) {
                                    if (!hasOpenQuote) return@pointerInput
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.changes.any { it.pressed }) {
                                                notePanels = notePanels.map { it.copy(showFullQuote = false) }
                                            }
                                        }
                                    }
                                }
                        ) {
                            MarkdownText(
                                markdown = state.body,
                                noteSourceRanges = state.textNotes.mapNotNull { textNote ->
                                    val start = textNote.resolution.resolvedStartSourceOffset
                                    val end = textNote.resolution.resolvedEndSourceOffset
                                    if (start != null && end != null && end > start) {
                                        TextNoteSourceRange(
                                            id = textNote.note.id,
                                            startSourceOffset = start,
                                            endSourceOffset = end
                                        )
                                    } else {
                                        null
                                    }
                                },
                                focusedSourceOffset = focusedSourceOffset,
                                anchorRequests = notePanels.mapNotNull { panel ->
                                    panel.anchorSourceOffset(state)?.let { sourceOffset ->
                                        TextAnchorPositionRequest(panel.id, sourceOffset)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onAddTextNote = { selection ->
                                    addPanel(TextNotePanelState.Draft(selection), "")
                                },
                                onSourceOffsetTap = { sourceOffset ->
                                    openExistingPanel(notesAtSourceOffset(state.textNotes, sourceOffset))
                                },
                                onAnchorYChange = { positions ->
                                    if (panelAnchorYPx != positions) panelAnchorYPx = positions
                                },
                                onFocusedSourceOffsetLocated = { anchorY ->
                                    val topPadding = with(density) { 88.dp.toPx().toInt() }
                                    pendingReaderScrollY = anchorY - topPadding
                                },
                                onFocusedSourceOffsetConsumed = {
                                    focusedSourceOffset = null
                                }
                            )
                            TextNotePanelOverlay(
                                panels = notePanels,
                                state = state,
                                anchorPositions = panelAnchorYPx,
                                activePanelId = activePanelId,
                                onActivePanelChange = { activePanelId = it },
                                onUpdatePanel = ::updatePanel,
                                onClosePanel = ::closePanel,
                                onSaveDraft = { panel, selection ->
                                    viewModel.addTextNote(selection, panel.noteText)
                                    closePanel(panel.id)
                                },
                                onSaveExisting = { note, value ->
                                    viewModel.updateTextNote(note.note, value)
                                },
                                onDeleteExisting = { panel, note ->
                                    viewModel.deleteTextNote(note.note)
                                    closePanel(panel.id)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (notePanels.isEmpty()) 24.dp else if (notePanels.size == 1) 210.dp else 380.dp))
                    OrphanedTextNotesSection(state = state, viewModel = viewModel)
                    DocumentNoteSection(state = state, viewModel = viewModel)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            RightSupportingPaneScaffold(
                visible = showNoteWorkbench && state.canUseNote,
                width = noteWorkbenchWidth,
                onClose = { showNoteWorkbench = false }
            ) {
                NoteWorkbenchPanel(
                    state = state,
                    onClose = { showNoteWorkbench = false },
                    onOpenTextNote = { textNote ->
                        val sourceOffset = textNote.resolution.resolvedStartSourceOffset
                            ?: textNote.resolution.resolvedEndSourceOffset
                            ?: return@NoteWorkbenchPanel
                        showNoteWorkbench = false
                        openExistingPanel(notesForSelectedNote(state.textNotes, textNote))
                        focusedSourceOffset = sourceOffset
                    }
                )
            }
        }
    }
}

private sealed interface TextNotePanelState {
    data class Draft(val selection: TextAnchorSelection) : TextNotePanelState

    data class Existing(
        val noteIds: List<Long>,
        val selectedNoteId: Long
    ) : TextNotePanelState
}

private data class TextNotePanelUi(
    val id: Long,
    val slot: Int,
    val content: TextNotePanelState,
    val noteText: String,
    val showFullQuote: Boolean,
    val isClosing: Boolean
)

@Composable
private fun TextNotePanelOverlay(
    panels: List<TextNotePanelUi>,
    state: ReaderUiState,
    anchorPositions: Map<Long, Int>,
    activePanelId: Long?,
    onActivePanelChange: (Long?) -> Unit,
    onUpdatePanel: (Long, (TextNotePanelUi) -> TextNotePanelUi) -> Unit,
    onClosePanel: (Long) -> Unit,
    onSaveDraft: (TextNotePanelUi, TextAnchorSelection) -> Unit,
    onSaveExisting: (ResolvedTextNoteUi, String) -> Unit,
    onDeleteExisting: (TextNotePanelUi, ResolvedTextNoteUi) -> Unit
) {
    val density = LocalDensity.current
    var panelHeightsPx by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var panelDragOffsetsPx by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var panelPhases by remember { mutableStateOf<Map<Long, TextNotePanelPhase>>(emptyMap()) }
    var draggingPanelId by remember { mutableStateOf<Long?>(null) }
    var commitOffsetsAfterDrag by remember { mutableStateOf(false) }
    val panelIds = panels.map { it.id }
    val positionedPanels = panels.filter { it.id in anchorPositions }

    LaunchedEffect(panelIds) {
        val ids = panelIds.toSet()
        panelHeightsPx = panelHeightsPx.filterKeys { it in ids }
        panelDragOffsetsPx = panelDragOffsetsPx.filterKeys { it in ids } +
            panels.filter { it.id !in panelDragOffsetsPx }.associate { it.id to 0f }
        panelPhases = panelPhases.filterKeys { it in ids }
        if (activePanelId !in ids) {
            onActivePanelChange(panels.lastOrNull()?.id)
        }
        if (draggingPanelId !in ids) {
            draggingPanelId = null
        }
    }

    LaunchedEffect(panelIds, anchorPositions, panels.map { it.isClosing }) {
        val nextPhases = panelPhases.toMutableMap()
        panels.forEach { panel ->
            when {
                panel.isClosing -> nextPhases[panel.id] = TextNotePanelPhase.Exiting
                panel.id in anchorPositions && panel.id !in nextPhases -> {
                    nextPhases[panel.id] = TextNotePanelPhase.Entering
                }
            }
        }
        if (nextPhases != panelPhases) panelPhases = nextPhases
    }

    val panelOffsets = positionedPanels.resolvedPanelOffsets(
        anchorPositions = anchorPositions,
        panelHeights = panelHeightsPx,
        panelDragOffsets = panelDragOffsetsPx,
        activePanelId = activePanelId,
        draggingPanelId = draggingPanelId,
        panelHeightPx = with(density) { 172.dp.toPx() },
        gapPx = with(density) { 12.dp.toPx() }
    )

    fun commitPanelOffsets(offsets: Map<Long, Float>) {
        if (positionedPanels.isEmpty()) return
        panelDragOffsetsPx = panelDragOffsetsPx + positionedPanels.associate { panel ->
            val anchorY = (anchorPositions[panel.id] ?: 0).toFloat()
            val currentY = offsets[panel.id] ?: (anchorY + (panelDragOffsetsPx[panel.id] ?: 0f))
            panel.id to (currentY - anchorY)
        }
    }

    LaunchedEffect(commitOffsetsAfterDrag, panelOffsets, panelIds) {
        if (commitOffsetsAfterDrag) {
            commitPanelOffsets(panelOffsets)
            commitOffsetsAfterDrag = false
        }
    }

    positionedPanels.forEachIndexed { index, panel ->
        key(panel.id) {
            val targetOffset = panelOffsets[panel.id] ?: 0f
            val directPosition = panel.id == draggingPanelId || panel.id == activePanelId
            val phase = panelPhases[panel.id] ?: TextNotePanelPhase.Entering
            FloatingPanelMotion(
                targetOffset = targetOffset,
                directPosition = directPosition,
                modifier = Modifier
                    .fillMaxWidth()
                .zIndex(if (panel.id == (draggingPanelId ?: activePanelId)) 4f else 3f + index * 0.01f)
            ) { motionModifier, currentOffset ->
                PanelAppearanceLayer(
                    phase = phase,
                    onEntered = {
                        if (!panel.isClosing && panelPhases[panel.id] == TextNotePanelPhase.Entering) {
                            panelPhases = panelPhases + (panel.id to TextNotePanelPhase.Visible)
                        }
                    },
                    modifier = motionModifier
                ) {
                    TextNoteFloatingPanel(
                        panel = panel,
                        state = state,
                        accentColor = panelAccentColor(panel.slot),
                        onNoteTextChange = { value ->
                            onUpdatePanel(panel.id) { it.copy(noteText = value, showFullQuote = false) }
                        },
                        onShowFullQuoteChange = { show ->
                            onActivePanelChange(panel.id)
                            onUpdatePanel(panel.id) { it.copy(showFullQuote = show) }
                        },
                        onTitleDragStart = {
                            val rawY = (anchorPositions[panel.id] ?: 0).toFloat() +
                                (panelDragOffsetsPx[panel.id] ?: 0f)
                            val displayedOffset = currentOffset()
                            if (kotlin.math.abs(displayedOffset - rawY) > 0.5f) {
                                panelDragOffsetsPx = panelDragOffsetsPx + (
                                    panel.id to ((panelDragOffsetsPx[panel.id] ?: 0f) + (displayedOffset - rawY))
                                )
                            }
                            if (panel.showFullQuote) {
                                onUpdatePanel(panel.id) { it.copy(showFullQuote = false) }
                            }
                            onActivePanelChange(panel.id)
                            draggingPanelId = panel.id
                        },
                        onTitleDrag = { deltaY ->
                            panelDragOffsetsPx = panelDragOffsetsPx + (
                                panel.id to ((panelDragOffsetsPx[panel.id] ?: 0f) + deltaY)
                            )
                        },
                        onTitleDragEnd = {
                            draggingPanelId = null
                            commitOffsetsAfterDrag = true
                        },
                        onResetPosition = {
                            onActivePanelChange(panel.id)
                            panelDragOffsetsPx = panelDragOffsetsPx + (panel.id to 0f)
                            draggingPanelId = null
                            if (panel.showFullQuote) {
                                onUpdatePanel(panel.id) { it.copy(showFullQuote = false) }
                            }
                        },
                        onMeasuredHeight = { height ->
                            if (panelHeightsPx[panel.id] != height) {
                                panelHeightsPx = panelHeightsPx + (panel.id to height)
                            }
                        },
                        onClose = {
                            onClosePanel(panel.id)
                        },
                        onSaveDraft = { selection ->
                            onSaveDraft(panel, selection)
                        },
                        onSaveExisting = { note ->
                            onSaveExisting(note, panel.noteText)
                        },
                        onDeleteExisting = { note ->
                            onDeleteExisting(panel, note)
                        },
                        onSelectExisting = { note ->
                            onActivePanelChange(panel.id)
                            onUpdatePanel(panel.id) {
                                it.copy(
                                    content = TextNotePanelState.Existing(
                                        noteIds = (it.content as? TextNotePanelState.Existing)
                                            ?.noteIds
                                            ?: listOf(note.note.id),
                                        selectedNoteId = note.note.id
                                    ),
                                    noteText = note.note.note,
                                    showFullQuote = false
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelAppearanceLayer(
    phase: TextNotePanelPhase,
    onEntered: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        when (phase) {
            TextNotePanelPhase.Entering -> {
            progress.snapTo(0f)
            withFrameNanos { }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180)
            )
                onEntered()
            }
            TextNotePanelPhase.Visible -> {
                progress.snapTo(1f)
            }
            TextNotePanelPhase.Exiting -> {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 130)
            )
            }
        }
    }
    val progressValue = progress.value
    val entranceOffsetPx = with(LocalDensity.current) { 18.dp.toPx() }
    val scale = 0.975f + 0.025f * progressValue

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progressValue
            translationY = -entranceOffsetPx * (1f - progressValue)
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    ) {
        content()
    }
}

@Composable
private fun FloatingPanelMotion(
    targetOffset: Float,
    directPosition: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier, () -> Float) -> Unit
) {
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "note-panel-motion"
    )

    val currentDirectPosition by rememberUpdatedState(directPosition)
    val currentTargetOffset by rememberUpdatedState(targetOffset)
    content(
        modifier.graphicsLayer {
            translationY = if (currentDirectPosition) currentTargetOffset else animatedOffset
        }
    ) {
        if (currentDirectPosition) currentTargetOffset else animatedOffset
    }
}

@Composable
private fun QuoteTooltip(
    visible: Boolean,
    quote: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 80)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 80),
                initialOffsetY = { it / 8 }
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 60)) +
            slideOutVertically(
                animationSpec = tween(durationMillis = 60),
                targetOffsetY = { it / 8 }
            ),
        modifier = modifier
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                translationY = -size.height - 8.dp.toPx()
            }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp
        ) {
            Text(
                text = quote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TextNoteFloatingPanel(
    panel: TextNotePanelUi,
    state: ReaderUiState,
    accentColor: Color,
    onNoteTextChange: (String) -> Unit,
    onShowFullQuoteChange: (Boolean) -> Unit,
    onTitleDragStart: () -> Unit,
    onTitleDrag: (Float) -> Unit,
    onTitleDragEnd: () -> Unit,
    onResetPosition: () -> Unit,
    onMeasuredHeight: (Int) -> Unit,
    onClose: () -> Unit,
    onSaveDraft: (TextAnchorSelection) -> Unit,
    onSaveExisting: (ResolvedTextNoteUi) -> Unit,
    onDeleteExisting: (ResolvedTextNoteUi) -> Unit,
    onSelectExisting: (ResolvedTextNoteUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val panelColor = MaterialTheme.colorScheme.surface
    val panelContent = panel.content
    val currentOnShowFullQuoteChange by rememberUpdatedState(onShowFullQuoteChange)
    val currentOnTitleDragStart by rememberUpdatedState(onTitleDragStart)
    val currentOnTitleDrag by rememberUpdatedState(onTitleDrag)
    val currentOnTitleDragEnd by rememberUpdatedState(onTitleDragEnd)

    val existingNotes = when (panelContent) {
        is TextNotePanelState.Draft -> emptyList()
        is TextNotePanelState.Existing -> panelContent.noteIds.mapNotNull { id ->
            state.textNotes.firstOrNull { it.note.id == id }
        }
    }
    val selectedExisting = when (panelContent) {
        is TextNotePanelState.Draft -> null
        is TextNotePanelState.Existing -> existingNotes.firstOrNull { it.note.id == panelContent.selectedNoteId }
            ?: existingNotes.firstOrNull()
    }
    val quote = when (panelContent) {
        is TextNotePanelState.Draft -> panelContent.selection.selectedText
        is TextNotePanelState.Existing -> selectedExisting?.note?.selectedText.orEmpty()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        QuoteTooltip(
            visible = panel.showFullQuote && quote.isNotBlank(),
            quote = quote,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(2f)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onMeasuredHeight(coordinates.size.height)
                },
            shape = RoundedCornerShape(8.dp),
            color = panelColor,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .pointerInput(panel.id) {
                            detectDragGestures(
                                onDragStart = {
                                    currentOnShowFullQuoteChange(false)
                                    currentOnTitleDragStart()
                                },
                                onDragEnd = currentOnTitleDragEnd,
                                onDragCancel = currentOnTitleDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnShowFullQuoteChange(false)
                                    currentOnTitleDrag(dragAmount.y)
                                }
                            )
                        }
                        .clickable(enabled = quote.isNotBlank()) {
                            onShowFullQuoteChange(!panel.showFullQuote)
                        }
                ) {
                    Text(
                        text = quote.panelTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(38.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        panelColor
                                    )
                                )
                            )
                    )
                }
                TextButton(
                    onClick = onResetPosition,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("复位")
                }
                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("关闭")
                }
            }
            if (existingNotes.size > 1 && selectedExisting != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "${existingNotes.indexOf(selectedExisting) + 1}/${existingNotes.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        onClick = {
                            val index = existingNotes.indexOf(selectedExisting)
                            onShowFullQuoteChange(false)
                            onSelectExisting(existingNotes[(index - 1 + existingNotes.size) % existingNotes.size])
                        }
                    ) {
                        Text("上一条")
                    }
                    TextButton(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        onClick = {
                            val index = existingNotes.indexOf(selectedExisting)
                            onShowFullQuoteChange(false)
                            onSelectExisting(existingNotes[(index + 1) % existingNotes.size])
                        }
                    ) {
                        Text("下一条")
                    }
                }
            }
            OutlinedTextField(
                value = panel.noteText,
                onValueChange = {
                    onShowFullQuoteChange(false)
                    onNoteTextChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) onShowFullQuoteChange(false)
                    },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 0.dp)
            ) {
                selectedExisting?.let { note ->
                    TextButton(
                        onClick = { onDeleteExisting(note) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("删除")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                    onClick = {
                        when (panelContent) {
                            is TextNotePanelState.Draft -> onSaveDraft(panelContent.selection)
                            is TextNotePanelState.Existing -> selectedExisting?.let(onSaveExisting)
                        }
                    }
                ) {
                    Text("保存")
                }
            }
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    canUseNote: Boolean,
    isFavorite: Boolean,
    onOpenTree: () -> Unit,
    onOpenNoteWorkbench: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenTree, modifier = Modifier.size(42.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_menu_2),
                contentDescription = "文件树",
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (canUseNote) {
            IconButton(onClick = onOpenNoteWorkbench, modifier = Modifier.size(42.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_note),
                    contentDescription = "笔记工作台",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (canUseNote) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(42.dp)) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) R.drawable.ic_tabler_star_filled else R.drawable.ic_tabler_star
                    ),
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (canUseNote) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(42.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_refresh),
                    contentDescription = "刷新",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(42.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_settings),
                contentDescription = "设置",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun EmptyReaderState(onOpenTree: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请选择文档",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onOpenTree) {
            Text("打开文件树")
        }
    }
}

@Composable
private fun RightSupportingPaneScaffold(
    visible: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(8f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.10f))
                .clickable(onClick = onClose)
        )
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(width),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            content()
        }
    }
}

@Composable
private fun NoteWorkbenchPanel(
    state: ReaderUiState,
    onClose: () -> Unit,
    onOpenTextNote: (ResolvedTextNoteUi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "文档工作台",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = state.supported?.let { "${it.docId}@${it.version}" } ?: "当前没有活动文档",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tabler_chevron_right),
                            contentDescription = "关闭",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (!state.canUseNote) {
                    Text(
                        text = "请选择一个带合法 frontmatter 的文档。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    return@Column
                }

                if (state.note.isNotBlank()) {
                    Text(
                        text = "文档笔记",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                    Text(
                        text = state.note,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = "选区笔记 ${state.textNotes.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )

                if (state.textNotes.isEmpty()) {
                    Text(
                        text = "当前文档还没有选区笔记。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        state.textNotes
                            .sortedWith(
                                compareBy<ResolvedTextNoteUi> {
                                    it.resolution.resolvedStartSourceOffset ?: Int.MAX_VALUE
                                }.thenBy { it.note.updatedAt }
                            )
                            .forEach { textNote ->
                                NoteWorkbenchRow(
                                    textNote = textNote,
                                    onClick = { onOpenTextNote(textNote) }
                                )
                            }
                    }
                }
            }
}

@Composable
private fun NoteWorkbenchRow(
    textNote: ResolvedTextNoteUi,
    onClick: () -> Unit
) {
    val canJump = textNote.resolution.matchType != AnchorMatchType.ORPHANED &&
        textNote.resolution.resolvedStartSourceOffset != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canJump, onClick = onClick)
            .padding(vertical = 9.dp)
    ) {
        Text(
            text = textNote.note.selectedText.panelTitle(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (textNote.note.note.isNotBlank()) {
            Text(
                text = textNote.note.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (canJump) "点击跳转" else "位置需要重新确认",
            style = MaterialTheme.typography.bodySmall,
            color = if (canJump) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
    HorizontalDivider()
}

@Composable
private fun OrphanedTextNotesSection(
    state: ReaderUiState,
    viewModel: ReaderViewModel
) {
    val orphanedNotes = state.textNotes.filter { it.resolution.matchType == AnchorMatchType.ORPHANED }
    if (orphanedNotes.isEmpty()) return
    Text(
        text = "需要重新定位的笔记",
        style = MaterialTheme.typography.titleMedium
    )
    orphanedNotes.forEach { textNote ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Text(
                text = "“${textNote.note.selectedText}”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "位置需要重新确认",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = textNote.note.note.ifBlank { "空笔记" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.deleteTextNote(textNote.note) }) {
                    Text("删除")
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(18.dp))
}

@Composable
private fun DocumentNoteSection(state: ReaderUiState, viewModel: ReaderViewModel) {
    if (!state.canUseNote) return
    Text(
        text = "文档笔记",
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = "绑定当前文档版本；长按正文选中文字可添加选区笔记。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = state.note,
        onValueChange = viewModel::updateNote,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        minLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )
}

private fun notesAtSourceOffset(notes: List<ResolvedTextNoteUi>, sourceOffset: Int): List<ResolvedTextNoteUi> {
    val candidates = notes.filter { note ->
        val start = note.resolution.resolvedStartSourceOffset
        val end = note.resolution.resolvedEndSourceOffset
        start != null &&
            end != null &&
            end > start &&
            sourceOffset in start until end &&
            note.resolution.matchType != AnchorMatchType.ORPHANED
    }
    if (candidates.isEmpty()) return emptyList()

    val hasNested = candidates.any { outer ->
        candidates.any { inner ->
            outer.note.id != inner.note.id &&
                rangeStart(outer) <= rangeStart(inner) &&
                rangeEnd(outer) >= rangeEnd(inner)
        }
    }

    return if (hasNested) {
        candidates.sortedWith(
            compareBy<ResolvedTextNoteUi> { rangeEnd(it) - rangeStart(it) }
                .thenByDescending { rangeStart(it) }
        )
    } else {
        candidates.sortedWith(
            compareByDescending<ResolvedTextNoteUi> { rangeStart(it) }
                .thenBy { rangeEnd(it) - rangeStart(it) }
        )
    }
}

private fun notesForSelectedNote(
    notes: List<ResolvedTextNoteUi>,
    selectedNote: ResolvedTextNoteUi
): List<ResolvedTextNoteUi> {
    val start = selectedNote.resolution.resolvedStartSourceOffset
    val end = selectedNote.resolution.resolvedEndSourceOffset
    val sourceOffset = when {
        start != null && end != null && end > start -> start + ((end - start) / 2)
        start != null -> start
        end != null -> end
        else -> return listOf(selectedNote)
    }
    val related = notesAtSourceOffset(notes, sourceOffset)
    if (related.none { it.note.id == selectedNote.note.id }) return listOf(selectedNote)
    return listOf(selectedNote) + related.filterNot { it.note.id == selectedNote.note.id }
}

private fun rangeStart(note: ResolvedTextNoteUi): Int = note.resolution.resolvedStartSourceOffset ?: 0

private fun rangeEnd(note: ResolvedTextNoteUi): Int = note.resolution.resolvedEndSourceOffset ?: 0

private fun TextNotePanelUi.anchorSourceOffset(state: ReaderUiState): Int? {
    return when (val panelContent = content) {
        is TextNotePanelState.Draft -> panelContent.selection.endSourceOffset
        is TextNotePanelState.Existing -> state.textNotes
            .firstOrNull { it.note.id == panelContent.selectedNoteId }
            ?.resolution
            ?.resolvedEndSourceOffset
    }
}

private fun List<TextNotePanelUi>.resolvedPanelOffsets(
    anchorPositions: Map<Long, Int>,
    panelHeights: Map<Long, Int>,
    panelDragOffsets: Map<Long, Float>,
    activePanelId: Long?,
    draggingPanelId: Long?,
    panelHeightPx: Float,
    gapPx: Float
): Map<Long, Float> {
    val baseOffsets = associate { panel ->
        panel.id to ((anchorPositions[panel.id] ?: 0).toFloat() + (panelDragOffsets[panel.id] ?: 0f))
    }.toMutableMap()
    if (size < 2) return baseOffsets

    val lockedId = draggingPanelId ?: activePanelId ?: last().id
    val lockedPanel = firstOrNull { it.id == lockedId } ?: last()
    val lockedY = baseOffsets[lockedPanel.id] ?: return baseOffsets
    val lockedHeight = panelHeights[lockedPanel.id]?.toFloat() ?: panelHeightPx
    val clearance = gapPx + 2f

    filterNot { it.id == lockedPanel.id }.forEach { otherPanel ->
        val otherY = baseOffsets[otherPanel.id] ?: return@forEach
        val otherHeight = panelHeights[otherPanel.id]?.toFloat() ?: panelHeightPx
        val lockedBottom = lockedY + lockedHeight
        val otherBottom = otherY + otherHeight
        val overlaps = otherY < lockedBottom + clearance && otherBottom + clearance > lockedY
        if (!overlaps) return@forEach

        val aboveY = lockedY - otherHeight - clearance
        val belowY = lockedBottom + clearance
        baseOffsets[otherPanel.id] = if (otherY < lockedY && aboveY >= 0f) {
            aboveY
        } else {
            belowY
        }
    }
    return baseOffsets
}

private fun panelAccentColor(slot: Int): Color {
    return if (slot == 0) Color(0xFF3B82F6) else Color(0xFFF59E0B)
}

private fun String.panelTitle(): String {
    val normalized = trim().replace(Regex("""\s+"""), " ")
    if (normalized.isBlank()) return "新建笔记"
    return normalized
}
