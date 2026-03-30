package ai.rakuten.credentials

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [RakutenAICredentialManager] that holds a fixed, non-refreshable API key.
 *
 * The state is permanently [CredentialState.Valid] and [invalidate] is a no-op.
 *
 * **Use only for testing and sample apps.** In production, use
 * [RefreshableCredentialManager] so tokens can be rotated without reinstalling the app.
 *
 * @param token The static `RAKUTEN_AI_GATEWAY_KEY` value. Must not be blank.
 */
class StaticCredentialManager(
    private val token: String,
) : RakutenAICredentialManager {

    init {
        require(token.isNotBlank()) { "StaticCredentialManager: token must not be blank." }
    }

    private val _state: MutableStateFlow<CredentialState> =
        MutableStateFlow(CredentialState.Valid)

    override val state: StateFlow<CredentialState> = _state.asStateFlow()

    override suspend fun getValidToken(): String = token

    /** No-op — static credentials do not support refresh. */
    override suspend fun invalidate(): Unit = Unit

    override fun toString(): String = "StaticCredentialManager(token=***)"
}
