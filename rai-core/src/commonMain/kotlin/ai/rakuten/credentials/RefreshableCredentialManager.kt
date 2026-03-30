package ai.rakuten.credentials

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [RakutenAICredentialManager] that refreshes its token automatically when the
 * current one expires or is rejected with a 401.
 *
 * Refresh calls are serialized via a [Mutex] so that multiple in-flight requests
 * do not trigger redundant network calls — only one refresh is made regardless of
 * how many coroutines are waiting.
 *
 * ### Usage
 * ```kotlin
 * val credentialManager = RefreshableCredentialManager(
 *     initialToken = secureStorage.read("RAKUTEN_AI_GATEWAY_KEY"),
 * ) {
 *     // Suspend lambda called on expiry or 401. Must return a non-blank token.
 *     myAuthService.fetchNewGatewayKey()
 * }
 * ```
 *
 * @param initialToken The token to use immediately. Must not be blank.
 * @param refreshBlock Suspend lambda invoked whenever a new token is needed.
 *   Must return a non-blank string or throw if the refresh cannot be completed.
 */
class RefreshableCredentialManager(
    initialToken: String,
    private val refreshBlock: suspend () -> String,
) : RakutenAICredentialManager {

    init {
        require(initialToken.isNotBlank()) { "RefreshableCredentialManager: initialToken must not be blank." }
    }

    private val mutex = Mutex()
    private var currentToken: String = initialToken

    private val _state: MutableStateFlow<CredentialState> =
        MutableStateFlow(CredentialState.Valid)

    override val state: StateFlow<CredentialState> = _state.asStateFlow()

    /**
     * Returns the current token, refreshing it first if in [CredentialState.Expired]
     * or [CredentialState.Error] state.
     *
     * Thread-safe: concurrent callers block on [Mutex] and share a single refresh attempt.
     *
     * @throws Exception propagated from [refreshBlock] if refresh fails.
     */
    override suspend fun getValidToken(): String = mutex.withLock {
        if (_state.value is CredentialState.Expired || _state.value is CredentialState.Error) {
            refresh()
        }
        currentToken
    }

    /**
     * Marks the token as [CredentialState.Expired].
     *
     * The next [getValidToken] call will invoke [refreshBlock]. Only transitions
     * from [CredentialState.Valid] — already-expired tokens are not re-marked.
     */
    override suspend fun invalidate(): Unit = mutex.withLock {
        if (_state.value is CredentialState.Valid) {
            _state.value = CredentialState.Expired(reason = "Invalidated by 401 response")
        }
    }

    private suspend fun refresh() {
        _state.value = CredentialState.Refreshing
        try {
            val newToken = refreshBlock()
            require(newToken.isNotBlank()) { "refreshBlock returned a blank token." }
            currentToken = newToken
            _state.value = CredentialState.Valid
        } catch (e: Exception) {
            _state.value = CredentialState.Error(cause = e)
            throw e
        }
    }

    override fun toString(): String =
        "RefreshableCredentialManager(token=***, state=${_state.value})"
}
