package ai.rakuten.tools

import ai.koog.agents.core.tools.ToolRegistryBuilder

/**
 * Registers a [RaiTool] with the [ToolRegistryBuilder].
 *
 * Prefer this over the plain `tool()` extension when your tools extend [RaiTool],
 * as it preserves version and category metadata for logging and tracing.
 *
 * ```kotlin
 * val toolRegistry = ToolRegistry {
 *     raiTool(WeatherTool)
 *     raiTool(StockPriceTool)
 * }
 * ```
 *
 * @param tool The [RaiTool] instance to register. Tool names must be unique in the registry.
 */
fun ToolRegistryBuilder.raiTool(tool: RaiTool<*>): ToolRegistryBuilder = tool(tool)

/**
 * Registers multiple [RaiTool] instances at once.
 *
 * Equivalent to calling [raiTool] for each tool individually.
 *
 * ```kotlin
 * val toolRegistry = ToolRegistry {
 *     raiTools(WeatherTool, StockPriceTool, CalendarTool)
 * }
 * ```
 *
 * @param tools The [RaiTool] instances to register. Tool names must be unique.
 */
fun ToolRegistryBuilder.raiTools(vararg tools: RaiTool<*>): ToolRegistryBuilder {
    tools.forEach { tool(it) }
    return this
}
