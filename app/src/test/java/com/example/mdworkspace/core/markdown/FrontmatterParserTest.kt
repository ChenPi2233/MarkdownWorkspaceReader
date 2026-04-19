package com.example.mdworkspace.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontmatterParserTest {
    @Test
    fun parsesRequiredIdentityFieldsAndRemovesFrontmatterFromBody() {
        val markdown = """
            ---
            project_code: ANLAN-VN
            doc_id: NAR-001
            title: 叙事总纲
            version: 0.1.2
            last_updated: 2026-04-18
            depends_on:
              - PJT-001
            ---
            # 正文
        """.trimIndent()

        val parsed = FrontmatterParser.parse(markdown)

        assertTrue(parsed is ParsedMarkdown.Supported)
        parsed as ParsedMarkdown.Supported
        assertEquals("ANLAN-VN", parsed.metadata.projectCode)
        assertEquals("NAR-001", parsed.metadata.docId)
        assertEquals("叙事总纲", parsed.metadata.title)
        assertEquals("0.1.2", parsed.metadata.version)
        assertEquals("2026-04-18", parsed.metadata.lastUpdated)
        assertEquals("# 正文", parsed.body)
    }

    @Test
    fun keepsVersionAsExactStringWithoutSemverInterpretation() {
        val markdown = """
            ---
            project_code: ANLAN-VN
            doc_id: NAR-001
            title: 叙事总纲
            version: 0.1.3-alpha+local
            ---
            body
        """.trimIndent()

        val parsed = FrontmatterParser.parse(markdown) as ParsedMarkdown.Supported

        assertEquals("0.1.3-alpha+local", parsed.metadata.version)
    }

    @Test
    fun unsupportedWhenFrontmatterIsMissing() {
        val parsed = FrontmatterParser.parse("# Plain Markdown")

        assertTrue(parsed is ParsedMarkdown.Unsupported)
        parsed as ParsedMarkdown.Unsupported
        assertEquals(UnsupportedReason.MissingFrontmatter, parsed.reason)
        assertEquals("# Plain Markdown", parsed.body)
    }

    @Test
    fun unsupportedWhenRequiredIdentityFieldsAreMissing() {
        val markdown = """
            ---
            project_code: ANLAN-VN
            title: 叙事总纲
            version: 0.1.2
            ---
            body
        """.trimIndent()

        val parsed = FrontmatterParser.parse(markdown)

        assertTrue(parsed is ParsedMarkdown.Unsupported)
        parsed as ParsedMarkdown.Unsupported
        assertEquals(UnsupportedReason.MissingRequiredFields, parsed.reason)
        assertEquals("body", parsed.body)
    }
}
