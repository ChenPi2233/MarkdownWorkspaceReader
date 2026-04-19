package com.example.mdworkspace.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "repo_entries", primaryKeys = ["path"])
data class RepoEntryEntity(
    val path: String,
    val name: String,
    val type: String,
    val parentPath: String,
    val sha: String?,
    val downloadUrl: String?,
    val htmlUrl: String?,
    val cachedAt: Long
)

@Entity(
    tableName = "document_snapshots",
    primaryKeys = ["projectCode", "docId", "version"],
    indices = [
        Index(value = ["path"]),
        Index(value = ["projectCode", "docId"]),
        Index(value = ["lastOpenedAt"])
    ]
)
data class DocumentSnapshotEntity(
    val projectCode: String,
    val docId: String,
    val version: String,
    val title: String,
    val path: String,
    val content: String,
    val lastUpdated: String?,
    val sha: String?,
    val contentHash: String?,
    val cachedAt: Long,
    val lastOpenedAt: Long
)

@Entity(tableName = "unsupported_markdown_cache", primaryKeys = ["path"])
data class UnsupportedMarkdownCacheEntity(
    val path: String,
    val titleFallback: String,
    val content: String,
    val reason: String,
    val sha: String?,
    val cachedAt: Long,
    val lastOpenedAt: Long
)

@Entity(
    tableName = "document_notes",
    primaryKeys = ["projectCode", "docId", "docVersion"],
    indices = [
        Index(value = ["projectCode", "docId"]),
        Index(value = ["updatedAt"])
    ]
)
data class DocumentNoteEntity(
    val projectCode: String,
    val docId: String,
    val docVersion: String,
    val note: String,
    val updatedAt: Long
)

@Entity(
    tableName = "document_text_notes",
    indices = [
        Index(value = ["projectCode", "docId", "docVersion"]),
        Index(value = ["updatedAt"])
    ]
)
data class DocumentTextNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectCode: String,
    val docId: String,
    val docVersion: String,
    val bodyHash: String,
    val selectedText: String,
    val startSourceOffset: Int?,
    val endSourceOffset: Int?,
    val prefix: String,
    val suffix: String,
    val headingPathJson: String?,
    val blockId: String?,
    val isLegacy: Boolean,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["projectCode", "docId"]
)
data class FavoriteEntity(
    val projectCode: String,
    val docId: String,
    val title: String,
    val lastKnownPath: String?,
    val lastKnownVersion: String?,
    val addedAt: Long
)

@Entity(
    tableName = "recent_documents",
    primaryKeys = ["projectCode", "docId", "docVersion"]
)
data class RecentDocumentEntity(
    val projectCode: String,
    val docId: String,
    val docVersion: String,
    val title: String,
    val path: String,
    val openedAt: Long
)
