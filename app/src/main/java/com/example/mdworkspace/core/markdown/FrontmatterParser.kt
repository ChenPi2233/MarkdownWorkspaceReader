package com.example.mdworkspace.core.markdown

import com.example.mdworkspace.domain.model.ParsedDocumentMetadata

enum class UnsupportedReason {
    MissingFrontmatter,
    InvalidYaml,
    MissingRequiredFields
}

sealed interface ParsedMarkdown {
    val body: String

    data class Supported(
        val metadata: ParsedDocumentMetadata,
        override val body: String
    ) : ParsedMarkdown

    data class Unsupported(
        val reason: UnsupportedReason,
        override val body: String
    ) : ParsedMarkdown
}

object FrontmatterParser {
    private val keyValuePattern = Regex("""^([A-Za-z0-9_-]+):\s*(.*)$""")
    private val requiredKeys = listOf("project_code", "doc_id", "title", "version")

    fun parse(markdown: String): ParsedMarkdown {
        val normalized = MarkdownBodyNormalizer.normalize(markdown)
        val frontmatter = normalized.frontmatter
        if (frontmatter == null) {
            return ParsedMarkdown.Unsupported(
                reason = if (normalized.hasUnclosedFrontmatter) {
                    UnsupportedReason.InvalidYaml
                } else {
                    UnsupportedReason.MissingFrontmatter
                },
                body = normalized.body
            )
        }

        val yamlLines = frontmatter.split('\n')
        val body = normalized.body
        val values = linkedMapOf<String, String>()

        for (line in yamlLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue
            }
            val match = keyValuePattern.matchEntire(trimmed)
            if (match == null) {
                return ParsedMarkdown.Unsupported(
                    reason = UnsupportedReason.InvalidYaml,
                    body = body
                )
            }
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim().unquote()
            values[key] = value
        }

        if (requiredKeys.any { values[it].isNullOrBlank() }) {
            return ParsedMarkdown.Unsupported(
                reason = UnsupportedReason.MissingRequiredFields,
                body = body
            )
        }

        return ParsedMarkdown.Supported(
            metadata = ParsedDocumentMetadata(
                projectCode = values.getValue("project_code"),
                docId = values.getValue("doc_id"),
                title = values.getValue("title"),
                version = values.getValue("version"),
                lastUpdated = values["last_updated"]?.takeIf { it.isNotBlank() }
            ),
            body = body
        )
    }

    private fun String.unquote(): String {
        if (length < 2) return this
        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, length - 1)
        } else {
            this
        }
    }
}
