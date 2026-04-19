package com.example.mdworkspace.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DocumentNoteDao {
    @Query(
        """
        SELECT * FROM document_notes
        WHERE projectCode = :projectCode AND docId = :docId AND docVersion = :version
        LIMIT 1
        """
    )
    suspend fun get(projectCode: String, docId: String, version: String): DocumentNoteEntity?

    @Upsert
    suspend fun upsert(note: DocumentNoteEntity)
}
