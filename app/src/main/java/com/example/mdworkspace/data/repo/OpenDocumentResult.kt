package com.example.mdworkspace.data.repo

import com.example.mdworkspace.core.markdown.UnsupportedReason
import com.example.mdworkspace.data.local.DocumentSnapshotEntity
import com.example.mdworkspace.data.local.UnsupportedMarkdownCacheEntity

sealed interface OpenDocumentResult {
    val path: String
    val body: String
    val fromCache: Boolean

    data class Supported(
        val snapshot: DocumentSnapshotEntity,
        override val body: String,
        val note: String,
        val isFavorite: Boolean,
        override val fromCache: Boolean
    ) : OpenDocumentResult {
        override val path: String = snapshot.path
    }

    data class Unsupported(
        val cache: UnsupportedMarkdownCacheEntity,
        override val body: String,
        val reason: UnsupportedReason,
        override val fromCache: Boolean
    ) : OpenDocumentResult {
        override val path: String = cache.path
    }
}
