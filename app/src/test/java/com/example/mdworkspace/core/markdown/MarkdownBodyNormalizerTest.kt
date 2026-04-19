package com.example.mdworkspace.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBodyNormalizerTest {
    @Test
    fun removesClosedFrontmatterAndPreservesFinalNewline() {
        val markdown = "---\r\nproject_code: A\r\n---\r\n# Title\r\nbody\r\n"

        val normalized = MarkdownBodyNormalizer.normalize(markdown)

        assertEquals("# Title\nbody\n", normalized.body)
        assertEquals("project_code: A\n", normalized.frontmatter)
        assertFalse(normalized.hasUnclosedFrontmatter)
    }

    @Test
    fun keepsBodyUnchangedWhenFrontmatterIsUnclosed() {
        val markdown = "---\nproject_code: A\n# Title\n"

        val normalized = MarkdownBodyNormalizer.normalize(markdown)

        assertEquals(markdown, normalized.body)
        assertNull(normalized.frontmatter)
        assertTrue(normalized.hasUnclosedFrontmatter)
    }
}
