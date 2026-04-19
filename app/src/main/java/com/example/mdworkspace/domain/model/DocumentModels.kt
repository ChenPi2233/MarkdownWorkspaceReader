package com.example.mdworkspace.domain.model

data class DocumentIdentity(
    val projectCode: String,
    val docId: String
)

data class DocumentVersionKey(
    val projectCode: String,
    val docId: String,
    val version: String
)

data class ParsedDocumentMetadata(
    val projectCode: String,
    val docId: String,
    val title: String,
    val version: String,
    val lastUpdated: String?
) {
    val identity: DocumentIdentity
        get() = DocumentIdentity(projectCode = projectCode, docId = docId)

    val versionKey: DocumentVersionKey
        get() = DocumentVersionKey(projectCode = projectCode, docId = docId, version = version)
}

data class MarkdownDocumentSnapshot(
    val projectCode: String,
    val docId: String,
    val version: String,
    val title: String,
    val path: String,
    val content: String,
    val lastUpdated: String?,
    val cachedAt: Long,
    val lastOpenedAt: Long,
    val sha: String?,
    val contentHash: String?
)

data class RepositoryConfig(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val token: String = "",
    val rootPath: String = ""
) {
    val isComplete: Boolean
        get() = owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank()

    val displayName: String
        get() = if (owner.isBlank() || repo.isBlank()) "未配置仓库" else "$owner/$repo"

    val normalizedRootPath: String
        get() = rootPath.trim().trim('/')
}
