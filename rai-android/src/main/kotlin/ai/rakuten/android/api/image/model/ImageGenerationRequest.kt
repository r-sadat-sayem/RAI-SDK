package ai.rakuten.android.api.image.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root request body sent to the Google Vertex AI `generateContent` endpoint.
 *
 * ```json
 * {
 *   "contents": [{ "role": "user", "parts": [{ "text": "…" }] }],
 *   "generationConfig": {
 *     "responseModalities": ["IMAGE"],
 *     "imageConfig": { "aspectRatio": "16:9" },
 *     "candidateCount": 1
 *   }
 * }
 * ```
 */
@Serializable
public data class ImageGenerationRequest(
    val contents: List<ImageRequestContent>,
    @SerialName("generationConfig") val generationConfig: ImageGenerationConfig,
)

/** A single conversation turn with one or more [parts]. */
@Serializable
public data class ImageRequestContent(
    val role: String = "user",
    val parts: List<ImageRequestPart>,
)

/** A text part inside a [ImageRequestContent]. */
@Serializable
public data class ImageRequestPart(val text: String)

/**
 * Generation parameters for image output.
 *
 * @param responseModalities Must include `"IMAGE"` for image generation.
 * @param imageConfig        Optional sizing hint (aspect ratio).
 * @param candidateCount     Number of candidate images to generate.
 */
@Serializable
public data class ImageGenerationConfig(
    @SerialName("responseModalities") val responseModalities: List<String> = listOf("IMAGE"),
    @SerialName("imageConfig") val imageConfig: ImageConfig? = null,
    @SerialName("candidateCount") val candidateCount: Int = 1,
)

/**
 * Image-specific configuration hints.
 *
 * @param aspectRatio Requested aspect ratio, e.g. `"1:1"`, `"16:9"`, `"9:16"`, `"4:3"`.
 */
@Serializable
public data class ImageConfig(
    @SerialName("aspectRatio") val aspectRatio: String = "1:1",
)
