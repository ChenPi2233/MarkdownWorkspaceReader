package com.example.mdworkspace.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoEntryDao {
    @Query("SELECT * FROM repo_entries WHERE parentPath = :parentPath ORDER BY type ASC, name COLLATE NOCASE ASC")
    fun observeChildren(parentPath: String): Flow<List<RepoEntryEntity>>

    @Query("SELECT * FROM repo_entries WHERE parentPath = :parentPath ORDER BY type ASC, name COLLATE NOCASE ASC")
    suspend fun children(parentPath: String): List<RepoEntryEntity>

    @Upsert
    suspend fun upsertAll(entries: List<RepoEntryEntity>)

    @Query("DELETE FROM repo_entries WHERE parentPath = :parentPath")
    suspend fun deleteChildren(parentPath: String)
}
