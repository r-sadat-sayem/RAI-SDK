package ai.rakuten.credentials

/**
 * Represents the lifecycle state of a Rakuten AI Gateway credential.
 *
 * Observe [RakutenAICredentialManager.state] to react to transitions in your UI
 * or session management layer — e.g. showing a "session expired" banner when
 * the state reaches [Expired].
 */
public sealed class CredentialState {

    /** The current token is valid and ready to use. */
    public data object Valid : CredentialState()

    /** A token refresh is in progress. Requests are queued until refresh completes. */
    public data object Refreshing : CredentialState()

    /**
     * The token has expired or been explicitly invalidated via [RakutenAICredentialManager.invalidate].
     *
     * @param reason Optional human-readable explanation, e.g. `"401 from gateway"`.
     */
    public data class Expired(val reason: String? = null) : CredentialState()

    /**
     * A refresh attempt failed with an exception.
     *
     * @param cause The underlying exception returned by the refresh block.
     */
    public data class Error(val cause: Throwable) : CredentialState()
}
