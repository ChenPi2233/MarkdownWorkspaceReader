package com.example.mdworkspace.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface UnsupportedMarkdownCacheDao {
    @Upsert
    suspend fun upsert(cache: UnsupportedMarkdownCacheEntity)

    @Query("SELECT * FROM unsupported_markdown_cache WHERE path = :path LIMIT 1")
    suspend fun get(path: String): UnsupportedMarkdownCacheEntity?
}
