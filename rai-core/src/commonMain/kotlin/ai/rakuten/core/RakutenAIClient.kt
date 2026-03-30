package ai.rakuten.core

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.llm.LLModel
import ai.rakuten.credentials.RakutenAICredentialManager
import ai.rakuten.credentials.StaticCredentialManager

/**
 * An [AnthropicLLMClient] pre-configured to connect to the Rakuten AI Gateway.
 *
 * The gateway speaks the same wire protocol as the Anthropic Messages API — only the
 * [base URL][RakutenAISettings.BASE_URL] and model version strings differ. Authentication
 * uses the same `x-api-key` header.
 *
 * ### Construction
 *
 * **Production** — use the suspend factory [create] with a [ai.rakuten.credentials.RefreshableCredentialManager]
 * so tokens refresh automatically:
 * ```kotlin
 * val client = RakutenAIClient.create(
 *     credentialManager = RefreshableCredentialManager(initialToken = prefs.getKey()) {
 *         authService.fetchNewGatewayKey()
 *     }
 * )
 * ```
 *
 * **Testing / samples** — use the operator `invoke` shorthand:
 * ```kotlin
 * val client = RakutenAIClient(apiKey = "RAKUTEN_AI_GATEWAY_KEY")
 * ```
 *
 * ### Registering new models before a library update
 * ```kotlin
 * val newModel = LLModel(provider = LLMProvider.Anthropic, id = "rakuten-new-model", ...)
 * val client = RakutenAIClient(
 *     apiKey = key,
 *     additionalModels = mapOf(newModel to "new-model-wire-name-20260601"),
 * )
 * ```
 *
 * @param apiKey The gateway key placed in the `x-api-key` header.
 * @param credentialManager The manager that owns this key — stored for observability.
 * @param additionalModels Extra [LLModel]-to-wire-string mappings not yet in [RakutenAIModels].
 */
class RakutenAIClient private constructor(
    apiKey: String,
    val credentialManager: RakutenAICredentialManager,
    additionalModels: Map<LLModel, String> = emptyMap(),
) : AnthropicLLMClient(
    apiKey   = apiKey,
    settings = RakutenAISettings.toAnthropicClientSettings(additionalModels),
) {

    companion object {

        /**
         * Creates a [RakutenAIClient] by fetching the initial token from [credentialManager].
         *
         * Prefer this over the static-key constructor for production apps so that token
         * rotation is handled without restarting the client.
         *
         * @param credentialManager Provides and manages the gateway API key.
         * @param additionalModels Extra model-to-wire-string mappings.
         */
        suspend fun create(
            credentialManager: RakutenAICredentialManager,
            additionalModels: Map<LLModel, String> = emptyMap(),
        ): RakutenAIClient = RakutenAIClient(
            apiKey           = credentialManager.getValidToken(),
            credentialManager = credentialManager,
            additionalModels = additionalModels,
        )

        /**
         * Creates a [RakutenAIClient] with a fixed [apiKey].
         *
         * Wraps the key in a [StaticCredentialManager]. Suitable for testing and
         * sample applications where tokens do not expire.
         *
         * @param apiKey The `RAKUTEN_AI_GATEWAY_KEY` value. Must not be blank.
         * @param additionalModels Extra model-to-wire-string mappings.
         */
        operator fun invoke(
            apiKey: String,
            additionalModels: Map<LLModel, String> = emptyMap(),
        ): RakutenAIClient = RakutenAIClient(
            apiKey           = apiKey,
            credentialManager = StaticCredentialManager(apiKey),
            additionalModels = additionalModels,
        )
    }

    override fun toString(): String =
        "RakutenAIClient(manager=${credentialManager::class.simpleName}, state=${credentialManager.state.value})"
}
