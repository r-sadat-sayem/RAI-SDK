package ai.rakuten.android.viewmodel

import ai.koog.agents.core.agent.AIAgent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base [ViewModel] that manages the full lifecycle of a single [AIAgent] run.
 *
 * Subclass this and override [buildAgent] to supply an agent configured for your
 * use case. Wire the [onStreamChunk] parameter to enable the [RaiAgentState.Streaming]
 * state updates:
 *
 * ```kotlin
 * class ChatViewModel : RaiAgentViewModel() {
 *     override suspend fun buildAgent(
 *         onStreamChunk: suspend (String) -> Unit
 *     ) = rakutenAIAgent {
 *         apiKey        = securePrefs.getGatewayKey()
 *         systemPrompt  = "You are a helpful assistant."
 *         streaming     = true
 *         this.onStreamChunk = onStreamChunk   // <-- wire it in
 *         toolRegistry  = ToolRegistry { raiTool(WeatherTool) }
 *     }
 * }
 * ```
 *
 * ### State flow
 * ```
 * Idle → Running → (Streaming per token)* → Done
 *                                         → Error
 *      → Idle  (via cancel())
 * ```
 */
abstract class RaiAgentViewModel : ViewModel() {

    private val _state: MutableStateFlow<RaiAgentState> =
        MutableStateFlow(RaiAgentState.Idle)

    /** Observable state of the current agent run. Collect from your Fragment or Composable. */
    val state: StateFlow<RaiAgentState> = _state.asStateFlow()

    private var currentJob: Job? = null

    /**
     * Provides the [AIAgent] to run.
     *
     * Called each time [send] is invoked. This function is `suspend` because
     * [ai.rakuten.agent.rakutenAIAgent] fetches the initial credential token at build time.
     *
     * @param onStreamChunk Callback the ViewModel uses to emit [RaiAgentState.Streaming]
     *   updates. Pass it directly to `onStreamChunk` in the agent builder.
     */
    protected abstract suspend fun buildAgent(
        onStreamChunk: suspend (String) -> Unit,
    ): AIAgent<String, String>

    /**
     * Sends [input] to the agent and starts a new run.
     *
     * Any in-progress run is cancelled first. State transitions immediately to
     * [RaiAgentState.Running], then to [RaiAgentState.Streaming] per token
     * (if streaming is enabled), and finally to [RaiAgentState.Done] or
     * [RaiAgentState.Error].
     *
     * @param input The user message or task description to pass to the agent.
     */
    fun send(input: String) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch(Dispatchers.Default) {
            _state.value = RaiAgentState.Running
            val accumulated = StringBuilder()
            try {
                val agent = buildAgent { chunk ->
                    accumulated.append(chunk)
                    _state.value = RaiAgentState.Streaming(accumulated.toString())
                }
                val result = agent.run(input)
                _state.value = RaiAgentState.Done(result)
            } catch (e: CancellationException) {
                _state.value = RaiAgentState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = RaiAgentState.Error(cause = e)
            }
        }
    }

    /**
     * Cancels the current agent run (if any) and resets state to [RaiAgentState.Idle].
     */
    fun cancel() {
        currentJob?.cancel()
        _state.value = RaiAgentState.Idle
    }
}
