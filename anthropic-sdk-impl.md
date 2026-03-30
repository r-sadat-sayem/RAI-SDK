# Implementation Plan: Koog-Preserving Rakuten AI Gateway Adapter for Mobile

## Context

The SDK should continue using Koog for the high-level orchestration features that matter:

- agents
- tool calling
- future RAG / retrieval workflows
- strategy-driven multi-step execution

The problem is lower in the stack: Koog does not directly support the Rakuten AI Gateway as a
first-class mobile integration, and the current implementation couples authentication too tightly
to the underlying HTTP client. When the gateway token rotates, the transport layer can keep using
the stale token.

Rakuten AI Gateway exposes Anthropic-compatible and OpenAI-compatible endpoints. For the current
MVP, Anthropic-compatible chat is the shortest path because the existing SDK already aligns to
Anthropic semantics and Koog's current integration surface is already built around that shape.

Example gateway usage:

```python
client = Anthropic(
    base_url="https://api.ai.public.rakuten-it.com/anthropic/",
    auth_token=os.getenv("RAKUTEN_AI_GATEWAY_KEY"),
)

message = client.messages.create(
    model="claude-3-7-sonnet-20250219",
    max_tokens=1024,
    messages=[
        {
            "role": "user",
            "content": "Hello, Claude. Write me a story about a magic backpack!",
        }
    ],
)
print(message.content)
```

and:

```python
client = OpenAI(
    api_key=os.environ.get("OPENAI_API_KEY"),
    base_url="https://api.ai.public.rakuten-it.com/openai/v1",
)

response = client.chat.completions.create(
    model="gpt-5-mini",
    messages=[
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content": "Write a short story about a magic backpack."},
    ],
)
print(response)
```

For MVP, this plan keeps Koog in place and adapts the Rakuten Anthropic-compatible gateway under
the existing Koog-facing API.

---

## Goal

Build a Rakuten Gateway-backed Koog wrapper for Android/JVM mobile use that:

- preserves Koog agent, tool, and future RAG capabilities
- preserves the current SDK-facing builder APIs as much as possible
- routes requests through `https://api.ai.public.rakuten-it.com/anthropic/`
- supports refreshable gateway credentials correctly
- supports non-streaming and streaming execution

---

## Non-Goals for MVP

- removing Koog from the SDK
- replacing agent strategy logic with a custom chat loop
- building a generalized OpenAI gateway path in the first implementation
- redesigning the public Android/UI state model
- adding multi-provider abstraction unless required by the implementation

OpenAI-compatible gateway support can be a follow-up phase once the Anthropic-compatible path is
stable and tested.

---

## Scope Summary

| Category | Action |
|---|---|
| `RakutenAIAgent`, `RakutenAIAgentConfig` | Keep |
| `RaiAgentViewModel`, `RaiAgentState`, sample UI | Keep |
| Koog tool system (`RaiTool`, `ToolRegistry`) | Keep |
| `RakutenAIClient` | Rewrite internals, preserve role |
| `RakutenAIExecutors` | Keep public API, update internals if needed |
| `RakutenAIModels`, `RakutenAISettings` | Keep, extend if needed |
| Token refresh behavior | Fix |
| Anthropic-compatible gateway transport | Implement/solidify |
| OpenAI-compatible gateway transport | Defer |

---

## Architecture Direction

### Keep Koog as the orchestration layer

Koog remains responsible for:

- prompt execution model
- agent graph / strategy execution
- tool invocation lifecycle
- future retrieval/RAG integration

### Make Rakuten-specific code a provider adapter layer

The Rakuten SDK layer should own:

- base URL configuration
- gateway auth header / token sourcing
- model mapping from SDK model types to Rakuten wire names
- retry/rebuild behavior on 401
- any response/stream normalization needed by Koog

### Preserve typed model support

The SDK should continue exposing typed models via `LLModel` and `RakutenAIModels` rather than
regressing to raw string model ids everywhere. The wire string remains an internal mapping detail.

---

## Proposed Design

## 1. Keep the Public Koog-Facing Entry Points

Do not delete these files:

```text
rai-core/src/commonMain/kotlin/ai/rakuten/agent/RakutenAIAgent.kt
rai-core/src/commonMain/kotlin/ai/rakuten/agent/RakutenAIAgentConfig.kt
rai-core/src/commonMain/kotlin/ai/rakuten/executor/RakutenAIExecutors.kt
rai-android/src/main/kotlin/ai/rakuten/android/viewmodel/RaiAgentViewModel.kt
```

These are the stable SDK-facing entry points and should continue to be the main developer surface.

## 2. Rewrite `RakutenAIClient` as a Rakuten-Aware Koog Transport

**Path**: `rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAIClient.kt`

Current problem:

- it subclasses Koog's `AnthropicLLMClient`
- the API key is fixed at client construction time
- token refresh in `RakutenAICredentialManager` does not rebind the underlying HTTP client

MVP direction:

- keep `RakutenAIClient` as the Rakuten-specific Koog integration point
- preserve `create(credentialManager)` and `invoke(apiKey)` factories
- preserve support for `additionalModels`
- change internals so requests use a fresh valid token whenever necessary

Two acceptable implementation strategies:

### Option A. Rebuild the underlying Koog/HTTP client on 401

- `RakutenAIClient` owns a mutable underlying client instance
- each execution attempt uses the current instance
- on unauthorized response:
  - invalidate credentials
  - fetch a fresh token
  - rebuild the underlying client
  - retry exactly once

### Option B. Bypass the stale client problem by owning the transport one layer lower

- `RakutenAIClient` still presents the Koog-facing behavior expected by `PromptExecutor`
- internally it constructs the request/response path directly against Rakuten's Anthropic endpoint
- Koog remains above it; only the HTTP transport is customized

For MVP, prefer the simpler path that preserves compatibility with the least code churn. If Koog's
client classes are not practically extensible for token refresh, use Option B.

## 3. Keep `RakutenAIExecutors`, but Make Them Thin

**Path**: `rai-core/src/commonMain/kotlin/ai/rakuten/executor/RakutenAIExecutors.kt`

Do not remove these factory functions and executor types. They already fit the intended public
surface well.

Update them only as needed to:

- call the rewritten `RakutenAIClient`
- preserve current streaming semantics
- preserve tool-call compatibility expected by Koog

The goal is internal repair, not API churn.

## 4. Preserve `RakutenAIAgentConfig`

**Path**: `rai-core/src/commonMain/kotlin/ai/rakuten/agent/RakutenAIAgentConfig.kt`

Keep these fields unless implementation constraints prove otherwise:

- `apiKey`
- `credentialManager`
- `model: LLModel`
- `systemPrompt`
- `maxIterations`
- `toolRegistry`
- `streaming`
- `onStreamChunk`
- `onToolCall`
- `onError`

This is the correct abstraction boundary for an SDK that wants Koog features while remaining
gateway-specific underneath.

## 5. Preserve `RakutenAIModels` and Internal Model Mapping

**Path**: `rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAIModels.kt`

Keep:

- typed `LLModel` definitions
- `Default`
- `DEFAULT_MODEL_VERSIONS_MAP`
- `additionalModels` overrides

Add new models by expanding the internal map, not by pushing raw string ids into the public API.

## 6. Keep Anthropic-Compatible Gateway as the MVP Provider

**Path**: `rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAISettings.kt`

For MVP, the only required transport target is:

- `BASE_URL = "https://api.ai.public.rakuten-it.com/anthropic/"`

The OpenAI-compatible gateway example is useful as future-proofing, but supporting both styles in
the first implementation adds risk without helping the immediate Koog use case.

## 7. Treat OpenAI-Compatible Gateway Support as a Follow-Up

Possible future shape:

- `RakutenGatewayProvider.Anthropic`
- `RakutenGatewayProvider.OpenAI`

But do not build this abstraction in MVP unless the implementation genuinely requires it. Avoid
pre-abstracting a second provider path that is not yet used.

---

## Files to Change

### Primary

```text
rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAIClient.kt
rai-core/src/commonMain/kotlin/ai/rakuten/executor/RakutenAIExecutors.kt
rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAISettings.kt
```

### Likely Small or No-Change Files

```text
rai-core/src/commonMain/kotlin/ai/rakuten/agent/RakutenAIAgent.kt
rai-core/src/commonMain/kotlin/ai/rakuten/agent/RakutenAIAgentConfig.kt
rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAIModels.kt
rai-android/src/main/kotlin/ai/rakuten/android/viewmodel/RaiAgentViewModel.kt
sample-app/src/main/kotlin/ai/rakuten/rai/sample/ChatViewModel.kt
```

### New Files

Only add new files if implementation pressure requires them. Prefer avoiding extra layering for
MVP. If needed, likely additions would be:

```text
rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAnthropicTransport.kt
rai-core/src/commonMain/kotlin/ai/rakuten/core/RakutenAnthropicStreamMapper.kt
```

These should live in `rai-core`, not `rai-tools-core`, because they are transport concerns rather
than tool-definition concerns.

---

## Implementation Steps

## Step 1. Inspect Koog Extension Points

Before coding, confirm exactly which layer is the narrowest safe override point:

- can `AnthropicLLMClient` be recreated cheaply and swapped safely?
- where are request headers/base URL resolved?
- where are streaming frames mapped into Koog `StreamFrame` values?
- where are tool calls materialized into Koog `Message.Tool.Call` values?

Decision rule:

- if Koog's existing client path can be refreshed safely, extend it
- if not, implement the Rakuten-specific transport directly under the executor contract

## Step 2. Fix Token Refresh at the Transport Boundary

Implement request execution so that:

- the current valid token is obtained from `RakutenAICredentialManager`
- unauthorized responses trigger `invalidate()`
- a fresh token is fetched
- the request is retried once
- non-auth failures are not retried blindly

This is the primary bug fix.

## Step 3. Preserve Non-Streaming Behavior First

Get plain request/response execution working with:

- system prompt
- regular user messages
- model mapping
- max token handling
- Koog-compatible response conversion

Do this before streaming. It reduces the debugging surface.

## Step 4. Add Streaming Support

Preserve the current behavior from `RakutenAIStreamingPromptExecutor`:

- text responses emit `onChunk` incrementally
- tool-call responses still produce a valid final tool-call result for Koog
- final assembled assistant text remains identical to the non-streaming path

If streaming via Koog's existing client stack cannot be refreshed safely, normalize the gateway
stream into Koog `StreamFrame` values in Rakuten-owned code.

## Step 5. Verify Tool Call Compatibility

Tool support is a hard requirement. Confirm that the Rakuten Anthropic-compatible endpoint returns
tool use payloads in a shape that Koog already expects, or add a small translation layer in the
Rakuten client path.

Do not move tool bridging into `rai-tools-core` unless there is a clear need. Avoid creating a
module cycle.

## Step 6. Keep Sample App Working

The sample app should continue to compile and behave the same from the app developer's perspective:

- select model
- enable tools
- send prompt
- stream partial text
- show final answer or error

---

## Testing Plan

## Unit Tests

Add or update tests for:

- static API key success path
- refreshable credential success path
- 401 then refresh then success
- repeated 401 fails after single retry
- model mapping uses Rakuten wire names
- `additionalModels` override works

## Streaming Tests

Add tests for:

- streamed text is emitted in order
- final accumulated text matches non-streaming result
- tool-call turn does not corrupt stream handling

## Tooling / Agent Tests

Add tests for:

- `rakutenAIAgent {}` still builds correctly
- tool registry still works
- `onToolCall` still fires if previously supported
- `onError` still fires on unrecoverable failures

## Smoke Test

Manual smoke test in `sample-app`:

- valid token prompt succeeds
- expired token scenario recovers after refresh
- enabled tool can be called by the model
- streaming UI still updates progressively

---

## Risks

### Risk 1. Koog internals may be too rigid for clean token refresh

Mitigation:

- decide early whether to extend or bypass the current client layer
- do not force inheritance if composition is cleaner

### Risk 2. Streaming shape may differ subtly from what Koog expects

Mitigation:

- implement non-streaming first
- add explicit streaming frame tests before wiring UI assumptions

### Risk 3. Tool-call payloads may require translation

Mitigation:

- test with at least one real tool in the sample app
- keep translation local to `rai-core`

### Risk 4. Over-abstracting for OpenAI gateway too early

Mitigation:

- ship Anthropic-compatible gateway MVP first
- add OpenAI support only when there is a real integration need

---

## MVP Exit Criteria

The MVP is complete when all of the following are true:

- `rakutenAIAgent {}` remains the primary entry point
- Koog agents continue to run unchanged at the public API layer
- Rakuten gateway Anthropic endpoint is used successfully on Android/JVM
- credential refresh works without rebuilding the app/session manually
- streaming works
- tool calling still works
- sample app continues to function with the existing UX pattern

---

## Out of Scope Follow-Ups

- OpenAI-compatible Rakuten gateway support
- explicit RAG samples and retrieval adapters
- richer observability/tracing around gateway calls
- model discovery from gateway at runtime
- provider-selection DSL if Anthropic and OpenAI support both become first-class later

---

## Recommendation

Proceed with this Koog-preserving MVP.

This keeps the SDK aligned with the product goal: Rakuten Gateway support on mobile without losing
Koog's higher-level capabilities. It fixes the real weakness, the transport/auth layer, instead of
replacing the part of the stack that you explicitly want to keep.
