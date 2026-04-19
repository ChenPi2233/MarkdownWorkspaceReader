package com.example.mdworkspace.data.repo

import com.example.mdworkspace.core.datastore.RepositoryConfigStore
import com.example.mdworkspace.core.markdown.FrontmatterParser
import com.example.mdworkspace.core.markdown.ParsedMarkdown
import com.example.mdworkspace.core.markdown.UnsupportedReason
import com.example.mdworkspace.core.markdown.sha256Hex
import com.example.mdworkspace.core.network.GitHubApi
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
import com.example.mdworkspace.domain.model.DocumentVersionKey
import com.example.mdworkspace.domain.model.RepositoryConfig
import com.example.mdworkspace.domain.model.TextAnchorSelection
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MarkdownWorkspaceRepository(
    private val configStore: RepositoryConfigStore,
    private val gitHubApi: GitHubApi,
    private val repoEntryDao: RepoEntryDao,
    private val snapshotDao: DocumentSnapshotDao,
    private val unsupportedMarkdownCacheDao: UnsupportedMarkdownCacheDao,
    private val noteDao: DocumentNoteDao,
    private val textNoteDao: DocumentTextNoteDao,
    private val favoriteDao: FavoriteDao,
    private val recentDocumentDao: RecentDocumentDao
) {
    val configFlow: Flow<RepositoryConfig> = configStore.configFlow
    val favoritesFlow: Flow<List<FavoriteEntity>> = favoriteDao.observeFavorites()
    val recentDocumentsFlow: Flow<List<RecentDocumentEntity>> = recentDocumentDao.observeRecent(limit = 8)

    fun observeTextNotes(key: DocumentVersionKey): Flow<List<DocumentTextNoteEntity>> {
        return textNoteDao.observeForVersion(
            projectCode = key.projectCode,
            docId = key.docId,
            version = key.version
        )
    }

    fun observeDirectory(path: String): Flow<List<RepoEntryEntity>> {
        return repoEntryDao.observeChildren(path.normalizedPath())
    }

    suspend fun cachedDirectory(path: String): List<RepoEntryEntity> {
        return repoEntryDao.children(path.normalizedPath())
    }

    suspend fun cachedDocument(path: String): OpenDocumentResult? {
        return loadCachedDocument(path.normalizedPath())
    }

    suspend fun saveConfig(config: RepositoryConfig) {
        configStore.save(config)
    }

    suspend fun refreshDirectory(path: String): Result<List<RepoEntryEntity>> {
        val config = configFlow.first()
        if (!config.isComplete) {
            return Result.failure(IllegalStateException("请先配置 GitHub 仓库"))
        }
        val normalizedPath = path.normalizedPath()
        return runCatching {
            val now = System.currentTimeMillis()
            val entries = gitHubApi.getDirectory(config, normalizedPath)
                .filter { it.type == "dir" || it.name.isMarkdownFileName() }
                .map { item ->
                    RepoEntryEntity(
                        path = item.path.normalizedPath(),
                        name = item.name,
                        type = item.type,
                        parentPath = normalizedPath,
                        sha = item.sha,
                        downloadUrl = item.downloadUrl,
                        htmlUrl = item.htmlUrl,
                        cachedAt = now
                    )
                }
            repoEntryDao.deleteChildren(normalizedPath)
            repoEntryDao.upsertAll(entries)
            entries
        }.recoverCatching {
            val cached = repoEntryDao.children(normalizedPath)
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    suspend fun openDocument(path: String, forceRefresh: Boolean = false): Result<OpenDocumentResult> {
        val config = configFlow.first()
        if (!config.isComplete) {
            return Result.failure(IllegalStateException("请先配置 GitHub 仓库"))
        }
        val normalizedPath = path.normalizedPath()
        return runCatching {
            val file = gitHubApi.getMarkdownFile(config, normalizedPath)
            cacheOpenedMarkdown(
                path = file.path.normalizedPath(),
                name = file.name,
                sha = file.sha,
                content = file.content,
                fromCache = false
            )
        }.recoverCatching {
            loadCachedDocument(normalizedPath) ?: throw it
        }
    }

    suspend fun saveNote(key: DocumentVersionKey, note: String): String {
        noteDao.upsert(
            DocumentNoteEntity(
                projectCode = key.projectCode,
                docId = key.docId,
                docVersion = key.version,
                note = note,
                updatedAt = System.currentTimeMillis()
            )
        )
        return note
    }

    suspend fun saveTextNote(key: DocumentVersionKey, body: String, selection: TextAnchorSelection, note: String) {
        val start = selection.startSourceOffset
        val end = selection.endSourceOffset
        require(start >= 0 && end > start && end <= body.length) {
            "选区位置无效"
        }
        val now = System.currentTimeMillis()
        val selectedText = body.substring(start, end)
        textNoteDao.upsert(
            DocumentTextNoteEntity(
                projectCode = key.projectCode,
                docId = key.docId,
                docVersion = key.version,
                bodyHash = body.sha256Hex(),
                selectedText = selectedText,
                startSourceOffset = start,
                endSourceOffset = end,
                prefix = body.substring(max(0, start - ANCHOR_CONTEXT_CHARS), start),
                suffix = body.substring(end, min(body.length, end + ANCHOR_CONTEXT_CHARS)),
                headingPathJson = headingPathBefore(body, start)?.toJsonArray(),
                blockId = null,
                isLegacy = false,
                note = note,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteTextNote(note: DocumentTextNoteEntity) {
        textNoteDao.delete(note)
    }

    suspend fun updateTextNote(note: DocumentTextNoteEntity, value: String) {
        textNoteDao.upsert(
            note.copy(
                note = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleFavorite(snapshot: DocumentSnapshotEntity): Boolean {
        val existing = favoriteDao.get(snapshot.projectCode, snapshot.docId)
        return if (existing == null) {
            favoriteDao.upsert(
                FavoriteEntity(
                    projectCode = snapshot.projectCode,
                    docId = snapshot.docId,
                    title = snapshot.title,
                    lastKnownPath = snapshot.path,
                    lastKnownVersion = snapshot.version,
                    addedAt = System.currentTimeMillis()
                )
            )
            true
        } else {
            favoriteDao.delete(snapshot.projectCode, snapshot.docId)
            false
        }
    }

    suspend fun openFavorite(favorite: FavoriteEntity): Result<OpenDocumentResult> {
        val currentSnapshot = snapshotDao.latestForDocument(favorite.projectCode, favorite.docId)
        val path = currentSnapshot?.path ?: favorite.lastKnownPath
        return if (path.isNullOrBlank()) {
            Result.failure(IllegalStateException("收藏缺少可打开路径，请先刷新仓库"))
        } else {
            openDocument(path = path, forceRefresh = true)
        }
    }

    private suspend fun cacheOpenedMarkdown(
        path: String,
        name: String,
        sha: String?,
        content: String,
        fromCache: Boolean
    ): OpenDocumentResult {
        val parsed = FrontmatterParser.parse(content)
        val now = System.currentTimeMillis()
        return when (parsed) {
            is ParsedMarkdown.Supported -> {
                val metadata = parsed.metadata
                val snapshot = DocumentSnapshotEntity(
                    projectCode = metadata.projectCode,
                    docId = metadata.docId,
                    version = metadata.version,
                    title = metadata.title,
                    path = path,
                    content = parsed.body,
                    lastUpdated = metadata.lastUpdated,
                    sha = sha,
                    contentHash = parsed.body.sha256Hex(),
                    cachedAt = now,
                    lastOpenedAt = now
                )
                snapshotDao.upsert(snapshot)
                recentDocumentDao.upsert(
                    RecentDocumentEntity(
                        projectCode = snapshot.projectCode,
                        docId = snapshot.docId,
                        docVersion = snapshot.version,
                        title = snapshot.title,
                        path = snapshot.path,
                        openedAt = now
                    )
                )
                val note = noteDao.get(snapshot.projectCode, snapshot.docId, snapshot.version)?.note.orEmpty()
                val favorite = favoriteDao.get(snapshot.projectCode, snapshot.docId)
                if (favorite != null) {
                    favoriteDao.upsert(
                        favorite.copy(
                            title = snapshot.title,
                            lastKnownPath = snapshot.path,
                            lastKnownVersion = snapshot.version
                        )
                    )
                }
                OpenDocumentResult.Supported(
                    snapshot = snapshot,
                    body = parsed.body,
                    note = note,
                    isFavorite = favorite != null,
                    fromCache = fromCache
                )
            }

            is ParsedMarkdown.Unsupported -> {
                val cache = UnsupportedMarkdownCacheEntity(
                    path = path,
                    titleFallback = name,
                    content = parsed.body,
                    reason = parsed.reason.name,
                    sha = sha,
                    cachedAt = now,
                    lastOpenedAt = now
                )
                unsupportedMarkdownCacheDao.upsert(cache)
                OpenDocumentResult.Unsupported(
                    cache = cache,
                    body = parsed.body,
                    reason = parsed.reason,
                    fromCache = fromCache
                )
            }
        }
    }

    private suspend fun loadCachedDocument(path: String): OpenDocumentResult? {
        val supported = snapshotDao.latestForPath(path)
        if (supported != null) {
            val note = noteDao.get(supported.projectCode, supported.docId, supported.version)?.note.orEmpty()
            val favorite = favoriteDao.get(supported.projectCode, supported.docId) != null
            return OpenDocumentResult.Supported(
                snapshot = supported,
                body = supported.content,
                note = note,
                isFavorite = favorite,
                fromCache = true
            )
        }
        val unsupported = unsupportedMarkdownCacheDao.get(path) ?: return null
        return OpenDocumentResult.Unsupported(
            cache = unsupported,
            body = unsupported.content,
            reason = runCatching { UnsupportedReason.valueOf(unsupported.reason) }
                .getOrDefault(UnsupportedReason.InvalidYaml),
            fromCache = true
        )
    }

    fun rootPathFor(config: RepositoryConfig): String = config.normalizedRootPath

    private fun String.normalizedPath(): String = trim().trim('/')

    private fun String.isMarkdownFileName(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".md") || lower.endsWith(".markdown")
    }

    private fun headingPathBefore(body: String, sourceOffset: Int): List<String>? {
        val stack = mutableListOf<String>()
        var cursor = 0
        body.lineSequence().forEach { line ->
            if (cursor >= sourceOffset) return@forEach
            val match = headingPattern.matchEntire(line.trim())
            if (match != null) {
                val level = match.groupValues[1].length
                val title = match.groupValues[2].trim()
                while (stack.size >= level) stack.removeAt(stack.lastIndex)
                stack += title
            }
            cursor += line.length + 1
        }
        return stack.takeIf { it.isNotEmpty() }
    }

    private fun List<String>.toJsonArray(): String {
        return joinToString(prefix = "[", postfix = "]") { value ->
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }

    private companion object {
        const val ANCHOR_CONTEXT_CHARS = 64
        val headingPattern = Regex("""^(#{1,6})\s+(.+)$""")
    }
}
