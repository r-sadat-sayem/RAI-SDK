@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package ai.rakuten.android.api.image.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
/**
 * Root response from the Google Vertex AI `generateContent` endpoint.
 *
 * ```json
 * {
 *   "candidates": [{
 *     "content": {
 *       "parts": [{
 *         "inlineData": { "mimeType": "image/png", "data": "<base64>" }
 *       }]
 *     }
 *   }]
 * }
 * ```
 */
@Serializable
data class ImageGenerationResponse(
    val candidates: List<ImageCandidate> = emptyList(),
)

/** A single generation candidate returned by the model. */
@Serializable
data class ImageCandidate(
    val content: ImageResponseContent,
)

/** Content of a [ImageCandidate]. */
@Serializable
data class ImageResponseContent(
    val parts: List<ImageResponsePart> = emptyList(),
)

/**
 * A single part inside a [ImageResponseContent].
 *
 * For image generation responses the image arrives in [inlineData].
 * Text explanations, if any, appear in [text].
 */
@Serializable
data class ImageResponsePart(
    @SerialName("inlineData") val inlineData: InlineImageData? = null,
    val text: String? = null,
)

/**
 * Raw image payload embedded in the response.
 *
 * @param mimeType MIME type of the image, e.g. `"image/png"`.
 * @param data     Base64-encoded image bytes.
 */
@Serializable
data class InlineImageData(
    @SerialName("mimeType") val mimeType: String,
    val data: String,
)
