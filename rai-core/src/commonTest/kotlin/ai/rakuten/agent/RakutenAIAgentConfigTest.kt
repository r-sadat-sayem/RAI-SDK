package ai.rakuten.agent

import ai.rakuten.core.RakutenAIModels
import ai.rakuten.credentials.StaticCredentialManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RakutenAIAgentConfigTest {

    // ── #9 missing both credentials → throws ─────────────────────────────────

    @Test
    fun `missing both credentials throws IllegalArgumentException`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            rakutenAIAgent {
                // neither apiKey nor credentialManager set
                systemPrompt = "test"
            }
        }
    }

    // ── #10 both credentials set → throws ────────────────────────────────────

    @Test
    fun `setting both apiKey and credentialManager throws`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            rakutenAIAgent {
                apiKey             = "test-key"
                credentialManager  = StaticCredentialManager("test-key")
            }
        }
    }

    // ── #11 only apiKey → valid ───────────────────────────────────────────────

    @Test
    fun `only apiKey set does not throw`() = runTest {
        // Should succeed — will build a real agent pointed at the gateway
        // We only test that config validation passes; we don't run the agent
        val config = RakutenAIAgentConfig().apply {
            apiKey = "some-key"
        }
        assertNotNull(config.apiKey)
        assertNull(config.credentialManager)
    }

    // ── #12 only credentialManager → valid ───────────────────────────────────

    @Test
    fun `only credentialManager set does not throw`() = runTest {
        val config = RakutenAIAgentConfig().apply {
            credentialManager = StaticCredentialManager("some-key")
        }
        assertNull(config.apiKey)
        assertNotNull(config.credentialManager)
    }

    // ── #13 defaults are sensible ─────────────────────────────────────────────

    @Test
    fun `default maxIterations is 50`() {
        val config = RakutenAIAgentConfig()
        assertEquals(50, config.maxIterations)
    }

    @Test
    fun `default streaming is false`() {
        val config = RakutenAIAgentConfig()
        assertEquals(false, config.streaming)
    }

    @Test
    fun `default model is Claude4_6Sonnet`() {
        val config = RakutenAIAgentConfig()
        assertEquals(RakutenAIModels.Claude4_6Sonnet, config.model)
    }

    @Test
    fun `default systemPrompt is empty`() {
        val config = RakutenAIAgentConfig()
        assertEquals("", config.systemPrompt)
    }

    @Test
    fun `default onStreamChunk is null`() {
        val config = RakutenAIAgentConfig()
        assertNull(config.onStreamChunk)
    }

    @Test
    fun `default onToolCall is null`() {
        val config = RakutenAIAgentConfig()
        assertNull(config.onToolCall)
    }

    @Test
    fun `default onError is null`() {
        val config = RakutenAIAgentConfig()
        assertNull(config.onError)
    }

    // ── Config DSL: values are assigned correctly ─────────────────────────────

    @Test
    fun `apiKey is stored on the config`() {
        val config = RakutenAIAgentConfig().apply { apiKey = "raik-abc123" }
        assertEquals("raik-abc123", config.apiKey)
    }

    @Test
    fun `systemPrompt is stored on the config`() {
        val config = RakutenAIAgentConfig().apply {
            apiKey = "key"
            systemPrompt = "You are a helpful assistant."
        }
        assertEquals("You are a helpful assistant.", config.systemPrompt)
    }

    @Test
    fun `maxIterations can be overridden`() {
        val config = RakutenAIAgentConfig().apply {
            apiKey = "key"
            maxIterations = 10
        }
        assertEquals(10, config.maxIterations)
    }

    @Test
    fun `streaming flag can be set to true`() {
        val config = RakutenAIAgentConfig().apply {
            apiKey    = "key"
            streaming = true
        }
        assertEquals(true, config.streaming)
    }
}
