package com.example.mdworkspace.core.markdown

import com.example.mdworkspace.domain.model.AnchorMatchType
import com.example.mdworkspace.domain.model.AnchorResolutionReason
import com.example.mdworkspace.domain.model.TextAnchorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class TextAnchorResolverTest {
    @Test
    fun sameBodyResolvesExactOffset() {
        val body = "Alpha **bold** beta"
        val anchor = anchorFor(body, body.indexOf("bold"), body.indexOf("bold") + "bold".length)

        val resolution = TextAnchorResolver.resolve(anchor, body)

        assertEquals(AnchorMatchType.EXACT_OFFSET, resolution.matchType)
        assertEquals(AnchorResolutionReason.HASH_MATCH_OFFSET_VALID, resolution.reason)
        assertEquals(1.0f, resolution.confidence)
        assertFalse(resolution.needsReview)
    }

    @Test
    fun changedBodyWithUniqueTextResolvesTextUnique() {
        val oldBody = "Alpha unique phrase beta"
        val newBody = "Intro\nAlpha unique phrase beta"
        val start = oldBody.indexOf("unique phrase")
        val anchor = anchorFor(oldBody, start, start + "unique phrase".length)

        val resolution = TextAnchorResolver.resolve(anchor, newBody)

        assertEquals(AnchorMatchType.TEXT_UNIQUE, resolution.matchType)
        assertEquals(0.9f, resolution.confidence)
        assertFalse(resolution.needsReview)
    }

    @Test
    fun repeatedTextUsesClearContextMatch() {
        val body = "first context AAAAA selected after first context\nsecond context BBBBB selected after second context"
        val start = body.lastIndexOf("selected")
        val anchor = anchorFor(body, start, start + "selected".length)
        val changed = body.replace("first context", "moved first context")

        val resolution = TextAnchorResolver.resolve(anchor.copy(bodyHash = "old"), changed)

        assertEquals(AnchorMatchType.CONTEXT_MATCHED, resolution.matchType)
        assertEquals(0.75f, resolution.confidence)
        assertFalse(resolution.needsReview)
    }

    @Test
    fun ambiguousRepeatedTextReturnsOrphaned() {
        val body = "same repeated same repeated"
        val start = body.indexOf("repeated")
        val anchor = anchorFor(body, start, start + "repeated".length)

        val resolution = TextAnchorResolver.resolve(anchor.copy(bodyHash = "old"), body)

        assertEquals(AnchorMatchType.ORPHANED, resolution.matchType)
        assertEquals(AnchorResolutionReason.MULTIPLE_CANDIDATES_LOW_CONFIDENCE, resolution.reason)
        assertTrue(resolution.needsReview)
    }

    @Test
    fun emptyBodyReturnsExplicitReason() {
        val anchor = anchorFor("body", 0, 4)

        val resolution = TextAnchorResolver.resolve(anchor, "")

        assertEquals(AnchorMatchType.ORPHANED, resolution.matchType)
        assertEquals(AnchorResolutionReason.EMPTY_BODY, resolution.reason)
    }

    @Test
    fun invalidOffsetReturnsExplicitReason() {
        val body = "short"
        val anchor = anchorFor(body, 0, 4).copy(startSourceOffset = 10, endSourceOffset = 12)

        val resolution = TextAnchorResolver.resolve(anchor, body)

        assertEquals(AnchorMatchType.ORPHANED, resolution.matchType)
        assertEquals(AnchorResolutionReason.INVALID_OFFSET, resolution.reason)
    }

    @Test
    fun legacyCanResolveOnlyWhenTextIsUnique() {
        val body = "before legacy quote after"

        val resolution = TextAnchorResolver.resolveLegacy("legacy quote", body)

        assertEquals(AnchorMatchType.TEXT_UNIQUE, resolution.matchType)
        assertEquals(AnchorResolutionReason.LEGACY_ANCHOR, resolution.reason)
    }

    private fun anchorFor(body: String, start: Int, end: Int): TextAnchorSnapshot {
        return TextAnchorSnapshot(
            projectCode = "ANLAN-VN",
            docId = "NAR-001",
            docVersion = "0.1.2",
            bodyHash = body.sha256Hex(),
            selectedText = body.substring(start, end),
            startSourceOffset = start,
            endSourceOffset = end,
            prefix = body.substring(max(0, start - 64), start),
            suffix = body.substring(end, min(body.length, end + 64))
        )
    }
}
