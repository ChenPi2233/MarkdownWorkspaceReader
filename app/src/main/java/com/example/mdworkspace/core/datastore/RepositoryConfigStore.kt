package com.example.mdworkspace.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mdworkspace.domain.model.RepositoryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.repositoryConfigDataStore by preferencesDataStore(name = "repository_config")

class RepositoryConfigStore(
    private val context: Context
) {
    val configFlow: Flow<RepositoryConfig> = context.repositoryConfigDataStore.data.map { preferences ->
        RepositoryConfig(
            owner = preferences[OWNER].orEmpty(),
            repo = preferences[REPO].orEmpty(),
            branch = preferences[BRANCH] ?: "main",
            token = preferences[TOKEN].orEmpty(),
            rootPath = preferences[ROOT_PATH].orEmpty()
        )
    }

    suspend fun save(config: RepositoryConfig) {
        context.repositoryConfigDataStore.edit { preferences ->
            preferences[OWNER] = config.owner.trim()
            preferences[REPO] = config.repo.trim()
            preferences[BRANCH] = config.branch.trim().ifBlank { "main" }
            preferences[TOKEN] = config.token.trim()
            preferences[ROOT_PATH] = config.rootPath.trim().trim('/')
        }
    }

    private companion object {
        val OWNER = stringPreferencesKey("owner")
        val REPO = stringPreferencesKey("repo")
        val BRANCH = stringPreferencesKey("branch")
        val TOKEN = stringPreferencesKey("token")
        val ROOT_PATH = stringPreferencesKey("root_path")
    }
}
