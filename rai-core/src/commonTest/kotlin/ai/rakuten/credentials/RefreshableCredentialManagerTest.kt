package ai.rakuten.credentials

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RefreshableCredentialManagerTest {

    // ── #32 returns initial token when Valid ──────────────────────────────────

    @Test
    fun `getValidToken returns initial token when state is Valid`() = runTest {
        val manager = RefreshableCredentialManager("initial-token") { "new-token" }
        assertEquals("initial-token", manager.getValidToken())
    }

    @Test
    fun `initial state is Valid`() {
        val manager = RefreshableCredentialManager("token") { "refreshed" }
        assertIs<CredentialState.Valid>(manager.state.value)
    }

    // ── #33 invalidate triggers refresh and returns to Valid ──────────────────

    @Test
    fun `invalidate marks token as Expired`() = runTest {
        val manager = RefreshableCredentialManager("old-token") { "new-token" }
        manager.invalidate()
        // After invalidate, state is Expired (refresh happens lazily on next getValidToken)
        assertIs<CredentialState.Expired>(manager.state.value)
    }

    @Test
    fun `getValidToken after invalidate calls refresh block and returns new token`() = runTest {
        var refreshCount = 0
        val manager = RefreshableCredentialManager("old-token") {
            refreshCount++
            "refreshed-token"
        }
        manager.invalidate()
        val token = manager.getValidToken()
        assertEquals("refreshed-token", token)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `state transitions to Valid after successful refresh`() = runTest {
        val manager = RefreshableCredentialManager("old-token") { "new-token" }
        manager.invalidate()
        manager.getValidToken()
        assertIs<CredentialState.Valid>(manager.state.value)
    }

    @Test
    fun `invalidate is idempotent when already Expired`() = runTest {
        var refreshCount = 0
        val manager = RefreshableCredentialManager("token") {
            refreshCount++
            "refreshed"
        }
        manager.invalidate()
        manager.invalidate() // second call should not mark as expired again
        manager.getValidToken()
        assertEquals(1, refreshCount)
    }

    // ── #34 concurrent invalidate calls deduplicate to single refresh ─────────

    @Test
    fun `concurrent getValidToken calls after invalidate trigger only one refresh`() = runTest {
        var refreshCount = 0
        val manager = RefreshableCredentialManager("token") {
            refreshCount++
            "refreshed-token"
        }
        manager.invalidate()

        // Launch concurrent getValidToken calls — mutex ensures only one refresh fires
        val results = (1..5).map {
            async { manager.getValidToken() }
        }.map { it.await() }

        assertEquals(1, refreshCount, "refresh block must be called exactly once")
        results.forEach { assertEquals("refreshed-token", it) }
    }

    // ── #35 refresh failure transitions state to Error ────────────────────────

    @Test
    fun `refresh failure sets state to Error`() = runTest {
        val cause = RuntimeException("network error")
        val manager = RefreshableCredentialManager("token") { throw cause }
        manager.invalidate()

        assertFailsWith<RuntimeException> { manager.getValidToken() }
        assertIs<CredentialState.Error>(manager.state.value)
    }

    @Test
    fun `refresh failure propagates the original exception`() = runTest {
        val cause = RuntimeException("auth service down")
        val manager = RefreshableCredentialManager("token") { throw cause }
        manager.invalidate()

        val thrown = assertFailsWith<RuntimeException> { manager.getValidToken() }
        assertEquals("auth service down", thrown.message)
    }

    @Test
    fun `getValidToken in Error state retries the refresh block`() = runTest {
        var callCount = 0
        val manager = RefreshableCredentialManager("token") {
            callCount++
            if (callCount == 1) throw RuntimeException("first call fails")
            "recovered-token"
        }

        // First invalidate → refresh fails → state = Error
        manager.invalidate()
        assertFailsWith<RuntimeException> { manager.getValidToken() }
        assertIs<CredentialState.Error>(manager.state.value)

        // Second getValidToken → refresh retried → succeeds
        val token = manager.getValidToken()
        assertEquals("recovered-token", token)
        assertIs<CredentialState.Valid>(manager.state.value)
    }

    // ── Guard: blank initial token rejected ───────────────────────────────────

    @Test
    fun `blank initial token throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            RefreshableCredentialManager("  ") { "token" }
        }
    }

    // ── toString masks the token ──────────────────────────────────────────────

    @Test
    fun `toString does not expose the token`() {
        val manager = RefreshableCredentialManager("my-secret-key") { "new" }
        val str = manager.toString()
        assert("my-secret-key" !in str) {
            "toString() must not expose the token; got: $str"
        }
    }
}
