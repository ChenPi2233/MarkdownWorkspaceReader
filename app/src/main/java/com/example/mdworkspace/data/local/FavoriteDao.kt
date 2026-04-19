package com.example.mdworkspace.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE projectCode = :projectCode AND docId = :docId LIMIT 1")
    suspend fun get(projectCode: String, docId: String): FavoriteEntity?

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE projectCode = :projectCode AND docId = :docId")
    suspend fun delete(projectCode: String, docId: String)
}
