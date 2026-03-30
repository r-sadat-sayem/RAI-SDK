package ai.rakuten.credentials

import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the lifecycle of the API key used to authenticate with the Rakuten AI Gateway.
 *
 * The SDK ships two ready-made implementations:
 * - [StaticCredentialManager] — holds a fixed key; use for testing and demos.
 * - [RefreshableCredentialManager] — automatically refreshes the key on expiry or 401.
 *
 * Provide a custom implementation to integrate with your app's own auth infrastructure.
 *
 * ### Observing credential state
 * ```kotlin
 * lifecycleScope.launch {
 *     credentialManager.state.collect { state ->
 *         if (state is CredentialState.Expired) showReauthDialog()
 *     }
 * }
 * ```
 */
interface RakutenAICredentialManager {

    /**
     * Observable lifecycle state of the managed credential.
     *
     * Emits a new value whenever the state transitions (e.g. Valid → Refreshing → Valid).
     */
    val state: StateFlow<CredentialState>

    /**
     * Returns a valid gateway token, triggering a refresh first if the state is
     * [CredentialState.Expired] or [CredentialState.Error].
     *
     * Implementations must serialize concurrent refresh calls so that multiple
     * waiting coroutines share a single network round-trip.
     *
     * @throws Exception if the refresh attempt fails.
     */
    suspend fun getValidToken(): String

    /**
     * Marks the current token as [CredentialState.Expired].
     *
     * Call this when you receive a 401 response from the gateway. The next call to
     * [getValidToken] will invoke the refresh mechanism. The SDK calls this automatically
     * when using [RefreshableCredentialManager].
     */
    suspend fun invalidate()
}
