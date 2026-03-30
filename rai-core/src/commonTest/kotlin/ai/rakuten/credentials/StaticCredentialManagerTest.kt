package ai.rakuten.credentials

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class StaticCredentialManagerTest {

    // ── #29 getValidToken returns the configured key ──────────────────────────

    @Test
    fun `getValidToken returns the configured key`() = runTest {
        val manager = StaticCredentialManager("test-key-abc")
        assertEquals("test-key-abc", manager.getValidToken())
    }

    @Test
    fun `getValidToken returns same key on repeated calls`() = runTest {
        val manager = StaticCredentialManager("stable-key")
        repeat(5) {
            assertEquals("stable-key", manager.getValidToken())
        }
    }

    // ── #30 state is always Valid ─────────────────────────────────────────────

    @Test
    fun `initial state is Valid`() {
        val manager = StaticCredentialManager("any-key")
        assertIs<CredentialState.Valid>(manager.state.value)
    }

    @Test
    fun `state remains Valid after getValidToken`() = runTest {
        val manager = StaticCredentialManager("any-key")
        manager.getValidToken()
        assertIs<CredentialState.Valid>(manager.state.value)
    }

    // ── #31 invalidate is a no-op ─────────────────────────────────────────────

    @Test
    fun `invalidate does not change state`() = runTest {
        val manager = StaticCredentialManager("any-key")
        manager.invalidate()
        assertIs<CredentialState.Valid>(manager.state.value)
    }

    @Test
    fun `getValidToken after invalidate still returns the same key`() = runTest {
        val manager = StaticCredentialManager("key-123")
        manager.invalidate()
        assertEquals("key-123", manager.getValidToken())
    }

    // ── Guard: blank token is rejected at construction ─────────────────────────

    @Test
    fun `blank token throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            StaticCredentialManager("   ")
        }
    }

    @Test
    fun `empty token throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            StaticCredentialManager("")
        }
    }

    // ── toString masks the token ──────────────────────────────────────────────

    @Test
    fun `toString does not expose the token`() {
        val manager = StaticCredentialManager("super-secret-key")
        val str = manager.toString()
        assert("super-secret-key" !in str) {
            "toString() must not expose the token; got: $str"
        }
    }
}
