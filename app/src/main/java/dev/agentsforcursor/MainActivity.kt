package dev.agentsforcursor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.agentsforcursor.notify.Notifications
import dev.agentsforcursor.ui.AgentsApp
import dev.agentsforcursor.ui.theme.AgentsTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as AgentsApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeAgentDeepLink(intent)
        setContent {
            AgentsTheme {
                AgentsApp(container)
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
