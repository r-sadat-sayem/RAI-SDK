package ai.rakuten.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.TypeToken

/**
 * Base class for tools in the RAI SDK ecosystem.
 *
 * Extends Koog's [SimpleTool] (result type is always [String]) with two metadata
 * fields useful for logging and tracing: [toolVersion] and [category].
 *
 * ### Defining a tool
 * ```kotlin
 * object WeatherTool : RaiTool<WeatherTool.Args>(
 *     argsType    = typeToken<Args>(),
 *     name        = "get_weather",
 *     description = "Returns current weather conditions for a city.",
 *     toolVersion = "1.0",
 *     category    = "environment",
 * ) {
 *     @Serializable
 *     data class Args(
 *         @LLMDescription("City name, e.g. Tokyo")
 *         val city: String,
 *     )
 *
 *     override suspend fun execute(args: Args): String =
 *         WeatherApiClient.fetch(args.city).toSummary()
 * }
 * ```
 *
 * ### Registering a tool
 * ```kotlin
 * val toolRegistry = ToolRegistry { raiTool(WeatherTool) }
 * ```
 *
 * @param TArgs The argument type. Must be annotated with `@Serializable`.
 * @param argsType [TypeToken] for [TArgs], obtained via `typeToken<TArgs>()`.
 * @param name Unique snake_case tool name exposed to the LLM (e.g. `"get_weather"`).
 * @param description Human-readable explanation the LLM uses to decide when to call this tool.
 * @param toolVersion Semantic version of this tool's implementation. Defaults to `"1.0"`.
 * @param category Optional grouping label for logs and traces (e.g. `"finance"`, `"environment"`).
 */
abstract class RaiTool<TArgs>(
    argsType: TypeToken,
    name: String,
    description: String,
    val toolVersion: String = "1.0",
    val category: String = "general",
) : SimpleTool<TArgs>(
    argsType    = argsType,
    name        = name,
    description = description,
)
