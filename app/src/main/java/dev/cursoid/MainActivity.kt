package dev.cursoid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.cursoid.notify.Notifications
import dev.cursoid.ui.CursoidApp
import dev.cursoid.ui.theme.CursoidTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as CursoidApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeAgentDeepLink(intent)
        setContent {
            CursoidTheme {
                CursoidApp(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeAgentDeepLink(intent)
    }

    private fun consumeAgentDeepLink(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_AGENT_ID)?.let {
            container.pendingAgentId.value = it
        }
    }
}
