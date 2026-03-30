package ai.rakuten.android.api.image

import ai.rakuten.android.api.RakutenApiEndpoints
import ai.rakuten.android.api.image.model.ImageConfig
import ai.rakuten.android.api.image.model.ImageGenerationConfig
import ai.rakuten.android.api.image.model.ImageGenerationRequest
import ai.rakuten.android.api.image.model.ImageGenerationResponse
import ai.rakuten.android.api.image.model.ImageRequestContent
import ai.rakuten.android.api.image.model.ImageRequestPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * HTTP service that calls the Rakuten AI Gateway Google Vertex AI endpoint to generate images.
 *
 * Uses `gemini-3.1-flash-image-preview` by default. The response contains a base64-encoded
 * image that is decoded into raw [ByteArray] before being returned.
 *
 * ### Usage
 * ```kotlin
 * val service = RakutenImageApiService(apiKey = gatewayKey, httpClient = get())
 *
 * when (val result = service.generateImage(prompt = "A cat on a surfboard")) {
 *     is ImageGenerationResult.Success -> displayImage(result.imageBytes)
 *     is ImageGenerationResult.Error   -> showError(result.message)
 * }
 * ```
 *
 * @param apiKey     Your `RAKUTEN_AI_GATEWAY_KEY`, placed in the `Authorization` header.
 * @param httpClient OkHttp client — inject the Koin-provided instance so logging works.
 */
class RakutenImageApiService(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true   // required: fields with default values (e.g. role = "user") must be emitted
    }

    /**
     * Generates an image from the given text [prompt].
     *
     * @param prompt      Text description of the desired image.
     * @param model       Image model wire name. Defaults to [RakutenApiEndpoints.DEFAULT_IMAGE_MODEL].
     * @param aspectRatio Requested aspect ratio, e.g. `"1:1"`, `"16:9"`, `"9:16"`, `"4:3"`.
     * @return [ImageGenerationResult.Success] with decoded image bytes, or
     *         [ImageGenerationResult.Error] on failure.
     */
    suspend fun generateImage(
        prompt: String,
        model: String = RakutenApiEndpoints.DEFAULT_IMAGE_MODEL,
        aspectRatio: String = "1:1",
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = ImageGenerationRequest(
                contents = listOf(
                    ImageRequestContent(parts = listOf(ImageRequestPart(text = prompt)))
                ),
                generationConfig = ImageGenerationConfig(
                    responseModalities = listOf("IMAGE"),
                    imageConfig = ImageConfig(aspectRatio = aspectRatio),
                    candidateCount = 1,
                ),
            )

            val call = httpClient.newCall(buildRequest(requestBody, model))
            cancelOnCoroutineCompletion(call)

            call.execute().use { response ->
                val body = response.body?.string()
                    ?: return@withContext ImageGenerationResult.Error("Empty response (HTTP ${response.code})")

                if (!response.isSuccessful) {
                    return@withContext ImageGenerationResult.Error("HTTP ${response.code}: $body")
                }

                val parsed = json.decodeFromString<ImageGenerationResponse>(body)
                val inlineData = parsed.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull { it.inlineData != null }
                    ?.inlineData
                    ?: return@withContext ImageGenerationResult.Error("No image data in response")

                val imageBytes = Base64.getDecoder().decode(inlineData.data)
                ImageGenerationResult.Success(imageBytes = imageBytes, mimeType = inlineData.mimeType)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImageGenerationResult.Error(
                message = e.message ?: "Image generation failed",
                cause = e,
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRequest(body: ImageGenerationRequest, model: String): Request {
        val jsonBody = json.encodeToString(body).toRequestBody(jsonMediaType)
        return Request.Builder()
            .url(RakutenApiEndpoints.imageGenerateUrl(model))
            .header("Authorization", apiKey)
//            .header("x-goog-api-key", apiKey)
            .post(jsonBody)
            .build()
    }

    private suspend fun cancelOnCoroutineCompletion(call: okhttp3.Call) {
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
    }
}
