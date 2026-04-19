package com.example.mdworkspace.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentSnapshotDao {
    @Upsert
    suspend fun upsert(snapshot: DocumentSnapshotEntity)

    @Query(
        """
        SELECT * FROM document_snapshots
        WHERE projectCode = :projectCode AND docId = :docId AND version = :version
        LIMIT 1
        """
    )
    suspend fun get(projectCode: String, docId: String, version: String): DocumentSnapshotEntity?

    @Query("SELECT * FROM document_snapshots WHERE path = :path ORDER BY cachedAt DESC LIMIT 1")
    suspend fun latestForPath(path: String): DocumentSnapshotEntity?

    @Query(
        """
        SELECT * FROM document_snapshots
        WHERE projectCode = :projectCode AND docId = :docId
        ORDER BY cachedAt DESC
        LIMIT 1
        """
    )
    suspend fun latestForDocument(projectCode: String, docId: String): DocumentSnapshotEntity?

    @Query("SELECT * FROM document_snapshots ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecentlyOpenedSnapshots(limit: Int): Flow<List<DocumentSnapshotEntity>>
}
