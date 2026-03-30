package ai.rakuten.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.rakuten.core.RakutenAIClient
import ai.rakuten.credentials.RakutenAICredentialManager
import io.ktor.http.invoke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

// ─── Factory functions ────────────────────────────────────────────────────────

/**
 * Creates a non-streaming [RakutenAIPromptExecutor] backed by a static API key.
 *
 * For production with token refresh, use [rakutenAIExecutor] with a
 * [ai.rakuten.credentials.RefreshableCredentialManager].
 *
 * @param apiKey The `RAKUTEN_AI_GATEWAY_KEY` value.
 */
fun rakutenAIExecutor(apiKey: String): RakutenAIPromptExecutor =
    RakutenAIPromptExecutor(RakutenAIClient(apiKey))

/**
 * Creates a non-streaming [RakutenAIPromptExecutor] by fetching the initial token
 * from [credentialManager].
 *
 * @param credentialManager Provides and manages the gateway API key.
 */
suspend fun rakutenAIExecutor(
    credentialManager: RakutenAICredentialManager,
): RakutenAIPromptExecutor =
    RakutenAIPromptExecutor(RakutenAIClient.create(credentialManager))

/**
 * Creates a [RakutenAIStreamingPromptExecutor] that calls [onChunk] with each text
 * token as it arrives from the model.
 *
 * When the model responds with a tool call instead of text, streaming is skipped for
 * that turn — the complete tool-call JSON must arrive before execution can proceed.
 *
 * @param credentialManager Provides and manages the gateway API key.
 * @param onChunk Suspend callback invoked for each [StreamFrame.TextDelta].
 */
suspend fun rakutenAIStreamingExecutor(
    credentialManager: RakutenAICredentialManager,
    onChunk: suspend (String) -> Unit,
): RakutenAIStreamingPromptExecutor =
    RakutenAIStreamingPromptExecutor(
        client  = RakutenAIClient.create(credentialManager),
        onChunk = onChunk,
    )

// ─── Executor implementations ─────────────────────────────────────────────────

/**
 * A [PromptExecutor] backed by a single [RakutenAIClient], with no streaming tap.
 *
 * Used when `streaming = false` (the default) in the agent builder.
 */
class RakutenAIPromptExecutor internal constructor(
    private val client: RakutenAIClient,
) : PromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> = client.execute(prompt, model, tools)

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = client.executeStreaming(prompt, model, tools)

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        client.moderate(prompt, model)

    override fun close(): Unit = client.close()
}

/**
 * A [PromptExecutor] that taps the streaming response to deliver incremental text
 * tokens via [onChunk] while still returning the full assembled response to the
 * agent graph.
 *
 * - **Text responses**: streams token-by-token; [onChunk] is called per [StreamFrame.TextDelta].
 * - **Tool-call responses**: streaming is bypassed for that turn; the full JSON is
 *   collected before returning, ensuring `execute()` always returns a valid tool call.
 */
class RakutenAIStreamingPromptExecutor internal constructor(
    private val client: RakutenAIClient,
    private val onChunk: suspend (String) -> Unit,
) : PromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val textBuilders = mutableMapOf<Int, StringBuilder>()
        val toolCalls    = mutableListOf<StreamFrame.ToolCallComplete>()

        client.executeStreaming(prompt, model, tools).collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> {
                    textBuilders
                        .getOrPut(frame.index ?: 0) { StringBuilder() }
                        .append(frame.text)
                    onChunk(frame.text)
                }
                is StreamFrame.ToolCallComplete -> toolCalls.add(frame)
                else -> Unit
            }
        }

        return when {
            toolCalls.isNotEmpty() -> toolCalls.map { tc ->
                Message.Tool.Call(
                    id      = tc.id,
                    tool    = tc.name,
                    content = tc.content,
                    metaInfo = ResponseMetaInfo.Empty,
                )
            }
            else -> {
                val text = textBuilders.entries
                    .sortedBy { it.key }
                    .joinToString("") { it.value.toString() }
                listOf(Message.Assistant(content = text, metaInfo = ResponseMetaInfo.Empty))
            }
        }
    }

    /** Passes the raw stream through and taps [StreamFrame.TextDelta] frames via [onChunk]. */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> =
        client.executeStreaming(prompt, model, tools)
            .onEach { frame -> if (frame is StreamFrame.TextDelta) onChunk(frame.text) }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        client.moderate(prompt, model)

    override fun close(): Unit = client.close()
}
