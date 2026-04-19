package com.example.mdworkspace.core.network

data class GitHubContentItem(
    val name: String,
    val path: String,
    val type: String,
    val sha: String?,
    val downloadUrl: String?,
    val htmlUrl: String?
)

data class GitHubMarkdownFile(
    val path: String,
    val name: String,
    val sha: String?,
    val downloadUrl: String?,
    val content: String
)
