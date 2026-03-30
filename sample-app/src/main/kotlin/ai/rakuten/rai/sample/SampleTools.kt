package ai.rakuten.rai.sample

import ai.koog.serialization.typeToken
import ai.rakuten.tools.RaiTool
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Echoes the message back — good for verifying tool-call round-trips. */
object EchoTool : RaiTool<EchoTool.Args>(
    argsType    = typeToken<Args>(),
    name        = "echo",
    description = "Echoes the provided message back. Useful for testing.",
    toolVersion = "1.0",
    category    = "utility",
) {
    @Serializable data class Args(val message: String)
    override suspend fun execute(args: Args) = "Echo: ${args.message}"
}

/** Returns the current local date and time. */
object CurrentTimeTool : RaiTool<CurrentTimeTool.Args>(
    argsType    = typeToken<Args>(),
    name        = "get_current_time",
    description = "Returns the current local date and time.",
    toolVersion = "1.0",
    category    = "utility",
) {
    @Serializable class Args
    override suspend fun execute(args: Args): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}

/** Counts the words in a piece of text. */
object WordCountTool : RaiTool<WordCountTool.Args>(
    argsType    = typeToken<Args>(),
    name        = "count_words",
    description = "Counts the number of words in the provided text.",
    toolVersion = "1.0",
    category    = "utility",
) {
    @Serializable data class Args(val text: String)
    override suspend fun execute(args: Args): String {
        val count = args.text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return "Word count: $count"
    }
}
