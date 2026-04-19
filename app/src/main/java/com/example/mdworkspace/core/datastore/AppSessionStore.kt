package com.example.mdworkspace.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSessionDataStore by preferencesDataStore(name = "app_session")

data class ReaderSessionState(
    val lastDocumentPath: String = "",
    val lastScrollY: Int = 0
)

class AppSessionStore(
    private val context: Context
) {
    val readerSessionFlow: Flow<ReaderSessionState> = context.appSessionDataStore.data.map { preferences ->
        ReaderSessionState(
            lastDocumentPath = preferences[LAST_DOCUMENT_PATH].orEmpty(),
            lastScrollY = preferences[LAST_SCROLL_Y] ?: 0
        )
    }

    suspend fun saveReaderPosition(path: String, scrollY: Int) {
        if (path.isBlank()) return
        context.appSessionDataStore.edit { preferences ->
            preferences[LAST_DOCUMENT_PATH] = path
            preferences[LAST_SCROLL_Y] = scrollY.coerceAtLeast(0)
        }
    }

    private companion object {
        val LAST_DOCUMENT_PATH = stringPreferencesKey("last_document_path")
        val LAST_SCROLL_Y = intPreferencesKey("last_scroll_y")
    }
}
