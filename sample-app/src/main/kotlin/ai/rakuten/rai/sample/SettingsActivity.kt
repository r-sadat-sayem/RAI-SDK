package ai.rakuten.rai.sample

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ai.rakuten.rai.sample.databinding.ActivitySettingsBinding

/**
 * Lets the user enter and persist their `RAKUTEN_AI_GATEWAY_KEY`.
 *
 * The key is stored in [EncryptedSharedPreferences][CredentialStorage] and
 * takes effect the next time the app is launched (Koin is re-initialized in
 * [SampleApplication.onCreate]).
 *
 * In a production app you would fetch the token from your backend rather than
 * asking the user to enter it manually.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill with the currently stored key (masked display only).
        CredentialStorage.loadKey(this)?.let {
            binding.etApiKey.hint = "Key saved (${it.take(6)}…)"
        }

        binding.btnSave.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Key cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CredentialStorage.saveKey(this, key)
            Toast.makeText(this, "Key saved. Restart the app to apply.", Toast.LENGTH_LONG).show()
            finish()
        }

        binding.btnClear.setOnClickListener {
            CredentialStorage.clearKey(this)
            binding.etApiKey.text?.clear()
            binding.etApiKey.hint = "Enter RAKUTEN_AI_GATEWAY_KEY"
            Toast.makeText(this, "Key cleared.", Toast.LENGTH_SHORT).show()
        }
    }
}
