package ai.rakuten.tools

import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for [RaiTool] — verifies metadata preservation, arg deserialization, and execute behaviour.
 * These run entirely in-process; no gateway calls are made.
 */
class RaiToolTest {

    // ── Test tools ────────────────────────────────────────────────────────────

    object GreetTool : RaiTool<GreetTool.Args>(
        argsType    = typeToken<Args>(),
        name        = "greet",
        description = "Returns a greeting for the given name.",
        toolVersion = "2.1",
        category    = "demo",
    ) {
        @Serializable data class Args(val name: String)
        override suspend fun execute(args: Args) = "Hello, ${args.name}!"
    }

    object AddTool : RaiTool<AddTool.Args>(
        argsType    = typeToken<Args>(),
        name        = "add",
        description = "Adds two integers.",
        // toolVersion and category intentionally left at defaults
    ) {
        @Serializable data class Args(val a: Int, val b: Int)
        override suspend fun execute(args: Args) = "${args.a + args.b}"
    }

    // ── #1 tool metadata is preserved ────────────────────────────────────────

    @Test
    fun `tool name is preserved`() {
        assertEquals("greet", GreetTool.name)
    }

    @Test
    fun `custom toolVersion is preserved`() {
        assertEquals("2.1", GreetTool.toolVersion)
    }

    @Test
    fun `custom category is preserved`() {
        assertEquals("demo", GreetTool.category)
    }

    @Test
    fun `default toolVersion is 1 dot 0`() {
        assertEquals("1.0", AddTool.toolVersion)
    }

    @Test
    fun `default category is general`() {
        assertEquals("general", AddTool.category)
    }

    // ── #2 execute receives correctly deserialized args ───────────────────────

    @Test
    fun `execute returns greeting with provided name`() = runTest {
        val result = GreetTool.execute(GreetTool.Args(name = "World"))
        assertEquals("Hello, World!", result)
    }

    @Test
    fun `execute adds two numbers correctly`() = runTest {
        val result = AddTool.execute(AddTool.Args(a = 3, b = 4))
        assertEquals("7", result)
    }

    // ── #3 execute returns a String ───────────────────────────────────────────

    @Test
    fun `execute return type is String`() = runTest {
        val result: String = GreetTool.execute(GreetTool.Args("Kotlin"))
        assertNotNull(result)
    }

    @Test
    fun `execute never returns null`() = runTest {
        val result = AddTool.execute(AddTool.Args(0, 0))
        assertNotNull(result)
    }

    // ── Tool descriptor (fed to LLM) ──────────────────────────────────────────

    @Test
    fun `tool descriptor name matches tool name`() {
        assertEquals(GreetTool.name, GreetTool.descriptor.name)
    }
}
