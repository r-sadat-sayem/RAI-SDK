package ai.rakuten.rai.sample.ui.format

/**
 * Typed content model for a single chat message.
 *
 * Using a sealed class allows the UI to render each content type with
 * appropriate styling rather than treating everything as plain text.
 */
sealed class MessageContent {

    /** Plain, unformatted user-typed text. */
    data class PlainText(val value: String) : MessageContent()

    /**
     * Assistant reply that may contain Markdown syntax.
     *
     * The `MarkdownText` composable renders headings, bold, italic, inline
     * code, and fenced code blocks from this value.
     */
    data class Markdown(val value: String) : MessageContent()

    /**
     * A standalone fenced code block with optional [language] hint.
     *
     * Use this when the entire message *is* a code snippet (e.g. a tool
     * that returns source code).
     */
    data class Code(val code: String, val language: String = "") : MessageContent()

    /**
     * A generated image returned by the image generation API.
     *
     * @param bytes    Raw image bytes (already decoded from base64).
     * @param mimeType MIME type reported by the model, e.g. `"image/png"`.
     */
    data class GeneratedImage(
        val bytes: ByteArray,
        val mimeType: String,
    ) : MessageContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GeneratedImage) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
    }

    /** Convenience: extract displayable text for logging / accessibility. */
    val previewText: String
        get() = when (this) {
            is PlainText      -> value
            is Markdown       -> value
            is Code           -> code
            is GeneratedImage -> "[Generated image · $mimeType]"
        }
}
