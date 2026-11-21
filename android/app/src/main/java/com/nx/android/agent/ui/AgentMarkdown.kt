package com.nx.android.agent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Inline markup: **bold**, `code`, *italic* — bold first in the alternation so ** wins over *. */
private val INLINE = Regex("""\*\*(.+?)\*\*|`([^`\n]+)`|\*([^*\n]+?)\*""")

private val HEADING = Regex("""^(#{1,4})\s+(.*)$""")
private val BULLET = Regex("""^\s*[-*•]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")

/**
 * The assistant's answers rendered as a Markdown subset — headings, bullet and numbered lists,
 * fenced code blocks, bold, italic and inline code — the shapes the models actually produce.
 * Hand-rolled on [AnnotatedString] (zero new dependencies): good enough for chat answers, and
 * a library can replace it later behind this same composable.
 */
@Composable
internal fun AgentMarkdownText(text: String, modifier: Modifier = Modifier) {
    val codeBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)
    val annotated = remember(text) { markdownToAnnotated(text, codeBg) }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.5.sp),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

internal fun markdownToAnnotated(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val mono = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
    var inFence = false
    for (raw in text.lines()) {
        if (raw.trimStart().startsWith("```")) {
            inFence = !inFence
            append('\n')
            continue
        }
        if (inFence) {
            withStyle(mono) { append(raw) }
            append('\n')
            continue
        }
        HEADING.find(raw)?.let { m ->
            val level = m.groupValues[1].length
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (18.5 - level * 1.5).sp)) {
                append(m.groupValues[2])
            }
            append('\n')
            return@let
        } ?: run {
            BULLET.find(raw)?.let { m ->
                append("•  ")
                inline(m.groupValues[1], codeBg)
                append('\n')
            } ?: run {
                NUMBERED.find(raw)?.let { m ->
                    append("${m.groupValues[1]}. ")
                    inline(m.groupValues[2], codeBg)
                    append('\n')
                } ?: run {
                    inline(raw, codeBg)
                    append('\n')
                }
            }
        }
    }
}

/** One line's inline markup into styled spans. */
private fun AnnotatedString.Builder.inline(s: String, codeBg: Color) {
    var last = 0
    for (m in INLINE.findAll(s)) {
        append(s.substring(last, m.range.first))
        val (style, content) = when {
            m.groupValues[1].isNotEmpty() -> SpanStyle(fontWeight = FontWeight.Bold) to m.groupValues[1]
            m.groupValues[2].isNotEmpty() ->
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg) to m.groupValues[2]
            else -> SpanStyle(fontStyle = FontStyle.Italic) to m.groupValues[3]
        }
        withStyle(style) { append(content) }
        last = m.range.last + 1
    }
    append(s.substring(last))
}
