package ai.rakuten.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.prompt.llm.LLModel
import ai.rakuten.core.RakutenAIModels
import ai.rakuten.credentials.RakutenAICredentialManager

/**
 * Configuration holder for the [rakutenAIAgent] builder DSL.
 *
 * Set properties inside the `rakutenAIAgent { }` block:
 * ```kotlin
 * val agent = rakutenAIAgent {
 *     apiKey       = "RAKUTEN_AI_GATEWAY_KEY"   // or use credentialManager
 *     systemPrompt = "You are a helpful assistant."
 *     streaming    = true
 *     onStreamChunk = { chunk -> textView.append(chunk) }
 * }
 * ```
 *
 * **Credential rule:** Set exactly one of [apiKey] or [credentialManager].
 * Setting both or neither throws [IllegalArgumentException] at build time.
 */
class RakutenAIAgentConfig {

    /**
     * Static gateway API key.
     *
     * Wraps the value in a [ai.rakuten.credentials.StaticCredentialManager].
     * For production, prefer [credentialManager] with a [ai.rakuten.credentials.RefreshableCredentialManager].
     */
    var apiKey: String? = null

    /**
     * Credential manager for production use — supports automatic token refresh.
     *
     * Mutually exclusive with [apiKey].
     */
    var credentialManager: RakutenAICredentialManager? = null

    /** LLM model to use for all calls in this agent. Defaults to [RakutenAIModels.Default]. */
    var model: LLModel = RakutenAIModels.Default

    /** System prompt placed at the start of every conversation turn. Empty by default. */
    var systemPrompt: String = ""

    /**
     * Maximum number of LLM + tool-call cycles before the agent stops.
     *
     * Increase for tasks that require many sequential tool calls. Defaults to 50.
     */
    var maxIterations: Int = 50

    /**
     * Tools available to the agent. Defaults to an empty registry.
     *
     * Populate via the [ToolRegistry] builder DSL:
     * ```kotlin
     * toolRegistry = ToolRegistry { raiTool(WeatherTool) }
     * ```
     */
    var toolRegistry: ToolRegistry = ToolRegistryBuilder().build()

    /**
     * Enables token-by-token streaming.
     *
     * When `true`, [onStreamChunk] is called for each text delta as it arrives
     * from the model. The agent still returns the complete final result from `run()`.
     * Defaults to `false`.
     */
    var streaming: Boolean = false

    /**
     * Suspend callback invoked per text token when [streaming] is `true`.
     *
     * Called from `Dispatchers.Default`. Use `withContext(Dispatchers.Main)` if you
     * need to update a View or Compose state from the main thread.
     *
     * ```kotlin
     * onStreamChunk = { chunk ->
     *     withContext(Dispatchers.Main) { textView.append(chunk) }
     * }
     * ```
     */
    var onStreamChunk: (suspend (String) -> Unit)? = null

    /**
     * Called when a tool invocation begins.
     *
     * @param toolName The name of the tool being called.
     * @param toolArgs Raw JSON string of the arguments passed to the tool.
     */
    var onToolCall: (suspend (toolName: String, toolArgs: String) -> Unit)? = null

    /**
     * Called when the agent encounters an unrecoverable error during a run.
     *
     * @param cause The exception that terminated the agent execution.
     */
    var onError: (suspend (cause: Throwable) -> Unit)? = null
}
