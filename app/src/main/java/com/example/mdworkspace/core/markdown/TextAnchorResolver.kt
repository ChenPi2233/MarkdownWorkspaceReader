package com.example.mdworkspace.core.markdown

import com.example.mdworkspace.domain.model.AnchorMatchType
import com.example.mdworkspace.domain.model.AnchorResolutionReason
import com.example.mdworkspace.domain.model.TextAnchorResolution
import com.example.mdworkspace.domain.model.TextAnchorSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TextAnchorResolver {
    private const val CONTEXT_MATCH_MIN_CHARS = 12
    private const val FUZZY_CONTEXT_MIN_CHARS = 24

    fun resolve(anchor: TextAnchorSnapshot, normalizedMarkdownBody: String): TextAnchorResolution {
        if (normalizedMarkdownBody.isEmpty()) {
            return orphan(AnchorResolutionReason.EMPTY_BODY)
        }
        if (anchor.selectedText.isEmpty()) {
            return orphan(AnchorResolutionReason.TEXT_NOT_FOUND)
        }

        val offsetResolution = resolveExactOffset(anchor, normalizedMarkdownBody)
        if (offsetResolution != null) return offsetResolution

        val occurrences = normalizedMarkdownBody.findAll(anchor.selectedText)
        if (occurrences.size == 1) {
            val start = occurrences.first()
            return TextAnchorResolution(
                resolvedStartSourceOffset = start,
                resolvedEndSourceOffset = start + anchor.selectedText.length,
                matchType = AnchorMatchType.TEXT_UNIQUE,
                reason = if (anchor.isLegacy) {
                    AnchorResolutionReason.LEGACY_ANCHOR
                } else {
                    AnchorResolutionReason.OFFSET_TEXT_MISMATCH
                },
                confidence = 0.9f,
                needsReview = false
            )
        }
        if (occurrences.size > 1) {
            return resolveByContext(anchor, normalizedMarkdownBody, occurrences)
        }

        val fuzzy = resolveFuzzyContext(anchor, normalizedMarkdownBody)
        if (fuzzy != null) return fuzzy

        return orphan(
            if (anchor.isLegacy) AnchorResolutionReason.LEGACY_ANCHOR else AnchorResolutionReason.TEXT_NOT_FOUND
        )
    }

    fun resolveLegacy(selectedText: String, normalizedMarkdownBody: String): TextAnchorResolution {
        if (normalizedMarkdownBody.isEmpty()) return orphan(AnchorResolutionReason.EMPTY_BODY)
        if (selectedText.isEmpty()) return orphan(AnchorResolutionReason.LEGACY_ANCHOR)
        val occurrences = normalizedMarkdownBody.findAll(selectedText)
        return if (occurrences.size == 1) {
            val start = occurrences.first()
            TextAnchorResolution(
                resolvedStartSourceOffset = start,
                resolvedEndSourceOffset = start + selectedText.length,
                matchType = AnchorMatchType.TEXT_UNIQUE,
                reason = AnchorResolutionReason.LEGACY_ANCHOR,
                confidence = 0.9f,
                needsReview = false
            )
        } else {
            orphan(AnchorResolutionReason.LEGACY_ANCHOR)
        }
    }

    private fun resolveExactOffset(
        anchor: TextAnchorSnapshot,
        normalizedMarkdownBody: String
    ): TextAnchorResolution? {
        val start = anchor.startSourceOffset
        val end = anchor.endSourceOffset
        if (start < 0 || end < start || end > normalizedMarkdownBody.length) {
            return orphan(AnchorResolutionReason.INVALID_OFFSET)
        }

        val sliceMatches = normalizedMarkdownBody.substring(start, end) == anchor.selectedText
        if (anchor.bodyHash == normalizedMarkdownBody.sha256Hex() && sliceMatches) {
            return TextAnchorResolution(
                resolvedStartSourceOffset = start,
                resolvedEndSourceOffset = end,
                matchType = AnchorMatchType.EXACT_OFFSET,
                reason = AnchorResolutionReason.HASH_MATCH_OFFSET_VALID,
                confidence = 1.0f,
                needsReview = false
            )
        }
        return null
    }

    private fun resolveByContext(
        anchor: TextAnchorSnapshot,
        normalizedMarkdownBody: String,
        occurrences: List<Int>
    ): TextAnchorResolution {
        val ranked = occurrences.map { start ->
            val end = start + anchor.selectedText.length
            ContextCandidate(
                start = start,
                end = end,
                score = contextScore(
                    body = normalizedMarkdownBody,
                    start = start,
                    end = end,
                    prefix = anchor.prefix,
                    suffix = anchor.suffix
                )
            )
        }.sortedByDescending { it.score }

        val best = ranked.first()
        val second = ranked.getOrNull(1)?.score ?: 0
        if (best.score >= 2 && best.score - second >= 1) {
            return TextAnchorResolution(
                resolvedStartSourceOffset = best.start,
                resolvedEndSourceOffset = best.end,
                matchType = AnchorMatchType.CONTEXT_MATCHED,
                reason = AnchorResolutionReason.OFFSET_TEXT_MISMATCH,
                confidence = 0.75f,
                needsReview = false
            )
        }
        return orphan(AnchorResolutionReason.MULTIPLE_CANDIDATES_LOW_CONFIDENCE)
    }

    private fun resolveFuzzyContext(
        anchor: TextAnchorSnapshot,
        normalizedMarkdownBody: String
    ): TextAnchorResolution? {
        val expectedLength = anchor.selectedText.length
        if (expectedLength == 0) return null

        val candidates = mutableListOf<IntRange>()
        val prefixNeedle = anchor.prefix.takeLast(FUZZY_CONTEXT_MIN_CHARS)
        if (prefixNeedle.length >= FUZZY_CONTEXT_MIN_CHARS) {
            normalizedMarkdownBody.findAll(prefixNeedle)
                .mapTo(candidates) { start ->
                    val candidateStart = start + prefixNeedle.length
                    candidateStart until min(normalizedMarkdownBody.length, candidateStart + expectedLength)
                }
        }

        val suffixNeedle = anchor.suffix.take(FUZZY_CONTEXT_MIN_CHARS)
        if (suffixNeedle.length >= FUZZY_CONTEXT_MIN_CHARS) {
            normalizedMarkdownBody.findAll(suffixNeedle)
                .mapTo(candidates) { start ->
                    val candidateStart = max(0, start - expectedLength)
                    candidateStart until start
                }
        }

        val distinct = candidates
            .filter { it.first >= 0 && it.last < normalizedMarkdownBody.length }
            .distinct()
        if (distinct.size != 1) return null

        val range = distinct.first()
        val recoveredLength = range.last - range.first + 1
        val lengthDelta = abs(recoveredLength - expectedLength)
        val maxDelta = max(4, expectedLength / 5)
        if (lengthDelta > maxDelta) return null

        return TextAnchorResolution(
            resolvedStartSourceOffset = range.first,
            resolvedEndSourceOffset = range.last + 1,
            matchType = AnchorMatchType.FUZZY_CONTEXT,
            reason = AnchorResolutionReason.MANUAL_REVIEW_REQUIRED,
            confidence = 0.75f,
            needsReview = true
        )
    }

    private fun contextScore(
        body: String,
        start: Int,
        end: Int,
        prefix: String,
        suffix: String
    ): Int {
        var score = 0
        val prefixNeedle = prefix.takeLast(CONTEXT_MATCH_MIN_CHARS)
        if (prefixNeedle.length >= CONTEXT_MATCH_MIN_CHARS) {
            val before = body.substring(max(0, start - prefix.length), start)
            if (before.endsWith(prefixNeedle)) score += 1
        }
        val suffixNeedle = suffix.take(CONTEXT_MATCH_MIN_CHARS)
        if (suffixNeedle.length >= CONTEXT_MATCH_MIN_CHARS) {
            val after = body.substring(end, min(body.length, end + suffix.length))
            if (after.startsWith(suffixNeedle)) score += 1
        }
        return score
    }

    private fun String.findAll(needle: String): List<Int> {
        if (needle.isEmpty()) return emptyList()
        val matches = mutableListOf<Int>()
        var index = indexOf(needle)
        while (index >= 0) {
            matches += index
            index = indexOf(needle, startIndex = index + 1)
        }
        return matches
    }

    private fun orphan(reason: AnchorResolutionReason): TextAnchorResolution {
        return TextAnchorResolution(
            resolvedStartSourceOffset = null,
            resolvedEndSourceOffset = null,
            matchType = AnchorMatchType.ORPHANED,
            reason = reason,
            confidence = 0.0f,
            needsReview = true
        )
    }

    private data class ContextCandidate(
        val start: Int,
        val end: Int,
        val score: Int
    )
}
