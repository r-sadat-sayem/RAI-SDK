package ai.rakuten.tools

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [RaiToolRegistry] extension functions — verifies that tools are registered
 * correctly through the [raiTool] / [raiTools] DSL.
 */
class RaiToolRegistryTest {

    // ── Test tools ────────────────────────────────────────────────────────────

    object PingTool : RaiTool<PingTool.Args>(
        argsType    = typeToken<Args>(),
        name        = "ping",
        description = "Responds with pong.",
        category    = "network",
    ) {
        @Serializable class Args
        override suspend fun execute(args: Args) = "pong"
    }

    object TimeTool : RaiTool<TimeTool.Args>(
        argsType    = typeToken<Args>(),
        name        = "get_time",
        description = "Returns the current time.",
    ) {
        @Serializable class Args
        override suspend fun execute(args: Args) = "12:00"
    }

    object EchoTool : RaiTool<EchoTool.Args>(
        argsType    = typeToken<Args>(),
        name        = "echo",
        description = "Echoes a message.",
    ) {
        @Serializable data class Args(val message: String)
        override suspend fun execute(args: Args) = args.message
    }

    // ── #5 single tool registered via raiTool ────────────────────────────────

    @Test
    fun `raiTool registers tool and it is discoverable by name`() {
        val registry = ToolRegistry { raiTool(PingTool) }
        val descriptor = registry.tools.find { it.name == "ping" }
        assertNotNull(descriptor, "Expected 'ping' tool in registry")
    }

    // ── #6 multiple tools via raiTools are all registered ─────────────────────

    @Test
    fun `raiTools registers all provided tools`() {
        val registry = ToolRegistry { raiTools(PingTool, TimeTool, EchoTool) }
        val names = registry.tools.map { it.name }
        assertTrue("ping"     in names, "Expected 'ping' in registry")
        assertTrue("get_time" in names, "Expected 'get_time' in registry")
        assertTrue("echo"     in names, "Expected 'echo' in registry")
    }

    @Test
    fun `raiTools registers correct count`() {
        val registry = ToolRegistry { raiTools(PingTool, TimeTool, EchoTool) }
        assertEquals(3, registry.tools.size)
    }

    // ── #8 empty registry ────────────────────────────────────────────────────

    @Test
    fun `empty registry contains no tools`() {
        val registry = ToolRegistryBuilder().build()
        assertTrue(registry.tools.isEmpty(), "Expected empty registry to have no tools")
    }

    // ── raiTool is chainable ──────────────────────────────────────────────────

    @Test
    fun `raiTool calls are chainable`() {
        val registry = ToolRegistry {
            raiTool(PingTool)
            raiTool(TimeTool)
        }
        assertEquals(2, registry.tools.size)
    }

    // ── Metadata accessible on the tool itself after registration ─────────────

    @Test
    fun `registered tool retains its category metadata`() {
        assertEquals("network", PingTool.category)
    }

    @Test
    fun `registered tool retains its toolVersion metadata`() {
        assertEquals("1.0", PingTool.toolVersion)
    }
}
