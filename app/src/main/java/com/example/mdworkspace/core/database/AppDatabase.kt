package com.example.mdworkspace.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mdworkspace.data.local.DocumentNoteDao
import com.example.mdworkspace.data.local.DocumentNoteEntity
import com.example.mdworkspace.data.local.DocumentSnapshotDao
import com.example.mdworkspace.data.local.DocumentSnapshotEntity
import com.example.mdworkspace.data.local.DocumentTextNoteDao
import com.example.mdworkspace.data.local.DocumentTextNoteEntity
import com.example.mdworkspace.data.local.FavoriteDao
import com.example.mdworkspace.data.local.FavoriteEntity
import com.example.mdworkspace.data.local.RecentDocumentDao
import com.example.mdworkspace.data.local.RecentDocumentEntity
import com.example.mdworkspace.data.local.RepoEntryDao
import com.example.mdworkspace.data.local.RepoEntryEntity
import com.example.mdworkspace.data.local.UnsupportedMarkdownCacheDao
import com.example.mdworkspace.data.local.UnsupportedMarkdownCacheEntity

@Database(
    entities = [
        RepoEntryEntity::class,
        DocumentSnapshotEntity::class,
        UnsupportedMarkdownCacheEntity::class,
        DocumentNoteEntity::class,
        DocumentTextNoteEntity::class,
        FavoriteEntity::class,
        RecentDocumentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoEntryDao(): RepoEntryDao
    abstract fun documentSnapshotDao(): DocumentSnapshotDao
    abstract fun unsupportedMarkdownCacheDao(): UnsupportedMarkdownCacheDao
    abstract fun documentNoteDao(): DocumentNoteDao
    abstract fun documentTextNoteDao(): DocumentTextNoteDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentDocumentDao(): RecentDocumentDao
}
