package ai.rakuten.rai.sample

import ai.koog.prompt.llm.LLModel
import ai.rakuten.android.ApiMessage
import ai.rakuten.android.RakutenAIChatClient
import ai.rakuten.android.viewmodel.RaiAgentState
import ai.rakuten.core.RakutenAIModels
import ai.rakuten.core.RakutenAISettings
import ai.rakuten.credentials.RakutenAICredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class MessageRole { User, Assistant }

data class ChatMessage(val role: MessageRole, val text: String)

data class ModelOption(
    val model: LLModel,
    val displayName: String,
    val description: String,
    val wireModel: String,
)

data class ToolOption(
    val name: String,
    val displayName: String,
    val description: String,
)

class ChatViewModel : ViewModel(), KoinComponent {

    private val credentialManager: RakutenAICredentialManager by inject()
    private val okHttpClient: OkHttpClient by inject()

    val availableModels: List<ModelOption> = RakutenAIModels.DEFAULT_MODEL_VERSIONS_MAP
        .entries
        .mapNotNull { (model, wireModel) ->
            when (model) {
                RakutenAIModels.Claude4_6Sonnet -> ModelOption(
                    model       = model,
                    displayName = "Claude Sonnet 4.6",
                    description = "Latest · 200K context · vision",
                    wireModel   = wireModel,
                )
                RakutenAIModels.Claude4_5Sonnet -> ModelOption(
                    model       = model,
                    displayName = "Claude Sonnet 4.5",
                    description = "Balanced · 200K context",
                    wireModel   = wireModel,
                )
                RakutenAIModels.Claude4_5Haiku  -> ModelOption(
                    model       = model,
                    displayName = "Claude Haiku 4.5",
                    description = "Fast & efficient",
                    wireModel   = wireModel,
                )
                else -> null
            }
        }
        .sortedBy { it.displayName }

    val availableTools = listOf(
        ToolOption(EchoTool.name,        "Echo",       "Echo a message back"),
        ToolOption(CurrentTimeTool.name, "Clock",      "Get the current time"),
        ToolOption(WordCountTool.name,   "Word Count", "Count words in text"),
    )

    // ── Observable state ──────────────────────────────────────────────────────

    private val _state = MutableStateFlow<RaiAgentState>(RaiAgentState.Idle)
    val state: StateFlow<RaiAgentState> = _state.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        availableModels.firstOrNull { it.wireModel == RakutenAISettings.DEFAULT_WIRE_MODEL }
            ?: availableModels.first()
    )
    val selectedModel: StateFlow<ModelOption> = _selectedModel.asStateFlow()

    private val _enabledTools = MutableStateFlow<Set<String>>(emptySet())
    val enabledTools: StateFlow<Set<String>> = _enabledTools.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var currentJob: Job? = null

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectModel(option: ModelOption) { _selectedModel.value = option }

    fun toggleTool(toolName: String) {
        _enabledTools.update { current ->
            if (toolName in current) current - toolName else current + toolName
        }
    }

    /**
     * Sends [input] to the selected model and streams the reply into the message list.
     *
     * Full conversation history is forwarded on every turn so the model has multi-turn
     * context. Any in-progress request is cancelled before starting the new one.
     */
    fun sendMessage(input: String) {
        currentJob?.cancel()

        _messages.update { it + ChatMessage(MessageRole.User, input) + ChatMessage(MessageRole.Assistant, "") }

        currentJob = viewModelScope.launch {
            _state.value = RaiAgentState.Running
            try {
                val apiKey = credentialManager.getValidToken()
                val client = RakutenAIChatClient(apiKey, okHttpClient)

                val enabledNames = _enabledTools.value
                val toolNote = if (enabledNames.isNotEmpty())
                    " You have access to the following tools: ${enabledNames.joinToString()}." else ""
                val systemPrompt = "You are a helpful assistant. Answer concisely.$toolNote"

                val history = _messages.value.dropLast(1)
                    .filter { it.text.isNotBlank() }
                    .map { ApiMessage(it.role.name.lowercase(), it.text) }

                val accumulated = StringBuilder()
                _state.value = RaiAgentState.Streaming("")

                client.chatStream(
                    messages     = history,
                    systemPrompt = systemPrompt,
                    model        = _selectedModel.value.wireModel,
                    onChunk      = { chunk ->
                        accumulated.append(chunk)
                        replaceLastAssistant(accumulated.toString())
                        _state.value = RaiAgentState.Streaming(accumulated.toString())
                    },
                )

                val finalText = accumulated.toString()
                replaceLastAssistant(finalText)
                _state.value = RaiAgentState.Done(finalText)

            } catch (e: CancellationException) {
                _state.value = RaiAgentState.Idle
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e::class.simpleName ?: "Unknown error"
                replaceLastAssistant("Error: $msg")
                _state.value = RaiAgentState.Error(e)
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        _state.value = RaiAgentState.Idle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun replaceLastAssistant(text: String) {
        _messages.update { msgs ->
            if (msgs.lastOrNull()?.role == MessageRole.Assistant) {
                msgs.dropLast(1) + ChatMessage(MessageRole.Assistant, text)
            } else msgs
        }
    }
}
