package com.example.mdworkspace.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mdworkspace.data.local.FavoriteEntity
import com.example.mdworkspace.data.local.RecentDocumentEntity
import com.example.mdworkspace.data.local.RepoEntryEntity
import com.example.mdworkspace.data.repo.MarkdownWorkspaceRepository
import com.example.mdworkspace.domain.model.RepositoryConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val config: RepositoryConfig = RepositoryConfig(),
    val entriesByParent: Map<String, List<RepoEntryEntity>> = emptyMap(),
    val expandedDirectories: Set<String> = emptySet(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val recentDocuments: List<RecentDocumentEntity> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

class WorkspaceViewModel(
    private val path: String,
    private val repository: MarkdownWorkspaceRepository
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.configFlow.collect { config ->
                update { copy(config = config) }
            }
        }
        viewModelScope.launch {
            repository.favoritesFlow.collect { favorites ->
                update { copy(favorites = favorites) }
            }
        }
        viewModelScope.launch {
            repository.recentDocumentsFlow.collect { recentDocuments ->
                update { copy(recentDocuments = recentDocuments) }
            }
        }
        refresh()
    }

    fun refresh() {
        refreshDirectory(path)
    }

    fun toggleDirectory(entry: RepoEntryEntity) {
        if (entry.path in state.value.expandedDirectories) {
            update { copy(expandedDirectories = expandedDirectories - entry.path) }
        } else {
            update { copy(expandedDirectories = expandedDirectories + entry.path) }
            refreshDirectory(entry.path)
        }
    }

    fun refreshExpandedTree() {
        viewModelScope.launch {
            update { copy(isLoading = true, message = null) }
            val directories = state.value.expandedDirectories + path
            var message: String? = null
            directories.forEach { directory ->
                val cached = repository.cachedDirectory(directory)
                if (cached.isNotEmpty()) {
                    update {
                        copy(entriesByParent = entriesByParent + (directory to cached))
                    }
                }
                val result = repository.refreshDirectory(directory)
                result.onSuccess { entries ->
                    update {
                        copy(entriesByParent = entriesByParent + (directory to entries))
                    }
                }.onFailure {
                    message = it.message
                }
            }
            update {
                copy(
                    isLoading = false,
                    message = message
                )
            }
        }
    }

    fun openFavorite(favorite: FavoriteEntity, onOpenDocument: (String) -> Unit) {
        val path = favorite.lastKnownPath
        if (path.isNullOrBlank()) {
            update { copy(message = "收藏缺少可打开路径，请刷新仓库") }
        } else {
            onOpenDocument(path)
        }
    }

    private fun update(block: WorkspaceUiState.() -> WorkspaceUiState) {
        _state.value = _state.value.block()
    }

    private fun refreshDirectory(directory: String) {
        viewModelScope.launch {
            val cached = repository.cachedDirectory(directory)
            update {
                copy(
                    isLoading = cached.isEmpty(),
                    message = null,
                    entriesByParent = if (cached.isNotEmpty()) {
                        entriesByParent + (directory to cached)
                    } else {
                        entriesByParent
                    }
                )
            }
            val result = repository.refreshDirectory(directory)
            update {
                if (result.isSuccess) {
                    copy(
                        isLoading = false,
                        entriesByParent = entriesByParent + (directory to result.getOrThrow()),
                        message = null
                    )
                } else {
                    copy(
                        isLoading = false,
                        message = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    companion object {
        fun factory(path: String, repository: MarkdownWorkspaceRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WorkspaceViewModel(path = path, repository = repository) as T
                }
            }
        }
    }
}
