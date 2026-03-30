package ai.rakuten.android.api.text

import ai.rakuten.android.ApiMessage
import ai.rakuten.android.api.RakutenApiEndpoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP service for Anthropic-compatible text completions via the Rakuten AI Gateway.
 *
 * This is the canonical low-level service for chat/text calls. It separates the text
 * API concern from image generation ([ai.rakuten.android.api.image.RakutenImageApiService]).
 *
 * For most use-cases prefer the agent-based API via `rakutenAIAgent { }` in `rai-core`.
 * Use this service directly only when you need raw multi-turn chat without the agent graph.
 *
 * ### Usage
 * ```kotlin
 * val service = RakutenTextApiService(apiKey = gatewayKey, httpClient = get())
 *
 * // Batch response:
 * val reply = service.complete(listOf(ApiMessage("user", "Hello!")))
 *
 * // Streaming:
 * service.stream(messages = history, onChunk = { chunk -> appendToUI(chunk) })
 * ```
 *
 * @param apiKey     Your `RAKUTEN_AI_GATEWAY_KEY`.
 * @param httpClient OkHttp client — inject the Koin-provided instance so logging works.
 */
public class RakutenTextApiService(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val endpoint = "${RakutenApiEndpoints.TEXT_BASE_URL}${RakutenApiEndpoints.TEXT_MESSAGES_PATH}"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Sends [messages] to the gateway and returns the complete assistant reply.
     *
     * @param messages     Conversation history, ordered oldest-first.
     * @param systemPrompt Optional system instruction placed before the first turn.
     * @param model        Wire model name. Defaults to [RakutenApiEndpoints.DEFAULT_TEXT_MODEL].
     * @param maxTokens    Maximum tokens in the model response.
     */
    public suspend fun complete(
        messages: List<ApiMessage>,
        systemPrompt: String = "",
        model: String = RakutenApiEndpoints.DEFAULT_TEXT_MODEL,
        maxTokens: Int = 1024,
    ): String = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(buildRequest(messages, systemPrompt, model, maxTokens, stream = false))
        cancelOnCoroutineCompletion(call)
        call.execute().use { response ->
            val body = response.body?.string() ?: error("Empty response (HTTP ${response.code})")
            if (!response.isSuccessful) error("HTTP ${response.code}: $body")
            parseFullResponse(body)
        }
    }

    /**
     * Streams the assistant reply token-by-token via [onChunk] and returns the full text.
     *
     * Uses the Anthropic SSE streaming protocol (`"stream": true`). Each text delta is
     * forwarded to [onChunk] as it arrives.
     *
     * @param messages     Conversation history, ordered oldest-first.
     * @param systemPrompt Optional system instruction.
     * @param model        Wire model name. Defaults to [RakutenApiEndpoints.DEFAULT_TEXT_MODEL].
     * @param maxTokens    Maximum tokens in the model response.
     * @param onChunk      Suspend callback invoked per text token.
     * @return             Full accumulated assistant reply text.
     */
    public suspend fun stream(
        messages: List<ApiMessage>,
        systemPrompt: String = "",
        model: String = RakutenApiEndpoints.DEFAULT_TEXT_MODEL,
        maxTokens: Int = 1024,
        onChunk: suspend (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(buildRequest(messages, systemPrompt, model, maxTokens, stream = true))
        cancelOnCoroutineCompletion(call)

        val accumulated = StringBuilder()
        call.execute().use { response ->
            val body = response.body ?: error("Empty body (HTTP ${response.code})")
            if (!response.isSuccessful) error("HTTP ${response.code}: ${body.string()}")

            val source = body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                try {
                    val root = Json.parseToJsonElement(data).jsonObject
                    if (root["type"]?.jsonPrimitive?.content == "content_block_delta") {
                        val text = root["delta"]
                            ?.jsonObject?.get("text")
                            ?.jsonPrimitive?.content
                            ?: continue
                        accumulated.append(text)
                        onChunk(text)
                    }
                } catch (_: Exception) { /* skip malformed SSE frames */ }
            }
        }
        accumulated.toString()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRequest(
        messages: List<ApiMessage>,
        systemPrompt: String,
        model: String,
        maxTokens: Int,
        stream: Boolean,
    ): Request {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            if (stream) put("stream", true)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                }
            }
        }.toString().toRequestBody(jsonMediaType)

        return Request.Builder()
            .url(endpoint)
            .header("Authorization",     apiKey)
            .header("anthropic-version", RakutenApiEndpoints.TEXT_API_VERSION)
            .post(body)
            .build()
    }

    private fun parseFullResponse(json: String): String =
        Json.parseToJsonElement(json)
            .jsonObject["content"]
            ?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
            ?: error("No text content in response: $json")

    private suspend fun cancelOnCoroutineCompletion(call: okhttp3.Call) {
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
    }
}
