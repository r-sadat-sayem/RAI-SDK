package ai.rakuten.rai.sample.ui.format

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders a Markdown string as a styled Compose layout.
 *
 * Supported syntax:
 * - `# Title` → [MaterialTheme.typography.titleLarge]
 * - `## Subtitle` → [MaterialTheme.typography.titleMedium]
 * - `### Heading` → [MaterialTheme.typography.titleSmall]
 * - ` ```…``` ` (fenced code block) → monospace surface with horizontal scroll
 * - `` `inline code` `` → monospace inline span
 * - `**bold**` → bold span
 * - `*italic*` or `_italic_` → italic span
 * - Blank lines between paragraphs → vertical spacing
 *
 * @param text     Raw Markdown source.
 * @param modifier Optional modifier applied to the outer [Column].
 * @param color    Default text color (inherits from caller when [Color.Unspecified]).
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val blocks = parseMarkdownBlocks(text)

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            when (block) {
                is MarkdownBlock.Heading -> HeadingBlock(block, color)
                is MarkdownBlock.FencedCode -> FencedCodeBlock(block)
                is MarkdownBlock.Paragraph -> ParagraphBlock(block, color)
                is MarkdownBlock.BlankLine -> Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ── Block composables ─────────────────────────────────────────────────────────

@Composable
private fun HeadingBlock(block: MarkdownBlock.Heading, color: Color) {
    val style: TextStyle = when (block.level) {
        1    -> MaterialTheme.typography.titleLarge
        2    -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text  = block.text.trim(),
        style = style,
        fontWeight = FontWeight.Bold,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
    )
}

@Composable
private fun FencedCodeBlock(block: MarkdownBlock.FencedCode) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(codeBackground, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            text       = block.code,
            style      = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ParagraphBlock(block: MarkdownBlock.Paragraph, color: Color) {
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant
    Text(
        text  = buildInlineAnnotatedString(block.text, inlineCodeBackground),
        style = MaterialTheme.typography.bodyMedium,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
    )
}

// ── Inline parser (bold / italic / inline code) ────────────────────────────

private fun buildInlineAnnotatedString(
    text: String,
    @Suppress("UNUSED_PARAMETER") codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // Inline code: `…`
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i++])
                }
            }
            // Bold: **…**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i++])
                }
            }
            // Italic: *…* or _…_
            (text[i] == '*' || text[i] == '_') && !text.startsWith("**", i) -> {
                val delim = text[i]
                val end = text.indexOf(delim, i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i++])
                }
            }
            else -> append(text[i++])
        }
    }
}

// ── Block-level parser ─────────────────────────────────────────────────────

private sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class FencedCode(val language: String, val code: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data object BlankLine : MarkdownBlock()
}

private val HEADING_RE = Regex("""^(#{1,3})\s+(.+)""")
private val FENCE_OPEN = Regex("""^```(\w*)""")

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        val fenceMatch = FENCE_OPEN.find(line)
        if (fenceMatch != null) {
            val language = fenceMatch.groupValues[1]
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            i++ // consume closing ```
            blocks += MarkdownBlock.FencedCode(language, codeLines.joinToString("\n"))
            continue
        }

        // Heading
        val headingMatch = HEADING_RE.find(line)
        if (headingMatch != null) {
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text  = headingMatch.groupValues[2],
            )
            i++
            continue
        }

        // Blank line
        if (line.isBlank()) {
            if (blocks.lastOrNull() !is MarkdownBlock.BlankLine) {
                blocks += MarkdownBlock.BlankLine
            }
            i++
            continue
        }

        // Paragraph — accumulate consecutive non-special lines
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank() || HEADING_RE.containsMatchIn(l) || FENCE_OPEN.containsMatchIn(l)) break
            paragraphLines.add(l)
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString("\n"))
        }
    }

    return blocks
}
