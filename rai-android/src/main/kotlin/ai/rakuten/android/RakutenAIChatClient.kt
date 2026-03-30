package ai.rakuten.android

import ai.rakuten.core.RakutenAISettings
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
 * A conversation message sent to / received from the Rakuten AI Gateway.
 *
 * @param role    `"user"` or `"assistant"`.
 * @param content Plain-text content of the message.
 */
public data class ApiMessage(
    val role: String,
    val content: String,
)

/**
 * Minimal HTTP client that calls the Rakuten AI Gateway `/v1/messages` endpoint,
 * matching exactly the following cURL:
 *
 * ```
 * curl --location 'https://api.ai.public.rakuten-it.com/anthropic/v1/messages' \
 *   --header 'Authorization: {your_subscription_key}' \
 *   --header 'anthropic-version: 2023-06-01' \
 *   --header 'content-type: application/json' \
 *   --data '{
 *       "model": "claude-sonnet-4-6",
 *       "max_tokens": 1024,
 *       "messages": [{"role": "user", "content": "Hi Claude."}]
 *   }'
 * ```
 *
 * HTTP traffic is logged by the [OkHttpClient]'s [okhttp3.logging.HttpLoggingInterceptor]
 * (configured via [ai.rakuten.android.di.raiHttpModule]).
 *
 * ### Usage
 * ```kotlin
 * // Inject OkHttpClient from Koin (includes logging interceptor):
 * val client = RakutenAIChatClient(apiKey = gatewayKey, httpClient = get())
 *
 * // Full response:
 * val reply = client.chat(listOf(ApiMessage("user", "Hello!")))
 *
 * // Streaming:
 * client.chatStream(messages = history, onChunk = { chunk -> print(chunk) })
 * ```
 *
 * @param apiKey     Your `RAKUTEN_AI_GATEWAY_KEY`, placed in the `Authorization` header.
 * @param httpClient OkHttp client — inject the Koin-provided instance so logging works.
 */
public class RakutenAIChatClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val endpoint     = "${RakutenAISettings.BASE_URL}${RakutenAISettings.MESSAGES_PATH}"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends [messages] to the gateway and returns the complete assistant reply.
     *
     * @param messages     Conversation history, ordered oldest-first.
     * @param systemPrompt Optional system instruction placed before the first turn.
     * @param model        Wire model name. Defaults to [RakutenAISettings.DEFAULT_WIRE_MODEL].
     * @param maxTokens    Maximum tokens in the model response.
     */
    public suspend fun chat(
        messages: List<ApiMessage>,
        systemPrompt: String = "",
        model: String = RakutenAISettings.DEFAULT_WIRE_MODEL,
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
     * forwarded to [onChunk] as it arrives. The OkHttp call is cancelled automatically
     * when the calling coroutine is cancelled.
     *
     * @param messages     Conversation history, ordered oldest-first.
     * @param systemPrompt Optional system instruction.
     * @param model        Wire model name. Defaults to [RakutenAISettings.DEFAULT_WIRE_MODEL].
     * @param maxTokens    Maximum tokens in the model response.
     * @param onChunk      Suspend callback invoked for each text token.
     * @return             Full accumulated assistant reply text.
     */
    public suspend fun chatStream(
        messages: List<ApiMessage>,
        systemPrompt: String = "",
        model: String = RakutenAISettings.DEFAULT_WIRE_MODEL,
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
            .header("anthropic-version", RakutenAISettings.API_VERSION)
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

    /** Cancels the OkHttp [call] when the surrounding coroutine is cancelled or completes. */
    private suspend fun cancelOnCoroutineCompletion(call: okhttp3.Call) {
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
    }
}
