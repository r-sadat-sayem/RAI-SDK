package ai.rakuten.core

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Model definitions available through the Rakuten AI Gateway.
 *
 * Each [LLModel] uses [LLMProvider.Anthropic] because the gateway exposes an
 * Anthropic-compatible API. The [LLModel.id] is an SDK-internal identifier;
 * the exact string sent on the wire is defined in [DEFAULT_MODEL_VERSIONS_MAP].
 *
 * ### Adding models not yet listed here
 * Pass a `Map<LLModel, String>` to [RakutenAIClient]'s `additionalModels` parameter —
 * no library update required:
 * ```kotlin
 * val myModel = LLModel(provider = LLMProvider.Anthropic, id = "rakuten-my-model", ...)
 * val client = RakutenAIClient(
 *     apiKey = key,
 *     additionalModels = mapOf(myModel to "my-model-wire-name-20260101"),
 * )
 * ```
 */
object RakutenAIModels {

    /**
     * Claude 4.6 Sonnet — the default model available through Rakuten AI Gateway.
     *
     * - Context window : 200 000 tokens
     * - Max output     : 64 000 tokens
     * - Capabilities   : text completion, tool use, tool choice, vision (images)
     */
    val Claude4_6Sonnet: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "rakuten-claude-sonnet-4-6",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
            LLMCapability.Vision.Image,
        ),
        contextLength    = 200_000,
        maxOutputTokens  = 64_000,
    )
    val Claude4_5Sonnet: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "rakuten-claude-sonnet-4-5",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
            LLMCapability.Vision.Image,
        ),
        contextLength    = 200_000,
        maxOutputTokens  = 64_000,
    )
    val Claude4_5Haiku: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "rakuten-claude-haiku-4-5",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
            LLMCapability.Vision.Image,
        ),
        contextLength    = 200_000,
        maxOutputTokens  = 64_000,
    )

    /** Model used when none is explicitly set in the agent builder. */
    val Default: LLModel = Claude4_6Sonnet

    /** Maps SDK [LLModel] identifiers → exact wire strings sent to the gateway. */
    public val DEFAULT_MODEL_VERSIONS_MAP: Map<LLModel, String> = mapOf(
        Claude4_6Sonnet to "claude-sonnet-4-6",
        Claude4_5Sonnet to "claude-sonnet-4-5-20250929",
        Claude4_5Haiku to "claude-haiku-4-5-20251001",
    )
}
