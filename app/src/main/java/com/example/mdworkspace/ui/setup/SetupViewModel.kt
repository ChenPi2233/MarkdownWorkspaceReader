package com.example.mdworkspace.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mdworkspace.data.repo.MarkdownWorkspaceRepository
import com.example.mdworkspace.domain.model.RepositoryConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SetupUiState(
    val ownerRepo: String = "",
    val branch: String = "main",
    val token: String = "",
    val rootPath: String = "",
    val isConnecting: Boolean = false,
    val message: String? = null
)

class SetupViewModel(
    private val repository: MarkdownWorkspaceRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = repository.configFlow.first()
            _state.value = SetupUiState(
                ownerRepo = if (config.owner.isBlank() && config.repo.isBlank()) "" else "${config.owner}/${config.repo}",
                branch = config.branch,
                token = config.token,
                rootPath = config.rootPath
            )
        }
    }

    fun updateOwnerRepo(value: String) = update { copy(ownerRepo = value, message = null) }
    fun updateBranch(value: String) = update { copy(branch = value, message = null) }
    fun updateToken(value: String) = update { copy(token = value, message = null) }
    fun updateRootPath(value: String) = update { copy(rootPath = value, message = null) }

    fun connect(onConnected: (String) -> Unit) {
        val current = state.value
        val parts = current.ownerRepo.trim().split('/').filter { it.isNotBlank() }
        if (parts.size != 2) {
            update { copy(message = "请输入 owner/repo") }
            return
        }

        val config = RepositoryConfig(
            owner = parts[0],
            repo = parts[1],
            branch = current.branch.ifBlank { "main" },
            token = current.token,
            rootPath = current.rootPath
        )
        viewModelScope.launch {
            update { copy(isConnecting = true, message = null) }
            repository.saveConfig(config)
            val rootPath = repository.rootPathFor(config)
            val result = repository.refreshDirectory(rootPath)
            update {
                copy(
                    isConnecting = false,
                    message = result.exceptionOrNull()?.message
                )
            }
            if (result.isSuccess) onConnected(rootPath)
        }
    }

    private fun update(block: SetupUiState.() -> SetupUiState) {
        _state.value = _state.value.block()
    }

    companion object {
        fun factory(repository: MarkdownWorkspaceRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SetupViewModel(repository) as T
                }
            }
        }
    }
}
