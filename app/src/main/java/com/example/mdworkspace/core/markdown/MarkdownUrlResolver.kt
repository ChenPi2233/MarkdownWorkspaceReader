package com.example.mdworkspace.core.markdown

import com.example.mdworkspace.domain.model.RepositoryConfig
import java.net.URLEncoder

object MarkdownUrlResolver {
    fun resolveImageUrl(rawUrl: String, config: RepositoryConfig, documentPath: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (!config.isComplete) return trimmed

        val targetPath = if (trimmed.startsWith("/")) {
            trimmed.trim('/')
        } else {
            val parent = documentPath.trim('/').substringBeforeLast('/', missingDelimiterValue = "")
            normalizePath(listOf(parent, trimmed).filter { it.isNotBlank() }.joinToString("/"))
        }

        return buildString {
            append("https://raw.githubusercontent.com/")
            append(urlEncode(config.owner))
            append("/")
            append(urlEncode(config.repo))
            append("/")
            append(urlEncode(config.branch))
            append("/")
            append(targetPath.split('/').joinToString("/") { urlEncode(it) })
        }
    }

    private fun normalizePath(path: String): String {
        val stack = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
