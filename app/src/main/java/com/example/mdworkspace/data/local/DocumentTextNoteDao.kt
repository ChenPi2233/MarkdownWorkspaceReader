package com.example.mdworkspace.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentTextNoteDao {
    @Query(
        """
        SELECT * FROM document_text_notes
        WHERE projectCode = :projectCode AND docId = :docId AND docVersion = :version
        ORDER BY updatedAt DESC
        """
    )
    fun observeForVersion(projectCode: String, docId: String, version: String): Flow<List<DocumentTextNoteEntity>>

    @Upsert
    suspend fun upsert(note: DocumentTextNoteEntity)

    @Delete
    suspend fun delete(note: DocumentTextNoteEntity)
}
