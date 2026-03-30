package ai.rakuten.android.di

import ai.rakuten.rai.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Koin module that provides a logging-enabled [OkHttpClient] for use with
 * [ai.rakuten.android.RakutenAIChatClient].
 *
 * Include this alongside [rakutenAICoreModule] in your `startKoin { }` block:
 * ```kotlin
 * startKoin {
 *     androidContext(this@App)
 *     modules(
 *         rakutenAICoreModule(credentialManager = ...),
 *         raiHttpModule(logLevel = if (BuildConfig.DEBUG)
 *             HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE),
 *     )
 * }
 * ```
 *
 * @param logLevel HTTP logging verbosity.
 *   Use [HttpLoggingInterceptor.Level.BODY] for debug builds and
 *   [HttpLoggingInterceptor.Level.NONE] for release builds.
 */
fun raiHttpModule(
    logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY,
) = module {

    single<HttpLoggingInterceptor> {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) logLevel else HttpLoggingInterceptor.Level.NONE
        }
    }

    single<OkHttpClient> {
        OkHttpClient.Builder()
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
