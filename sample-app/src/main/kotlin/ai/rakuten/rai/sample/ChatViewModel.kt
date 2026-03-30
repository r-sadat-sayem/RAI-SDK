package ai.rakuten.rai.sample

import ai.koog.prompt.llm.LLModel
import ai.rakuten.android.ApiMessage
import ai.rakuten.android.api.image.ImageGenerationResult
import ai.rakuten.android.api.image.RakutenImageApiService
import ai.rakuten.android.api.text.RakutenTextApiService
import ai.rakuten.android.viewmodel.RaiAgentState
import ai.rakuten.core.RakutenAIModels
import ai.rakuten.core.RakutenAISettings
import ai.rakuten.credentials.RakutenAICredentialManager
import ai.rakuten.rai.sample.ui.format.MessageContent
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

/**
 * A single message in the chat history.
 *
 * @param role    Whether the message is from the user or the assistant.
 * @param content Typed content — plain text, Markdown, code block, or generated image.
 */
data class ChatMessage(
    val role: MessageRole,
    val content: MessageContent,
)

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

/** Aspect ratio options exposed in the image generation panel. */
enum class AspectRatio(val label: String, val value: String) {
    Square("1:1",  "1:1"),
    Landscape("16:9", "16:9"),
    Portrait("9:16", "9:16"),
    Classic("4:3",  "4:3"),
}

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

    /** When `true`, the next send triggers image generation instead of text chat. */
    private val _isImageMode = MutableStateFlow(false)
    val isImageMode: StateFlow<Boolean> = _isImageMode.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(AspectRatio.Square)
    val selectedAspectRatio: StateFlow<AspectRatio> = _selectedAspectRatio.asStateFlow()

    private var currentJob: Job? = null

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectModel(option: ModelOption) { _selectedModel.value = option }

    fun toggleTool(toolName: String) {
        _enabledTools.update { current ->
            if (toolName in current) current - toolName else current + toolName
        }
    }

    fun toggleImageMode() { _isImageMode.update { !it } }

    fun selectAspectRatio(ratio: AspectRatio) { _selectedAspectRatio.value = ratio }

    /**
     * Routes to either [sendTextMessage] or [generateImage] depending on [isImageMode].
     */
    fun send(input: String) {
        if (_isImageMode.value) generateImage(input) else sendTextMessage(input)
    }

    /**
     * Sends [input] to the selected text model and streams the Markdown reply.
     *
     * Full conversation history is forwarded on every turn so the model has multi-turn
     * context. Any in-progress request is cancelled before starting the new one.
     */
    fun sendTextMessage(input: String) {
        currentJob?.cancel()

        _messages.update {
            it + ChatMessage(MessageRole.User, MessageContent.PlainText(input)) +
                    ChatMessage(MessageRole.Assistant, MessageContent.Markdown(""))
        }

        currentJob = viewModelScope.launch {
            _state.value = RaiAgentState.Running
            try {
                val apiKey = credentialManager.getValidToken()
                val service = RakutenTextApiService(apiKey, okHttpClient)

                val enabledNames = _enabledTools.value
                val toolNote = if (enabledNames.isNotEmpty())
                    " You have access to the following tools: ${enabledNames.joinToString()}." else ""
                val systemPrompt = "You are a helpful assistant. Answer concisely.$toolNote"

                val history = _messages.value.dropLast(1)
                    .filter { it.content.previewText.isNotBlank() }
                    .map { ApiMessage(it.role.name.lowercase(), it.content.previewText) }

                val accumulated = StringBuilder()
                _state.value = RaiAgentState.Streaming("")

                service.stream(
                    messages     = history,
                    systemPrompt = systemPrompt,
                    model        = _selectedModel.value.wireModel,
                    onChunk      = { chunk ->
                        accumulated.append(chunk)
                        replaceLastAssistant(MessageContent.Markdown(accumulated.toString()))
                        _state.value = RaiAgentState.Streaming(accumulated.toString())
                    },
                )

                replaceLastAssistant(MessageContent.Markdown(accumulated.toString()))
                _state.value = RaiAgentState.Done(accumulated.toString())

            } catch (e: CancellationException) {
                _state.value = RaiAgentState.Idle
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e::class.simpleName ?: "Unknown error"
                replaceLastAssistant(MessageContent.Markdown("**Error:** $msg"))
                _state.value = RaiAgentState.Error(e)
            }
        }
    }

    /**
     * Generates an image from the text [prompt] using the Gemini image model.
     *
     * The prompt message is added to the chat history immediately, followed by a
     * placeholder that is replaced with the image once generation completes.
     */
    fun generateImage(prompt: String) {
        currentJob?.cancel()

        _messages.update {
            it + ChatMessage(MessageRole.User, MessageContent.PlainText(prompt)) +
                    ChatMessage(MessageRole.Assistant, MessageContent.Markdown(""))
        }

        currentJob = viewModelScope.launch {
            _state.value = RaiAgentState.Running
            try {
                val apiKey = credentialManager.getValidToken()
                val imageService = RakutenImageApiService(apiKey, okHttpClient)

                replaceLastAssistant(MessageContent.Markdown("Generating image…"))

                when (val result = imageService.generateImage(
                    prompt      = prompt,
                    aspectRatio = _selectedAspectRatio.value.value,
                )) {
                    is ImageGenerationResult.Success -> {
                        replaceLastAssistant(
                            MessageContent.GeneratedImage(
                                bytes    = result.imageBytes,
                                mimeType = result.mimeType,
                            )
                        )
                        _state.value = RaiAgentState.Done("Image generated")
                    }
                    is ImageGenerationResult.Error -> {
                        replaceLastAssistant(MessageContent.Markdown("**Image generation failed:** ${result.message}"))
                        _state.value = RaiAgentState.Error(
                            result.cause ?: Exception(result.message)
                        )
                    }
                }

            } catch (e: CancellationException) {
                _state.value = RaiAgentState.Idle
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                replaceLastAssistant(MessageContent.Markdown("**Error:** $msg"))
                _state.value = RaiAgentState.Error(e)
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        _state.value = RaiAgentState.Idle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun replaceLastAssistant(content: MessageContent) {
        _messages.update { msgs ->
            if (msgs.lastOrNull()?.role == MessageRole.Assistant) {
                msgs.dropLast(1) + ChatMessage(MessageRole.Assistant, content)
            } else msgs
        }
    }
}
