package ai.rakuten.rai.sample

import ai.rakuten.rai.sample.ui.ChatScreen
import ai.rakuten.rai.sample.ui.theme.RaiSampleTheme
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (BuildConfig.DEBUG) {
            val key = CredentialStorage.loadKey(this)
            val isConfigured = key != null && key != "not-configured"
            Toast.makeText(
                this,
                if (isConfigured) "API key configured ✓" else "API key not set — tap ⚙ to configure",
                Toast.LENGTH_LONG,
            ).show()
        }

        setContent {
            RaiSampleTheme {
                ChatScreen(
                    viewModel            = viewModel,
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }
    }
}
