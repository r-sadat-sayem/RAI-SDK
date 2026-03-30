package ai.rakuten.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.serialization.typeToken
import ai.rakuten.core.RakutenAIModels
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the Koog agent graph routing logic using [FakePromptExecutor].
 *
 * No live network calls are made. The fake executor returns canned [Message.Response]
 * objects that drive the agent down specific graph edges.
 */
class RakutenAIAgentGraphTest {

    // ── Test tool (uses Koog SimpleTool — no rai-tools-core dependency) ────────

    object EchoTool : SimpleTool<EchoTool.Args>(
        argsType    = typeToken<Args>(),
        name        = "echo",
        description = "Echoes a message back.",
    ) {
        @Serializable data class Args(val message: String)
        override suspend fun execute(args: Args) = "Echo: ${args.message}"
    }

    // ── Shared agent builder ──────────────────────────────────────────────────

    private fun buildAgent(
        executor: FakePromptExecutor,
        systemPrompt: String = "",
        maxIterations: Int = 10,
        toolRegistry: ToolRegistry = ToolRegistryBuilder().build(),
    ): AIAgent<String, String> {
        val agentStrategy = strategy("test-agent") {
            val callLLM        by nodeLLMRequest()
            val executeTool    by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()

            edge(nodeStart      forwardTo callLLM)
            edge(callLLM        forwardTo executeTool    onToolCall        { true })
            edge(callLLM        forwardTo nodeFinish     onAssistantMessage { true })
            edge(executeTool    forwardTo sendToolResult)
            edge(sendToolResult forwardTo callLLM        onAssistantMessage { true })
            edge(sendToolResult forwardTo executeTool    onToolCall         { true })
        }

        return AIAgent(
            promptExecutor = executor,
            strategy       = agentStrategy,
            agentConfig    = AIAgentConfig(
                prompt             = prompt("test") { system(systemPrompt) },
                model              = RakutenAIModels.Default,
                maxAgentIterations = maxIterations,
            ),
            toolRegistry = toolRegistry,
        )
    }

    // ── #22 text reply → agent finishes and returns text ─────────────────────

    @Test
    fun `assistant text response terminates agent and returns text`() = runTest {
        val executor = FakePromptExecutor(
            listOf(assistantMessage("Hello, world!")),
        )
        val result = buildAgent(executor).run("Say hello")
        assertEquals("Hello, world!", result)
    }

    @Test
    fun `executor is called exactly once for a single-turn reply`() = runTest {
        val executor = FakePromptExecutor(listOf(assistantMessage("Done")))
        buildAgent(executor).run("input")
        assertEquals(1, executor.receivedPrompts.size)
    }

    // ── #23 tool call → execute → result to LLM → final text ─────────────────
    //
    // nodeLLMSendToolResult calls execute() itself (turn 2), then routes to
    // callLLM which calls execute() a final time (turn 3). Total = 3 for 1 tool.

    @Test
    fun `tool call triggers tool execution and routes result back to LLM`() = runTest {
        val executor = FakePromptExecutor(
            listOf(toolCallMessage("c1", "echo", """{"message":"hi"}""")), // turn 1
            listOf(assistantMessage("intermediate")),                       // turn 2 (sendToolResult)
            listOf(assistantMessage("Echo: hi")),                          // turn 3 (final callLLM)
        )
        val registry = ToolRegistryBuilder().apply { tool(EchoTool) }.build()
        val result = buildAgent(executor, toolRegistry = registry).run("echo hi")
        assertEquals("Echo: hi", result)
    }

    @Test
    fun `executor is called three times for a single tool call cycle`() = runTest {
        val executor = FakePromptExecutor(
            listOf(toolCallMessage("c1", "echo", """{"message":"test"}""")),
            listOf(assistantMessage("intermediate")),
            listOf(assistantMessage("result")),
        )
        val registry = ToolRegistryBuilder().apply { tool(EchoTool) }.build()
        buildAgent(executor, toolRegistry = registry).run("input")
        // 1 initial callLLM + 1 sendToolResult LLM call + 1 final callLLM = 3
        assertEquals(3, executor.receivedPrompts.size)
    }

    // ── #24 chained tool calls ────────────────────────────────────────────────
    //
    // 2 tool calls: each sendToolResult calls execute() once, then a final callLLM.
    // Total = 4 execute() calls.

    @Test
    fun `agent handles two sequential tool calls before final answer`() = runTest {
        val executor = FakePromptExecutor(
            listOf(toolCallMessage("c1", "echo", """{"message":"first"}""")), // turn 1
            listOf(toolCallMessage("c2", "echo", """{"message":"second"}""")),// turn 2 (sendToolResult → toolCall)
            listOf(assistantMessage("intermediate")),                          // turn 3 (sendToolResult → assistant)
            listOf(assistantMessage("Done after two tools")),                  // turn 4 (final callLLM)
        )
        val registry = ToolRegistryBuilder().apply { tool(EchoTool) }.build()
        val result = buildAgent(executor, toolRegistry = registry).run("chain")
        assertEquals("Done after two tools", result)
        assertEquals(4, executor.receivedPrompts.size)
    }

    // ── #27 system prompt reaches executor ────────────────────────────────────

    @Test
    fun `system prompt is present in the first prompt sent to the executor`() = runTest {
        val executor = FakePromptExecutor(listOf(assistantMessage("ok")))
        buildAgent(executor, systemPrompt = "You are a test assistant.").run("hello")

        val systemMessages = executor.receivedPrompts.first().messages.filter {
            it is ai.koog.prompt.message.Message.System
        }
        assertTrue(systemMessages.isNotEmpty(), "Expected a System message in the first prompt")
    }

    // ── #28 tool descriptors reach executor ───────────────────────────────────

    @Test
    fun `registered tools appear as descriptors in the executor call`() = runTest {
        val executor = FakePromptExecutor(listOf(assistantMessage("ok")))
        val registry = ToolRegistryBuilder().apply { tool(EchoTool) }.build()
        buildAgent(executor, toolRegistry = registry).run("input")

        val toolNames = executor.receivedTools.first().map { it.name }
        assertTrue("echo" in toolNames,
            "Expected 'echo' descriptor; got: $toolNames")
    }

    @Test
    fun `empty tool registry sends no tool descriptors to executor`() = runTest {
        val executor = FakePromptExecutor(listOf(assistantMessage("ok")))
        buildAgent(executor).run("input")
        assertTrue(executor.receivedTools.first().isEmpty())
    }
}
