package ai.rakuten.android.viewmodel

/**
 * Lifecycle states emitted by [RaiAgentViewModel.state].
 *
 * Collect from a Fragment or Composable and switch on this sealed class to drive your UI:
 * ```kotlin
 * viewModel.state.collect { state ->
 *     when (state) {
 *         RaiAgentState.Idle           -> Unit
 *         RaiAgentState.Running        -> showSpinner()
 *         is RaiAgentState.Streaming   -> renderText(state.accumulatedText)
 *         is RaiAgentState.Done        -> showFinalAnswer(state.result)
 *         is RaiAgentState.Error       -> showError(state.cause.message)
 *     }
 * }
 * ```
 *
 * State machine:
 * ```
 * Idle → Running → Streaming* → Done
 *                             → Error
 *      → Idle  (on cancel)
 * ```
 */
public sealed class RaiAgentState {

    /** No run in progress. Initial state, and state after [RaiAgentViewModel.cancel]. */
    public data object Idle : RaiAgentState()

    /** Run started; no response text has arrived yet. */
    public data object Running : RaiAgentState()

    /**
     * A streaming chunk has arrived from the model.
     *
     * Emitted repeatedly — once per text token — when the agent is configured with
     * `streaming = true`. Each emission contains the **full accumulated text** so far,
     * so you can assign it directly to a `TextView` or Compose `Text` without
     * maintaining a separate buffer.
     *
     * @param accumulatedText All text tokens received in this run, concatenated.
     */
    public data class Streaming(val accumulatedText: String) : RaiAgentState()

    /**
     * The run completed successfully.
     *
     * @param result The final string output returned by the agent.
     */
    public data class Done(val result: String) : RaiAgentState()

    /**
     * The run failed with an exception.
     *
     * @param cause The exception that terminated the agent run.
     */
    public data class Error(val cause: Throwable) : RaiAgentState()
}
