package ai.rakuten.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.rakuten.core.RakutenAIClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [RakutenAIStreamingPromptExecutor].
 *
 * Uses MockK to stub [RakutenAIClient.executeStreaming] with controlled [StreamFrame] flows,
 * allowing verification of the assembly logic without live network calls.
 */
class RakutenAIStreamingExecutorTest {

    private val mockClient = mockk<RakutenAIClient>(relaxed = true)
    private val mockPrompt = mockk<Prompt>(relaxed = true)
    private val mockModel  = mockk<LLModel>(relaxed = true)
    private val emptyTools = emptyList<ToolDescriptor>()

    // ── #14 onChunk fired for each TextDelta ──────────────────────────────────

    @Test
    fun `onChunk is called for each TextDelta frame`() = runTest {
        val frames = flowOf(
            StreamFrame.TextDelta(text = "Hello", index = 0),
            StreamFrame.TextDelta(text = ", ",    index = 0),
            StreamFrame.TextDelta(text = "world", index = 0),
        )
        every { mockClient.executeStreaming(any(), any(), any()) } returns frames

        val chunks = mutableListOf<String>()
        val executor = RakutenAIStreamingPromptExecutor(mockClient) { chunks.add(it) }

        executor.execute(mockPrompt, mockModel, emptyTools)

        assertEquals(listOf("Hello", ", ", "world"), chunks)
    }

    // ── #15 TextDeltas accumulate into one Message.Assistant ──────────────────

    @Test
    fun `text deltas are assembled into a single assistant message`() = runTest {
        val frames = flowOf(
            StreamFrame.TextDelta(text = "foo", index = 0),
            StreamFrame.TextDelta(text = "bar", index = 0),
            StreamFrame.TextDelta(text = "baz", index = 0),
        )
        every { mockClient.executeStreaming(any(), any(), any()) } returns frames

        val executor = RakutenAIStreamingPromptExecutor(mockClient) { }
        val responses = executor.execute(mockPrompt, mockModel, emptyTools)

        assertEquals(1, responses.size)
        val msg = responses.first()
        assertIs<Message.Assistant>(msg)
        assertEquals("foobarbaz", msg.content)
    }

    // ── #16 ToolCallComplete produces Message.Tool.Call — onChunk NOT called ──

    @Test
    fun `tool call frame returns Message Tool Call`() = runTest {
        val frames = flowOf(
            StreamFrame.ToolCallComplete(id = "call-1", name = "echo", content = """{"message":"hi"}"""),
        )
        every { mockClient.executeStreaming(any(), any(), any()) } returns frames

        val chunks = mutableListOf<String>()
        val executor = RakutenAIStreamingPromptExecutor(mockClient) { chunks.add(it) }
        val responses = executor.execute(mockPrompt, mockModel, emptyTools)

        assertEquals(1, responses.size)
        assertIs<Message.Tool.Call>(responses.first())
        assertTrue(chunks.isEmpty(), "onChunk must NOT fire for tool call frames")
    }

    @Test
    fun `tool call frame carries correct id name and content`() = runTest {
        val frames = flowOf(
            StreamFrame.ToolCallComplete(id = "id-42", name = "my_tool", content = """{"x":1}"""),
        )
        every { mockClient.executeStreaming(any(), any(), any()) } returns frames

        val executor = RakutenAIStreamingPromptExecutor(mockClient) { }
        val response = executor.execute(mockPrompt, mockModel, emptyTools).first()

        assertIs<Message.Tool.Call>(response)
        assertEquals("id-42",     response.id)
        assertEquals("my_tool",   response.tool)
        assertEquals("""{"x":1}""", response.content)
    }

    // ── #17 tool call takes priority over preceding text deltas ───────────────

    @Test
    fun `tool call takes priority over preceding text deltas`() = runTest {
        val frames = flowOf(
            StreamFrame.TextDelta(text = "partial text before tool", index = 0),
            StreamFrame.ToolCallComplete(id = "c1", name = "tool", content = "{}"),
        )
        every { mockClient.executeStreaming(any(), any(), any()) } returns frames

        val executor = RakutenAIStreamingPromptExecutor(mockClient) { }
        val responses = executor.execute(mockPrompt, mockModel, emptyTools)

        // When a tool call is present the result list contains tool calls only
        assertTrue(responses.all { it is Message.Tool.Call },
            "Expected only tool call responses when tool call frames are present")
    }

    // ── #18 empty stream → single assistant with empty content ───────────────
    //
    // When no frames arrive the executor falls through the else branch and
    // returns a single Message.Assistant with empty content (never an empty list).

    @Test
    fun `empty stream returns a single assistant message with empty content`() = runTest {
        every { mockClient.executeStreaming(any(), any(), any()) } returns flowOf()

        val executor = RakutenAIStreamingPromptExecutor(mockClient) { }
        val responses = executor.execute(mockPrompt, mockModel, emptyTools)

        assertEquals(1, responses.size)
        assertIs<Message.Assistant>(responses.first())
        assertEquals("", (responses.first() as Message.Assistant).content)
    }

    // ── executeStreaming delegates to client ──────────────────────────────────

    @Test
    fun `executeStreaming delegates to the underlying client`() {
        val fakeFlow = flowOf(StreamFrame.TextDelta(text = "x", index = 0))
        every { mockClient.executeStreaming(any(), any(), any()) } returns fakeFlow

        val executor = RakutenAIStreamingPromptExecutor(mockClient) { }
        val result = executor.executeStreaming(mockPrompt, mockModel, emptyTools)

        // The flow returned should be the tapped version of the client's flow
        verify { mockClient.executeStreaming(mockPrompt, mockModel, emptyTools) }
    }

    // ── close() delegates to client ──────────────────────────────────────────

    @Test
    fun `close delegates to the underlying client`() {
        val executor = RakutenAIStreamingPromptExecutor(mockClient) { }
        executor.close()
        verify { mockClient.close() }
    }
}
