package com.example.mdworkspace.core.markdown

data class NormalizedSourceRange(
    val start: Int,
    val end: Int
)

object SelectionRangeNormalizer {
    private val danglingMarkdownPrefix = Regex(
        pattern = """[ \t]*(?:(?:[-*+][ \t]+)?\[[ xX]]|[-*+]|#{1,6}|>|(?:\d+[.)]))[ \t]*"""
    )

    fun normalize(source: String, rawStart: Int, rawEnd: Int): NormalizedSourceRange? {
        var start = rawStart.coerceIn(0, source.length)
        var end = rawEnd.coerceIn(0, source.length)
        if (end < start) {
            val oldStart = start
            start = end
            end = oldStart
        }
        if (end <= start) return null

        end = trimDanglingNextLinePrefix(source, start, end)
        end = trimTrailingLineBreaks(source, start, end)

        if (end <= start) return null
        val selected = source.substring(start, end)
        if (selected.isBlank()) return null
        if (danglingMarkdownPrefix.matches(selected)) return null
        return NormalizedSourceRange(start = start, end = end)
    }

    private fun trimDanglingNextLinePrefix(source: String, start: Int, end: Int): Int {
        var candidateEnd = end
        while (candidateEnd > start) {
            val previousNewline = source.lastIndexOf('\n', startIndex = candidateEnd - 1)
            if (previousNewline < start) return candidateEnd

            val lineStart = previousNewline + 1
            val suffix = source.substring(lineStart, candidateEnd)
            if (!danglingMarkdownPrefix.matches(suffix)) return candidateEnd

            candidateEnd = previousNewline
        }
        return candidateEnd
    }

    private fun trimTrailingLineBreaks(source: String, start: Int, end: Int): Int {
        var candidateEnd = end
        while (candidateEnd > start) {
            val char = source[candidateEnd - 1]
            if (char != '\n' && char != '\r') break
            candidateEnd -= 1
        }
        return candidateEnd
    }
}
