package ai.rakuten.android.di

import ai.rakuten.credentials.RakutenAICredentialManager
import org.koin.dsl.module

/**
 * Core Koin module that registers [RakutenAICredentialManager] as a singleton.
 *
 * Include this in your `startKoin { }` block:
 * ```kotlin
 * // Application.kt
 * startKoin {
 *     androidContext(this@App)
 *     modules(
 *         rakutenAICoreModule(
 *             credentialManager = RefreshableCredentialManager(
 *                 initialToken = securePrefs.getGatewayKey(),
 *             ) {
 *                 // Called automatically on expiry or 401
 *                 authService.fetchNewGatewayKey()
 *             }
 *         )
 *     )
 * }
 * ```
 *
 * ### Why [ai.rakuten.core.RakutenAIClient] is not provided here
 *
 * [ai.rakuten.core.RakutenAIClient.create] is a `suspend` function because it
 * fetches the initial credential token from the manager. Koin module definitions
 * are synchronous, so the client cannot be instantiated inside a `single { }` block.
 *
 * Instead, create the client inside [ai.rakuten.android.viewmodel.RaiAgentViewModel.buildAgent],
 * which is a `suspend fun` and has access to `get<RakutenAICredentialManager>()`:
 * ```kotlin
 * override suspend fun buildAgent(onStreamChunk: suspend (String) -> Unit) =
 *     rakutenAIAgent {
 *         credentialManager = get()   // injected from Koin
 *         ...
 *     }
 * ```
 *
 * @param credentialManager The credential manager singleton. Use
 *   [ai.rakuten.credentials.StaticCredentialManager] for testing,
 *   [ai.rakuten.credentials.RefreshableCredentialManager] for production.
 */
public fun rakutenAICoreModule(
    credentialManager: RakutenAICredentialManager,
) = module {
    single<RakutenAICredentialManager> { credentialManager }
}
