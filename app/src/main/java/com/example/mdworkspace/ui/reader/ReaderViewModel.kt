package com.example.mdworkspace.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mdworkspace.core.markdown.TextAnchorResolver
import com.example.mdworkspace.data.local.DocumentSnapshotEntity
import com.example.mdworkspace.data.local.DocumentTextNoteEntity
import com.example.mdworkspace.data.local.UnsupportedMarkdownCacheEntity
import com.example.mdworkspace.data.repo.MarkdownWorkspaceRepository
import com.example.mdworkspace.data.repo.OpenDocumentResult
import com.example.mdworkspace.domain.model.DocumentVersionKey
import com.example.mdworkspace.domain.model.RepositoryConfig
import com.example.mdworkspace.domain.model.TextAnchorResolution
import com.example.mdworkspace.domain.model.TextAnchorSelection
import com.example.mdworkspace.domain.model.TextAnchorSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val config: RepositoryConfig = RepositoryConfig(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val supported: DocumentSnapshotEntity? = null,
    val unsupported: UnsupportedMarkdownCacheEntity? = null,
    val body: String = "",
    val note: String = "",
    val textNotes: List<ResolvedTextNoteUi> = emptyList(),
    val isFavorite: Boolean = false,
    val fromCache: Boolean = false
) {
    val canUseNote: Boolean
        get() = supported != null

    val hasDocument: Boolean
        get() = supported != null || unsupported != null || body.isNotBlank()
}

data class ResolvedTextNoteUi(
    val note: DocumentTextNoteEntity,
    val resolution: TextAnchorResolution
)

private fun DocumentSnapshotEntity?.sameVersionAs(other: DocumentSnapshotEntity): Boolean {
    return this != null &&
        projectCode == other.projectCode &&
        docId == other.docId &&
        version == other.version
}

class ReaderViewModel(
    private val path: String,
    private val repository: MarkdownWorkspaceRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()
    private var saveNoteJob: Job? = null
    private var textNotesJob: Job? = null

    init {
        viewModelScope.launch {
            repository.configFlow.collect { config ->
                _state.value = _state.value.copy(config = config)
            }
        }
        if (path.isBlank()) {
            update { copy(isLoading = false) }
        } else {
            load(forceRefresh = true)
        }
    }

    fun load(forceRefresh: Boolean) {
        if (path.isBlank()) {
            update { copy(isLoading = false, error = null) }
            return
        }
        viewModelScope.launch {
            val hadDocument = state.value.hasDocument
            update {
                copy(
                    isLoading = !hadDocument,
                    isRefreshing = hadDocument && forceRefresh,
                    error = null
                )
            }
            val cached = if (hadDocument) null else repository.cachedDocument(path)
            if (cached != null) {
                applyOpenedDocument(cached)
                if (!forceRefresh) return@launch
            }
            val result = repository.openDocument(path = path, forceRefresh = forceRefresh)
            result.fold(
                onSuccess = ::applyOpenedDocument,
                onFailure = { throwable ->
                    update {
                        copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (cached == null) {
                                throwable.message ?: "打开失败"
                            } else {
                                "已显示本地缓存，刷新失败：${throwable.message ?: "网络不可用"}"
                            }
                        )
                    }
                }
            )
        }
    }

    fun updateNote(value: String) {
        val snapshot = state.value.supported ?: return
        update { copy(note = value) }
        saveNoteJob?.cancel()
        saveNoteJob = viewModelScope.launch {
            delay(350)
            repository.saveNote(
                key = DocumentVersionKey(
                    projectCode = snapshot.projectCode,
                    docId = snapshot.docId,
                    version = snapshot.version
                ),
                note = value
            )
        }
    }

    fun toggleFavorite() {
        val snapshot = state.value.supported ?: return
        viewModelScope.launch {
            val isFavorite = repository.toggleFavorite(snapshot)
            update { copy(isFavorite = isFavorite) }
        }
    }

    fun addTextNote(selection: TextAnchorSelection, note: String) {
        val snapshot = state.value.supported ?: return
        viewModelScope.launch {
            repository.saveTextNote(
                key = DocumentVersionKey(
                    projectCode = snapshot.projectCode,
                    docId = snapshot.docId,
                    version = snapshot.version
                ),
                body = state.value.body,
                selection = selection,
                note = note
            )
        }
    }

    fun deleteTextNote(note: DocumentTextNoteEntity) {
        viewModelScope.launch {
            repository.deleteTextNote(note)
        }
    }

    fun updateTextNote(note: DocumentTextNoteEntity, value: String) {
        viewModelScope.launch {
            repository.updateTextNote(note, value)
        }
    }

    private fun applyOpenedDocument(opened: OpenDocumentResult) {
        when (opened) {
            is OpenDocumentResult.Supported -> {
                update {
                    copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        supported = opened.snapshot,
                        unsupported = null,
                        body = opened.body,
                        note = opened.note,
                        textNotes = if (supported.sameVersionAs(opened.snapshot)) textNotes else emptyList(),
                        isFavorite = opened.isFavorite,
                        fromCache = opened.fromCache
                    )
                }
                observeTextNotes(opened.snapshot)
            }

            is OpenDocumentResult.Unsupported -> {
                textNotesJob?.cancel()
                update {
                    copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        supported = null,
                        unsupported = opened.cache,
                        body = opened.body,
                        note = "",
                        textNotes = emptyList(),
                        isFavorite = false,
                        fromCache = opened.fromCache
                    )
                }
            }
        }
    }

    private fun observeTextNotes(snapshot: DocumentSnapshotEntity) {
        textNotesJob?.cancel()
        textNotesJob = viewModelScope.launch {
            repository.observeTextNotes(
                DocumentVersionKey(
                    projectCode = snapshot.projectCode,
                    docId = snapshot.docId,
                    version = snapshot.version
                )
            ).collect { notes ->
                update {
                    copy(
                        textNotes = notes.map { note ->
                            ResolvedTextNoteUi(
                                note = note,
                                resolution = resolveTextNote(note, snapshot)
                            )
                        }
                    )
                }
            }
        }
    }

    private fun resolveTextNote(
        note: DocumentTextNoteEntity,
        snapshot: DocumentSnapshotEntity
    ): TextAnchorResolution {
        val start = note.startSourceOffset
        val end = note.endSourceOffset
        if (start == null || end == null || note.bodyHash.isBlank()) {
            return TextAnchorResolver.resolveLegacy(
                selectedText = note.selectedText,
                normalizedMarkdownBody = snapshot.content
            )
        }
        return TextAnchorResolver.resolve(
            TextAnchorSnapshot(
                projectCode = note.projectCode,
                docId = note.docId,
                docVersion = note.docVersion,
                bodyHash = note.bodyHash,
                selectedText = note.selectedText,
                startSourceOffset = start,
                endSourceOffset = end,
                prefix = note.prefix,
                suffix = note.suffix,
                headingPath = null,
                blockId = note.blockId,
                isLegacy = note.isLegacy
            ),
            normalizedMarkdownBody = snapshot.content
        )
    }

    private fun update(block: ReaderUiState.() -> ReaderUiState) {
        _state.value = _state.value.block()
    }

    companion object {
        fun factory(path: String, repository: MarkdownWorkspaceRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ReaderViewModel(path = path, repository = repository) as T
                }
            }
        }
    }
}
