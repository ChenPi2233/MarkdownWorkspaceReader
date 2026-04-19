package com.example.mdworkspace.core.markdown

data class NormalizedMarkdownBody(
    val body: String,
    val frontmatter: String?,
    val hasUnclosedFrontmatter: Boolean
)

object MarkdownBodyNormalizer {
    fun normalize(markdown: String): NormalizedMarkdownBody {
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        if (!startsWithFrontmatterDelimiter(normalized)) {
            return NormalizedMarkdownBody(
                body = normalized,
                frontmatter = null,
                hasUnclosedFrontmatter = false
            )
        }

        val firstLineEnd = normalized.indexOf('\n')
        if (firstLineEnd < 0) {
            return NormalizedMarkdownBody(
                body = normalized,
                frontmatter = null,
                hasUnclosedFrontmatter = true
            )
        }

        val frontmatterStart = firstLineEnd + 1
        var lineStart = frontmatterStart
        while (lineStart <= normalized.length) {
            val lineEnd = normalized.indexOf('\n', lineStart).let { index ->
                if (index < 0) normalized.length else index
            }
            val line = normalized.substring(lineStart, lineEnd)
            if (line.trim() == "---") {
                val bodyStart = if (lineEnd < normalized.length) lineEnd + 1 else lineEnd
                return NormalizedMarkdownBody(
                    body = normalized.substring(bodyStart),
                    frontmatter = normalized.substring(frontmatterStart, lineStart),
                    hasUnclosedFrontmatter = false
                )
            }
            if (lineEnd == normalized.length) break
            lineStart = lineEnd + 1
        }

        return NormalizedMarkdownBody(
            body = normalized,
            frontmatter = null,
            hasUnclosedFrontmatter = true
        )
    }

    private fun startsWithFrontmatterDelimiter(markdown: String): Boolean {
        val firstLineEnd = markdown.indexOf('\n').let { if (it < 0) markdown.length else it }
        return markdown.substring(0, firstLineEnd).trim() == "---"
    }
}
