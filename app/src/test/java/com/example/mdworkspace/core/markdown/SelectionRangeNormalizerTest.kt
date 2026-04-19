package com.example.mdworkspace.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionRangeNormalizerTest {
    @Test
    fun trimsAccidentalNextListMarkerAtLineEnd() {
        val body = "对自身特殊性与世界常态并存的理解\n- 不轻易把自身遭遇包装成可被观看的悲剧"
        val rawEnd = body.indexOf("不轻易")

        val range = SelectionRangeNormalizer.normalize(body, body.indexOf("并存"), rawEnd)

        requireNotNull(range)
        assertEquals("并存的理解", body.substring(range.start, range.end))
    }

    @Test
    fun trimsAccidentalLineBreakWhenSelectionLandsAtNextLineStart() {
        val body = "当前版本有意弱化\n她具备某种向总体性靠近的倾向"
        val rawEnd = body.indexOf("她具备")

        val range = SelectionRangeNormalizer.normalize(body, body.indexOf("弱化"), rawEnd)

        requireNotNull(range)
        assertEquals("弱化", body.substring(range.start, range.end))
    }

    @Test
    fun keepsRealCrossLineContentSelection() {
        val body = "上一行末尾\n- 下一行内容"
        val rawEnd = body.indexOf("内容") + "内容".length

        val range = SelectionRangeNormalizer.normalize(body, body.indexOf("末尾"), rawEnd)

        requireNotNull(range)
        assertEquals("末尾\n- 下一行内容", body.substring(range.start, range.end))
    }

    @Test
    fun trimsDanglingHeadingPrefixOnly() {
        val body = "上一段文字\n## 下一节"
        val rawEnd = body.indexOf("下一节")

        val range = SelectionRangeNormalizer.normalize(body, body.indexOf("文字"), rawEnd)

        requireNotNull(range)
        assertEquals("文字", body.substring(range.start, range.end))
    }

    @Test
    fun rejectsOnlyDanglingMarkerSelection() {
        val body = "- "

        val range = SelectionRangeNormalizer.normalize(body, 0, body.length)

        assertNull(range)
    }
}
