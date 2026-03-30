package ai.rakuten.android.api.image

/**
 * Result of an image generation request.
 *
 * @see RakutenImageApiService.generateImage
 */
sealed class ImageGenerationResult {

    /**
     * Image was generated successfully.
     *
     * @param imageBytes Raw image bytes (decoded from base64).
     * @param mimeType   MIME type reported by the model, e.g. `"image/png"`.
     */
    data class Success(
        val imageBytes: ByteArray,
        val mimeType: String,
    ) : ImageGenerationResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return mimeType == other.mimeType && imageBytes.contentEquals(other.imageBytes)
        }
        override fun hashCode(): Int = 31 * mimeType.hashCode() + imageBytes.contentHashCode()
    }

    /**
     * Image generation failed.
     *
     * @param message Human-readable error description.
     * @param cause   Underlying exception, if available.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : ImageGenerationResult()
}
