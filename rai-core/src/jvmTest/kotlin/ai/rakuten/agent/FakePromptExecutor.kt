package ai.rakuten.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A test double for [PromptExecutor] that returns pre-programmed [Message.Response] lists.
 *
 * Each `execute()` call pops the next queued response, letting tests control the agent graph
 * routing without any live network calls.
 */
class FakePromptExecutor(
    vararg responses: List<Message.Response>,
) : PromptExecutor() {

    private val responseQueue: ArrayDeque<List<Message.Response>> =
        ArrayDeque(responses.toList())

    /** All prompts received in order. */
    val receivedPrompts = mutableListOf<Prompt>()

    /** All tool-descriptor lists received in order. */
    val receivedTools = mutableListOf<List<ToolDescriptor>>()

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        receivedPrompts.add(prompt)
        receivedTools.add(tools)
        return responseQueue.removeFirst()
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = emptyFlow()

    // Moderation is not exercised by any of the agent graph tests; blow up loudly if hit.
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        error("FakePromptExecutor: moderate() is not expected in these tests")

    override fun close() = Unit
}

// ── Convenience helpers ───────────────────────────────────────────────────────

fun assistantMessage(text: String): Message.Response =
    Message.Assistant(content = text, metaInfo = ResponseMetaInfo.Empty)

fun toolCallMessage(id: String, toolName: String, argsJson: String): Message.Response =
    Message.Tool.Call(
        id       = id,
        tool     = toolName,
        content  = argsJson,
        metaInfo = ResponseMetaInfo.Empty,
    )
