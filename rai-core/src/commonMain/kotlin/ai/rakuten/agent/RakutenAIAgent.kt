package ai.rakuten.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.rakuten.core.RakutenAIClient
import ai.rakuten.executor.RakutenAIPromptExecutor
import ai.rakuten.executor.RakutenAIStreamingPromptExecutor

/**
 * Builds and returns a fully configured [AIAgent] connected to the Rakuten AI Gateway.
 *
 * This is the primary entry point for most use cases. The agent runs a simple
 * single-subgraph strategy:
 * ```
 * start → callLLM → (tool call?) → executeTool → sendResult → callLLM → ...
 *                 → (text reply?) → finish
 * ```
 *
 * ### Minimal example
 * ```kotlin
 * val agent = rakutenAIAgent {
 *     apiKey       = "RAKUTEN_AI_GATEWAY_KEY"
 *     systemPrompt = "You are a helpful assistant."
 * }
 * val result = agent.run("Hello!")
 * ```
 *
 * ### With streaming
 * ```kotlin
 * val agent = rakutenAIAgent {
 *     credentialManager = myRefreshableManager
 *     model             = RakutenAIModels.Claude3_7Sonnet
 *     streaming         = true
 *     onStreamChunk     = { chunk -> runOnUiThread { chatView.append(chunk) } }
 *     toolRegistry      = ToolRegistry { raiTool(WeatherTool) }
 * }
 * ```
 *
 * @param configure Lambda to configure [RakutenAIAgentConfig].
 * @throws IllegalArgumentException if neither [RakutenAIAgentConfig.apiKey] nor
 *   [RakutenAIAgentConfig.credentialManager] is set, or if both are set simultaneously.
 */
suspend fun rakutenAIAgent(
    configure: RakutenAIAgentConfig.() -> Unit,
): AIAgent<String, String> {
    val config = RakutenAIAgentConfig().apply(configure)

    require(config.apiKey != null || config.credentialManager != null) {
        "rakutenAIAgent { } requires either apiKey or credentialManager to be set."
    }
    require(config.apiKey == null || config.credentialManager == null) {
        "Set either apiKey or credentialManager in rakutenAIAgent { }, not both."
    }

    val executor: PromptExecutor = buildExecutor(config)

    val agentStrategy = strategy("rakuten-ai-agent") {
        val callLLM        by nodeLLMRequest()
        val executeTool    by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        edge(nodeStart forwardTo callLLM)
        edge(callLLM forwardTo executeTool    onToolCall       { true })
        edge(callLLM forwardTo nodeFinish     onAssistantMessage { true })
        edge(executeTool forwardTo sendToolResult)
        edge(sendToolResult forwardTo callLLM        onAssistantMessage { true })
        edge(sendToolResult forwardTo executeTool    onToolCall         { true })
    }

    return AIAgent(
        promptExecutor = executor,
        strategy       = agentStrategy,
        agentConfig    = AIAgentConfig(
            prompt             = prompt("rakuten-ai") { system(config.systemPrompt) },
            model              = config.model,
            maxAgentIterations = config.maxIterations,
        ),
        toolRegistry = config.toolRegistry,
    ) {
        if (config.onToolCall != null || config.onError != null) {
            handleEvents {
                config.onToolCall?.let { handler ->
                    onToolCallStarting { ctx -> handler(ctx.toolName, ctx.toolArgs.toString()) }
                }
                config.onError?.let { handler ->
                    onAgentExecutionFailed { ctx -> handler(ctx.throwable) }
                }
            }
        }
    }
}

private suspend fun buildExecutor(config: RakutenAIAgentConfig): PromptExecutor {
    val client: RakutenAIClient = when {
        config.credentialManager != null -> RakutenAIClient.create(config.credentialManager!!)
        else                             -> RakutenAIClient(config.apiKey!!)
    }

    return if (config.streaming && config.onStreamChunk != null) {
        RakutenAIStreamingPromptExecutor(client, config.onStreamChunk!!)
    } else {
        RakutenAIPromptExecutor(client)
    }
}
