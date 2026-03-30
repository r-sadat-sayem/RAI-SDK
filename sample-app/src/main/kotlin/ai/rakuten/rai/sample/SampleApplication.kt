package ai.rakuten.rai.sample

import android.app.Application
import ai.rakuten.android.di.rakutenAICoreModule
import ai.rakuten.android.di.raiHttpModule
import ai.rakuten.credentials.StaticCredentialManager
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val gatewayKey = CredentialStorage.loadKey(this) ?: "not-configured"

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@SampleApplication)
            modules(
                rakutenAICoreModule(
                    credentialManager = StaticCredentialManager(gatewayKey),
                ),
                raiHttpModule(
                    logLevel = if (BuildConfig.DEBUG)
                        HttpLoggingInterceptor.Level.BODY
                    else
                        HttpLoggingInterceptor.Level.NONE,
                ),
            )
        }
    }
}
