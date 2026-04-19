package com.example.mdworkspace.ui.reader

import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.content.Context
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.ActionMode
import android.view.MotionEvent
import android.view.Menu
import android.view.MenuItem
import android.view.ViewConfiguration
import android.widget.TextView
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mdworkspace.core.markdown.SelectionRangeNormalizer
import com.example.mdworkspace.domain.model.TextAnchorSelection

private const val MENU_ADD_TEXT_NOTE = 0x4d57534e
private const val DEBUG_HIGHLIGHT_BOUNDS = false
private const val DEBUG_HIGHLIGHT_TAG = "MdHighlightDebug"

data class RenderedMarkdown(
    val visibleText: Spannable,
    val visibleToSourceOffset: IntArray,
    val sourceToVisibleOffset: IntArray
)

data class TextNoteSourceRange(
    val id: Long,
    val startSourceOffset: Int,
    val endSourceOffset: Int
)

data class TextAnchorPositionRequest(
    val id: Long,
    val sourceOffset: Int
)

private data class HighlightLayout(
    val coverage: IntArray,
    val boundaryOffsets: Set<Int>,
    val islands: List<HighlightIsland>
)

private data class HighlightIsland(
    val line: Int,
    val startOffset: Int,
    val endOffset: Int,
    val rect: RectF,
    val lineBottom: Float,
    val segments: List<HighlightSegment>
)

private data class HighlightSegment(
    val startOffset: Int,
    val endOffset: Int,
    val layer: Int,
    val xStart: Float,
    val xEnd: Float
)

private val noteHighlightGreen = Color.argb(88, 75, 188, 133)
private val noteHighlightYellow = Color.argb(92, 235, 189, 68)
private val noteHighlightRed = Color.argb(82, 222, 91, 86)
private val noteUnderlineGreen = Color.rgb(0, 236, 135)
private val noteUnderlineYellow = Color.rgb(255, 229, 0)

private fun highlightColorForLayer(layer: Int): Int {
    return when ((layer - 1) % 3) {
        0 -> noteHighlightGreen
        1 -> noteHighlightYellow
        else -> noteHighlightRed
    }
}

private fun underlineColorForLayer(layer: Int): Int? {
    return when {
        layer in 4..6 -> noteUnderlineGreen
        layer in 7..9 -> noteUnderlineYellow
        else -> null
    }
}

private fun adjacentBoundaries(noteSourceRanges: List<TextNoteSourceRange>): Set<Int> {
    val starts = noteSourceRanges.map { it.startSourceOffset }.toSet()
    val ends = noteSourceRanges.map { it.endSourceOffset }.toSet()
    return starts.intersect(ends)
}

private fun drawSegment(
    canvas: Canvas,
    paint: Paint,
    rect: RectF,
    roundStart: Boolean,
    roundEnd: Boolean
) {
    val radius = 6f
    when {
        roundStart && roundEnd -> canvas.drawRoundRect(rect, radius, radius, paint)
        roundStart -> {
            val rounded = RectF(rect.left, rect.top, rect.right + radius, rect.bottom)
            canvas.drawRoundRect(rounded, radius, radius, paint)
            canvas.drawRect(rect.left + radius, rect.top, rect.right, rect.bottom, paint)
        }
        roundEnd -> {
            val rounded = RectF(rect.left - radius, rect.top, rect.right, rect.bottom)
            canvas.drawRoundRect(rounded, radius, radius, paint)
            canvas.drawRect(rect.left, rect.top, rect.right - radius, rect.bottom, paint)
        }
        else -> canvas.drawRect(rect, paint)
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    noteSourceRanges: List<TextNoteSourceRange>,
    focusedSourceOffset: Int?,
    anchorRequests: List<TextAnchorPositionRequest>,
    modifier: Modifier = Modifier,
    onAddTextNote: (TextAnchorSelection) -> Unit,
    onSourceOffsetTap: (Int) -> Unit,
    onAnchorYChange: (Map<Long, Int>) -> Unit,
    onFocusedSourceOffsetLocated: (Int) -> Unit,
    onFocusedSourceOffsetConsumed: () -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            var downX = 0f
            var downY = 0f
            var downTime = 0L
            MarkdownTextView(context).apply {
                setTextIsSelectable(true)
                highlightColor = Color.argb(120, 96, 142, 255)
                textSize = 15.8f
                setTextColor(Color.rgb(31, 36, 33))
                setLineSpacing(3f, 1.06f)
                includeFontPadding = true
                setPadding(0, 8, 0, 8)
                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        menu.add(0, MENU_ADD_TEXT_NOTE, 0, "记笔记")
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        if (item.itemId != MENU_ADD_TEXT_NOTE) return false
                        val markdownState = tag as? MarkdownTextTag ?: return false
                        val rendered = markdownState.rendered
                        val visibleStart = selectionStart.coerceAtMost(selectionEnd).coerceAtLeast(0)
                        val visibleEnd = selectionStart.coerceAtLeast(selectionEnd).coerceAtMost(text.length)
                        val rawSourceStart = rendered.visibleToSourceOffset
                            .getOrNull(visibleStart)
                            ?.coerceIn(0, markdownState.source.length)
                            ?: return false
                        val rawSourceEnd = rendered.visibleToSourceOffset
                            .getOrNull(visibleEnd)
                            ?.coerceIn(0, markdownState.source.length)
                            ?: return false
                        val normalizedRange = SelectionRangeNormalizer
                            .normalize(markdownState.source, rawSourceStart, rawSourceEnd)
                            ?: return false
                        val sourceStart = normalizedRange.start
                        val sourceEnd = normalizedRange.end
                        if (sourceEnd <= sourceStart) return false

                        val selected = markdownState.source.substring(sourceStart, sourceEnd)
                        if (selected.isNotBlank()) {
                            onAddTextNote(
                                TextAnchorSelection(
                                    selectedText = selected,
                                    startSourceOffset = sourceStart,
                                    endSourceOffset = sourceEnd
                                )
                            )
                        }
                        mode.finish()
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode) = Unit
                }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            downTime = event.eventTime
                        }

                        MotionEvent.ACTION_UP -> {
                            val moved = kotlin.math.abs(event.x - downX) > touchSlop ||
                                kotlin.math.abs(event.y - downY) > touchSlop
                            val quickTap = event.eventTime - downTime < 260
                            if (!moved && quickTap && selectionStart == selectionEnd) {
                                (tag as? MarkdownTextTag)
                                    ?.sourceOffsetForTouch(this, event)
                                    ?.let(onSourceOffsetTap)
                            }
                        }
                    }
                    false
                }
            }
        },
        update = { textView ->
            val currentTag = textView.tag as? MarkdownTextTag
            val rendered = if (currentTag?.source == markdown) {
                currentTag.rendered
            } else {
                MarkdownSpannableRenderer.render(markdown).also { nextRendered ->
                    textView.text = nextRendered.visibleText
                    textView.tag = MarkdownTextTag(source = markdown, rendered = nextRendered)
                }
            }
            textView.noteSourceRanges = noteSourceRanges
            textView.renderedMarkdown = rendered
            textView.invalidate()
            focusedSourceOffset?.let { sourceOffset ->
                val visibleOffset = rendered.sourceToVisibleOffset
                    .getOrNull(sourceOffset.coerceIn(0, markdown.length))
                    ?.coerceIn(0, rendered.visibleText.length)
                    ?: return@let
                textView.post {
                    (textView.text as? Spannable)?.let { spannable ->
                        Selection.setSelection(spannable, visibleOffset)
                    }
                    textView.requestFocus()
                    textView.bringPointIntoView(visibleOffset)
                    textView.anchorYForSourceOffset(rendered, sourceOffset, markdown.length)
                        ?.let(onFocusedSourceOffsetLocated)
                    onFocusedSourceOffsetConsumed()
                }
            }
            textView.post {
                val positions = anchorRequests.mapNotNull { request ->
                    textView.anchorYForSourceOffset(rendered, request.sourceOffset, markdown.length)
                        ?.let { y -> request.id to y }
                }.toMap()
                onAnchorYChange(positions)
            }
        }
    )
}

private class MarkdownTextView(context: Context) : TextView(context) {
    var renderedMarkdown: RenderedMarkdown? = null
    var noteSourceRanges: List<TextNoteSourceRange> = emptyList()

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastDebugSignature: String? = null
    private var adjustingSelection = false

    override fun onDraw(canvas: Canvas) {
        drawNoteHighlights(canvas)
        super.onDraw(canvas)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!adjustingSelection && selStart != selEnd) {
            snapDanglingSelectionBoundary(selStart, selEnd)
        }
    }

    private fun snapDanglingSelectionBoundary(selStart: Int, selEnd: Int) {
        val markdownState = tag as? MarkdownTextTag ?: return
        val spannable = text as? Spannable ?: return
        val rendered = markdownState.rendered
        val visibleStart = minOf(selStart, selEnd).coerceIn(0, rendered.visibleText.length)
        val visibleEnd = maxOf(selStart, selEnd).coerceIn(0, rendered.visibleText.length)
        val rawSourceStart = rendered.visibleToSourceOffset
            .getOrNull(visibleStart)
            ?.coerceIn(0, markdownState.source.length)
            ?: return
        val rawSourceEnd = rendered.visibleToSourceOffset
            .getOrNull(visibleEnd)
            ?.coerceIn(0, markdownState.source.length)
            ?: return
        val normalized = SelectionRangeNormalizer
            .normalize(markdownState.source, rawSourceStart, rawSourceEnd)
            ?: return
        if (normalized.start == rawSourceStart && normalized.end == rawSourceEnd) return

        val snappedStart = rendered.visibleOffsetForSource(normalized.start)
            ?.coerceIn(0, rendered.visibleText.length)
            ?: return
        val snappedEnd = rendered.visibleOffsetForSource(normalized.end)
            ?.coerceIn(0, rendered.visibleText.length)
            ?: return
        if (snappedEnd <= snappedStart) return

        adjustingSelection = true
        try {
            if (selStart <= selEnd) {
                Selection.setSelection(spannable, snappedStart, snappedEnd)
            } else {
                Selection.setSelection(spannable, snappedEnd, snappedStart)
            }
        } finally {
            adjustingSelection = false
        }
    }

    private fun drawNoteHighlights(canvas: Canvas) {
        val rendered = renderedMarkdown ?: return
        val layout = layout ?: return
        val textLength = rendered.visibleText.length
        if (textLength == 0 || noteSourceRanges.isEmpty()) return

        val highlightLayout = HighlightLayoutEngine.build(layout, rendered, noteSourceRanges)
        if (highlightLayout.islands.isEmpty()) return

        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        highlightLayout.islands.forEach { island ->
            drawHighlightIsland(canvas, island)
        }
        drawDebugHighlightLayout(canvas, layout, highlightLayout)
        drawDebugLineBaselines(canvas, layout)
        logDebugState(highlightLayout)
        canvas.restore()
    }

    private fun drawHighlightIsland(canvas: Canvas, island: HighlightIsland) {
        val radius = 6f
        val islandPath = Path().apply {
            addRoundRect(island.rect, radius, radius, Path.Direction.CW)
        }
        highlightPaint.style = Paint.Style.FILL
        canvas.save()
        canvas.clipPath(islandPath)
        island.segments.forEach { segment ->
            highlightPaint.color = highlightColorForLayer(segment.layer)
            canvas.drawRect(
                segment.xStart,
                island.rect.top,
                segment.xEnd,
                island.rect.bottom,
                highlightPaint
            )
        }
        canvas.restore()

        island.segments.forEach { segment ->
            val underlineColor = underlineColorForLayer(segment.layer) ?: return@forEach
            val underlineTop = minOf(island.lineBottom - 4f, island.rect.bottom + 2.5f)
            if (underlineTop <= island.rect.top) return@forEach
            highlightPaint.color = underlineColor
            canvas.drawRoundRect(
                RectF(segment.xStart, underlineTop, segment.xEnd, underlineTop + 3.5f),
                2.5f,
                2.5f,
                highlightPaint
            )
        }
    }

    private fun drawDebugLineBaselines(canvas: Canvas, layout: android.text.Layout) {
        if (!DEBUG_HIGHLIGHT_BOUNDS) return
        debugPaint.strokeWidth = 1.2f
        debugPaint.style = Paint.Style.STROKE
        debugPaint.color = Color.argb(180, 70, 120, 255)
        for (line in 0 until layout.lineCount) {
            val baseline = layout.getLineBaseline(line).toFloat()
            canvas.drawLine(0f, baseline, layout.width.toFloat(), baseline, debugPaint)
        }
    }

    private fun drawDebugHighlightLayout(
        canvas: Canvas,
        layout: Layout,
        highlightLayout: HighlightLayout
    ) {
        if (!DEBUG_HIGHLIGHT_BOUNDS) return
        debugPaint.style = Paint.Style.STROKE
        debugPaint.strokeWidth = 2f
        highlightLayout.islands.forEach { island ->
            debugPaint.color = Color.argb(220, 255, 0, 180)
            canvas.drawRect(island.rect, debugPaint)
            debugPaint.color = Color.argb(220, 255, 80, 0)
            island.segments.forEach { segment ->
                canvas.drawLine(segment.xStart, island.rect.top, segment.xStart, island.rect.bottom, debugPaint)
                canvas.drawLine(segment.xEnd, island.rect.top, segment.xEnd, island.rect.bottom, debugPaint)
            }
        }
    }

    private fun logDebugState(highlightLayout: HighlightLayout) {
        if (!DEBUG_HIGHLIGHT_BOUNDS) return
        val signature = "${renderedMarkdown?.visibleText?.length}:${noteSourceRanges.joinToString { "${it.id}:${it.startSourceOffset}-${it.endSourceOffset}" }}"
        if (signature == lastDebugSignature) return
        lastDebugSignature = signature
        Log.d(
            DEBUG_HIGHLIGHT_TAG,
            "visibleLength=${highlightLayout.coverage.size}, ranges=${noteSourceRanges.joinToString()}, boundaries=${highlightLayout.boundaryOffsets}, islands=${highlightLayout.islands.size}"
        )
    }
}

private fun RenderedMarkdown.visibleOffsetForSource(sourceOffset: Int): Int? {
    return sourceToVisibleOffset
        .getOrNull(sourceOffset.coerceIn(0, sourceToVisibleOffset.lastIndex))
        ?.coerceIn(0, visibleText.length)
}

private object HighlightLayoutEngine {
    fun build(
        layout: Layout,
        rendered: RenderedMarkdown,
        noteSourceRanges: List<TextNoteSourceRange>
    ): HighlightLayout {
        val textLength = rendered.visibleText.length
        val coverage = IntArray(textLength)
        noteSourceRanges.forEach { range ->
            val start = rendered.visibleOffsetForSource(range.startSourceOffset)
                ?.coerceIn(0, textLength)
                ?: return@forEach
            val end = rendered.visibleOffsetForSource(range.endSourceOffset)
                ?.coerceIn(0, textLength)
                ?: return@forEach
            if (end <= start) return@forEach
            for (index in start until end) {
                coverage[index] = (coverage[index] + 1).coerceAtMost(9)
            }
        }

        val boundaryOffsets = adjacentBoundaries(noteSourceRanges)
            .mapNotNull { boundary -> rendered.visibleOffsetForSource(boundary) }
            .map { it.coerceIn(0, textLength) }
            .toSet()

        val islands = mutableListOf<HighlightIsland>()
        for (line in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(line).coerceIn(0, textLength)
            val lineEnd = layout.getLineEnd(line)
                .withoutTrailingNewline(rendered.visibleText)
                .coerceIn(lineStart, textLength)
            var islandStart = lineStart
            while (islandStart < lineEnd) {
                while (islandStart < lineEnd && coverage[islandStart] == 0) {
                    islandStart += 1
                }
                if (islandStart >= lineEnd) break

                var islandEnd = islandStart + 1
                while (
                    islandEnd < lineEnd &&
                    coverage[islandEnd] > 0 &&
                    islandEnd !in boundaryOffsets
                ) {
                    islandEnd += 1
                }

                val island = buildIsland(layout, line, islandStart, islandEnd, coverage)
                if (island != null) {
                    islands += island
                }
                islandStart = islandEnd
            }
        }

        return HighlightLayout(
            coverage = coverage,
            boundaryOffsets = boundaryOffsets,
            islands = islands
        )
    }

    private fun buildIsland(
        layout: Layout,
        line: Int,
        islandStart: Int,
        islandEnd: Int,
        coverage: IntArray
    ): HighlightIsland? {
        if (islandEnd <= islandStart) return null
        val xStart = layout.getPrimaryHorizontal(islandStart)
        val xEnd = layout.getPrimaryHorizontal(islandEnd)
        val left = minOf(xStart, xEnd)
        val right = maxOf(xStart, xEnd)
        if (right <= left) return null

        val lineTop = layout.getLineTop(line).toFloat()
        val lineBottom = layout.getLineBottom(line).toFloat()
        val baseline = layout.getLineBaseline(line).toFloat()
        val glyphTop = baseline + layout.getLineAscent(line).toFloat()
        val glyphBottom = baseline + layout.getLineDescent(line).toFloat()
        val glyphHeight = (glyphBottom - glyphTop).coerceAtLeast(1f)
        val verticalPadding = (glyphHeight * 0.05f).coerceIn(1.5f, 3.5f)
        val lineInset = (glyphHeight * 0.08f).coerceIn(3f, 6f)
        val rect = RectF(
            left,
            maxOf(lineTop + lineInset, glyphTop - verticalPadding),
            right,
            minOf(lineBottom - lineInset, glyphBottom + verticalPadding)
        )
        if (rect.bottom <= rect.top) return null

        val segments = mutableListOf<HighlightSegment>()
        var segmentStart = islandStart
        while (segmentStart < islandEnd) {
            val layer = coverage[segmentStart]
            var segmentEnd = segmentStart + 1
            while (segmentEnd < islandEnd && coverage[segmentEnd] == layer) {
                segmentEnd += 1
            }
            val segmentXStart = layout.getPrimaryHorizontal(segmentStart)
            val segmentXEnd = layout.getPrimaryHorizontal(segmentEnd)
            val segmentLeft = minOf(segmentXStart, segmentXEnd)
            val segmentRight = maxOf(segmentXStart, segmentXEnd)
            if (segmentRight > segmentLeft && layer > 0) {
                segments += HighlightSegment(
                    startOffset = segmentStart,
                    endOffset = segmentEnd,
                    layer = layer,
                    xStart = segmentLeft,
                    xEnd = segmentRight
                )
            }
            segmentStart = segmentEnd
        }
        if (segments.isEmpty()) return null

        return HighlightIsland(
            line = line,
            startOffset = islandStart,
            endOffset = islandEnd,
            rect = rect,
            lineBottom = lineBottom,
            segments = segments
        )
    }
}

private fun Int.withoutTrailingNewline(text: CharSequence?): Int {
    if (this <= 0 || text == null || this > text.length) return this
    return if (text[this - 1] == '\n') this - 1 else this
}

private data class MarkdownTextTag(
    val source: String,
    val rendered: RenderedMarkdown
) {
    fun sourceOffsetForTouch(textView: TextView, event: MotionEvent): Int? {
        val layout = textView.layout ?: return null
        val x = event.x - textView.totalPaddingLeft + textView.scrollX
        val y = event.y - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(y.toInt().coerceAtLeast(0))
        val visibleOffset = layout.getOffsetForHorizontal(line, x.coerceAtLeast(0f))
        return rendered.visibleToSourceOffset
            .getOrNull(visibleOffset)
            ?.coerceIn(0, source.length)
    }
}

private fun TextView.anchorYForSourceOffset(
    rendered: RenderedMarkdown,
    sourceOffset: Int,
    sourceLength: Int
): Int? {
    val layout = layout ?: return null
    val visibleOffset = rendered.sourceToVisibleOffset
        .getOrNull(sourceOffset.coerceIn(0, sourceLength))
        ?.coerceIn(0, rendered.visibleText.length)
        ?: return null
    val line = layout.getLineForOffset(visibleOffset)
    return totalPaddingTop + layout.getLineBottom(line) + 8
}

private object MarkdownSpannableRenderer {
    private val dividerColor = Color.rgb(184, 192, 186)
    private val codeBackground = Color.argb(42, 70, 83, 75)
    private val mutedColor = Color.rgb(69, 76, 72)
    private val imagePattern = Regex("""!\[([^]]*)]\(([^)]+)\)""")
    private val dividerPattern = Regex("""^([-*_])(?:\s*\1){2,}\s*$""")

    fun render(markdown: String): RenderedMarkdown {
        val out = MappedSpannableBuilder(markdown.length)
        var inCodeBlock = false
        var codeStart = 0

        markdown.sourceLines().forEach { sourceLine ->
            val rawLine = sourceLine.text
            val leading = rawLine.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) rawLine.length else it }
            val trimmed = rawLine.substring(leading).trimEnd()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    applyCodeBlock(out.text, codeStart, out.length)
                    out.appendSynthetic("\n", sourceLine.endExclusive)
                    inCodeBlock = false
                } else {
                    out.appendBlankLineIfNeeded(sourceLine.start)
                    codeStart = out.length
                    inCodeBlock = true
                }
                return@forEach
            }

            if (inCodeBlock) {
                out.appendMapped(rawLine, sourceLine.start)
                out.appendLineBreak(sourceLine)
                return@forEach
            }

            when {
                trimmed.isBlank() -> out.appendBlankLine(sourceLine.start)
                dividerPattern.matches(trimmed) -> appendDivider(out, sourceLine.start)
                trimmed.startsWith("# ") -> appendHeading(out, rawLine, sourceLine.start, leading + 2, 1.36f)
                trimmed.startsWith("## ") -> appendHeading(out, rawLine, sourceLine.start, leading + 3, 1.2f)
                trimmed.startsWith("### ") -> appendHeading(out, rawLine, sourceLine.start, leading + 4, 1.1f)
                trimmed.startsWith(">") -> appendQuote(out, rawLine, sourceLine.start, leading)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> appendListItem(out, rawLine, sourceLine.start, leading)
                tableLike(trimmed) -> appendCodeLine(out, rawLine, sourceLine)
                imageLike(trimmed) -> appendImageLine(out, trimmed, sourceLine.start)
                else -> appendParagraph(out, rawLine, sourceLine)
            }
        }

        if (inCodeBlock) {
            applyCodeBlock(out.text, codeStart, out.length)
        }
        return out.toRenderedMarkdown()
    }

    private fun appendHeading(
        out: MappedSpannableBuilder,
        line: String,
        lineSourceStart: Int,
        contentStartInLine: Int,
        size: Float
    ) {
        out.appendBlankLineIfNeeded(lineSourceStart)
        val start = out.length
        appendInline(out, line, lineSourceStart, contentStartInLine, line.trimEnd().length)
        val end = out.length
        out.text.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.text.setSpan(RelativeSizeSpan(size), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.appendSynthetic("\n\n", lineSourceStart + line.length)
    }

    private fun appendParagraph(out: MappedSpannableBuilder, sourceLine: String, line: SourceLine) {
        appendInline(out, sourceLine, line.start, 0, sourceLine.trimEnd().length)
        out.appendLineBreak(line)
    }

    private fun appendQuote(out: MappedSpannableBuilder, line: String, lineSourceStart: Int, leading: Int) {
        val start = out.length
        out.appendSynthetic("│ ", lineSourceStart + leading)
        val contentStart = (leading + 1).let { index ->
            if (line.getOrNull(index) == ' ') index + 1 else index
        }
        appendInline(out, line, lineSourceStart, contentStart, line.trimEnd().length)
        val end = out.length
        out.text.setSpan(ForegroundColorSpan(mutedColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.appendSynthetic("\n", lineSourceStart + line.length)
    }

    private fun appendListItem(out: MappedSpannableBuilder, line: String, lineSourceStart: Int, leading: Int) {
        out.appendSynthetic("• ", lineSourceStart + leading)
        appendInline(out, line, lineSourceStart, leading + 2, line.trimEnd().length)
        out.appendSynthetic("\n", lineSourceStart + line.length)
    }

    private fun appendDivider(out: MappedSpannableBuilder, sourceOffset: Int) {
        out.appendBlankLineIfNeeded(sourceOffset)
        val start = out.length
        out.appendSynthetic("────────────────────", sourceOffset)
        out.text.setSpan(ForegroundColorSpan(dividerColor), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.appendSynthetic("\n\n", sourceOffset)
    }

    private fun appendCodeLine(out: MappedSpannableBuilder, line: String, sourceLine: SourceLine) {
        val start = out.length
        out.appendMapped(line, sourceLine.start)
        out.appendLineBreak(sourceLine)
        applyCodeBlock(out.text, start, out.length)
    }

    private fun appendImageLine(out: MappedSpannableBuilder, line: String, sourceOffset: Int) {
        val match = imagePattern.matchEntire(line)
        val text = if (match == null) {
            line
        } else {
            val alt = match.groupValues[1].ifBlank { "图片" }
            val url = match.groupValues[2]
            "[$alt] $url"
        }
        out.appendSynthetic(text, sourceOffset)
        out.appendSynthetic("\n", sourceOffset + line.length)
    }

    private fun applyCodeBlock(builder: SpannableStringBuilder, start: Int, end: Int) {
        if (end <= start) return
        builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(BackgroundColorSpan(codeBackground), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendInline(
        out: MappedSpannableBuilder,
        line: String,
        lineSourceStart: Int,
        startInLine: Int,
        endInLine: Int
    ) {
        var index = startInLine
        while (index < endInLine) {
            when {
                line.startsWith("**", index) -> {
                    val close = line.indexOf("**", startIndex = index + 2).takeIf { it in (index + 2) until endInLine }
                    if (close != null) {
                        val start = out.length
                        out.appendMapped(line, lineSourceStart, index + 2, close)
                        out.text.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        index = close + 2
                    } else {
                        out.appendMapped(line, lineSourceStart, index, index + 1)
                        index += 1
                    }
                }

                line.startsWith("__", index) -> {
                    val close = line.indexOf("__", startIndex = index + 2).takeIf { it in (index + 2) until endInLine }
                    if (close != null) {
                        val start = out.length
                        out.appendMapped(line, lineSourceStart, index + 2, close)
                        out.text.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        index = close + 2
                    } else {
                        out.appendMapped(line, lineSourceStart, index, index + 1)
                        index += 1
                    }
                }

                line[index] == '`' -> {
                    val close = line.indexOf('`', startIndex = index + 1).takeIf { it in (index + 1) until endInLine }
                    if (close != null) {
                        val start = out.length
                        out.appendMapped(line, lineSourceStart, index + 1, close)
                        out.text.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.text.setSpan(BackgroundColorSpan(codeBackground), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        index = close + 1
                    } else {
                        out.appendMapped(line, lineSourceStart, index, index + 1)
                        index += 1
                    }
                }

                else -> {
                    out.appendMapped(line, lineSourceStart, index, index + 1)
                    index += 1
                }
            }
        }
    }

    private fun tableLike(line: String): Boolean = line.count { it == '|' } >= 2

    private fun imageLike(line: String): Boolean = imagePattern.matches(line)

    private fun String.sourceLines(): List<SourceLine> {
        if (isEmpty()) return emptyList()
        val lines = mutableListOf<SourceLine>()
        var start = 0
        while (start < length) {
            val newlineIndex = indexOf('\n', start)
            if (newlineIndex < 0) {
                lines += SourceLine(
                    text = substring(start),
                    start = start,
                    endExclusive = length,
                    hasNewline = false
                )
                break
            } else {
                lines += SourceLine(
                    text = substring(start, newlineIndex),
                    start = start,
                    endExclusive = newlineIndex,
                    hasNewline = true
                )
                start = newlineIndex + 1
            }
        }
        return lines
    }

    private data class SourceLine(
        val text: String,
        val start: Int,
        val endExclusive: Int,
        val hasNewline: Boolean
    )

    private class MappedSpannableBuilder(private val sourceLength: Int) {
        val text = SpannableStringBuilder()
        private val sourceStarts = mutableListOf<Int>()
        private val sourceEnds = mutableListOf<Int>()
        val length: Int
            get() = text.length

        fun appendMapped(source: String, lineSourceStart: Int, startInLine: Int = 0, endInLine: Int = source.length) {
            for (index in startInLine until endInLine) {
                appendChar(
                    char = source[index],
                    sourceStart = lineSourceStart + index,
                    sourceEnd = lineSourceStart + index + 1
                )
            }
        }

        fun appendSynthetic(value: String, sourceOffset: Int) {
            value.forEach { char ->
                appendChar(
                    char = char,
                    sourceStart = sourceOffset,
                    sourceEnd = sourceOffset
                )
            }
        }

        fun appendLineBreak(line: SourceLine) {
            if (line.hasNewline) {
                appendChar('\n', line.endExclusive, line.endExclusive + 1)
            } else {
                appendSynthetic("\n", line.endExclusive)
            }
        }

        fun appendBlankLine(sourceOffset: Int) {
            if (text.isNotEmpty() && !text.endsWith("\n\n")) {
                appendSynthetic("\n", sourceOffset)
            }
        }

        fun appendBlankLineIfNeeded(sourceOffset: Int) {
            if (text.isNotEmpty() && !text.endsWith("\n\n")) {
                appendSynthetic("\n", sourceOffset)
            }
        }

        fun toRenderedMarkdown(): RenderedMarkdown {
            val visibleToSource = IntArray(text.length + 1)
            if (sourceStarts.isEmpty()) {
                visibleToSource[0] = 0
            } else {
                for (index in sourceStarts.indices) {
                    visibleToSource[index] = sourceStarts[index].coerceIn(0, sourceLength)
                }
                visibleToSource[text.length] = sourceEnds.last().coerceIn(0, sourceLength)
            }

            val sourceToVisible = IntArray(sourceLength + 1) { -1 }
            sourceStarts.forEachIndexed { visibleIndex, sourceStart ->
                val start = sourceStart.coerceIn(0, sourceLength)
                val end = sourceEnds[visibleIndex].coerceIn(0, sourceLength)
                if (sourceToVisible[start] < 0) {
                    sourceToVisible[start] = visibleIndex
                }
                if (sourceToVisible[end] < 0 || sourceToVisible[end] < visibleIndex + 1) {
                    sourceToVisible[end] = visibleIndex + 1
                }
            }
            var lastVisible = 0
            for (sourceIndex in 0..sourceLength) {
                if (sourceToVisible[sourceIndex] < 0) {
                    sourceToVisible[sourceIndex] = lastVisible
                } else {
                    lastVisible = sourceToVisible[sourceIndex]
                }
            }

            return RenderedMarkdown(
                visibleText = text,
                visibleToSourceOffset = visibleToSource,
                sourceToVisibleOffset = sourceToVisible
            )
        }

        private fun appendChar(char: Char, sourceStart: Int, sourceEnd: Int) {
            text.append(char)
            sourceStarts += sourceStart
            sourceEnds += sourceEnd
        }
    }

}
