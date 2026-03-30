package ai.rakuten.rai.sample

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Thin wrapper over [EncryptedSharedPreferences] for securely storing the
 * `RAKUTEN_AI_GATEWAY_KEY` on-device.
 *
 * The key is AES-256 encrypted via the Android Keystore. It never appears in
 * plain text in SharedPreferences, log output, or backups.
 */
internal object CredentialStorage {

    private const val PREFS_FILE = "rai_credentials"
    private const val KEY_GATEWAY = "rakuten_ai_gateway_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * Returns the stored gateway key. Falls back to the value baked in at
     * build time from `local.properties` if nothing has been saved yet.
     */
    fun loadKey(context: Context): String? {
        val stored: String? = prefs(context).getString(KEY_GATEWAY, null)?.takeIf { it.isNotBlank() }
        if (stored != null) return stored
        val buildKey: String = BuildConfig.RAKUTEN_AI_GATEWAY
        return buildKey.takeIf { it.isNotBlank() }
    }

    /** Persists [key] securely. Overwrites any previously stored value. */
    fun saveKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GATEWAY, key.trim()).apply()
    }

    /** Removes the stored key. */
    fun clearKey(context: Context) {
        prefs(context).edit().remove(KEY_GATEWAY).apply()
    }
}
