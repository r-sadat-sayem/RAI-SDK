package ai.rakuten.core

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.llm.LLModel
import ai.rakuten.core.RakutenAISettings.BASE_URL

/**
 * Internal constants and factory helpers for the Rakuten AI Gateway connection.
 *
 * The gateway exposes an Anthropic-compatible API, so [AnthropicClientSettings] is used
 * for the underlying HTTP configuration. Only [BASE_URL] and the model version strings
 * differ from a standard Anthropic setup.
 */
object RakutenAISettings {

    /** Base URL of the Rakuten AI Gateway Anthropic-compatible endpoint. */
    const val BASE_URL = "https://api.ai.public.rakuten-it.com/anthropic/"

    /** Anthropic API version accepted by the gateway. */
    const val API_VERSION = "2023-06-01"

    /** Path for chat completions, appended to [BASE_URL]. */
    const val MESSAGES_PATH = "v1/messages"

    /** Path for listing available models, appended to [BASE_URL]. */
    const val MODELS_PATH = "v1/models"

    /**
     * Wire model name sent in the `model` field of every request.
     * Matches [RakutenAIModels.DEFAULT_MODEL_VERSIONS_MAP] for [RakutenAIModels.Claude4_6Sonnet].
     */
    const val DEFAULT_WIRE_MODEL = "claude-sonnet-4-6"

    /**
     * Builds [AnthropicClientSettings] pre-configured for the Rakuten AI Gateway.
     *
     * @param additionalModels Extra [LLModel]-to-wire-string mappings for models not yet
     *   present in [RakutenAIModels.DEFAULT_MODEL_VERSIONS_MAP].
     */
    fun toAnthropicClientSettings(
        additionalModels: Map<LLModel, String> = emptyMap(),
    ): AnthropicClientSettings = AnthropicClientSettings(
        baseUrl          = BASE_URL,
        apiVersion       = API_VERSION,
        messagesPath     = MESSAGES_PATH,
        modelsPath       = MODELS_PATH,
        modelVersionsMap = RakutenAIModels.DEFAULT_MODEL_VERSIONS_MAP + additionalModels,
    )
}
