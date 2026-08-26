package dev.cursoid.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.cursoid.AppContainer
import dev.cursoid.notify.Notifications
import dev.cursoid.ui.detail.AgentDetailScreen
import dev.cursoid.ui.inbox.InboxScreen
import dev.cursoid.ui.newagent.NewAgentScreen
import dev.cursoid.ui.settings.SettingsScreen
import dev.cursoid.ui.signin.SignInScreen

private object Routes {
    const val INBOX = "inbox"
    const val NEW_AGENT = "new-agent"
    const val SETTINGS = "settings"
    const val AGENT = "agent/{agentId}"

    fun agent(id: String) = "agent/$id"
}

@Composable
fun CursoidApp(container: AppContainer) {
    val session by container.session.collectAsStateWithLifecycle()

    if (!session.isSignedIn) {
        SignInScreen(container)
        return
    }

    RequestNotificationAccess()

    val navController = rememberNavController()
    val pendingAgentId by container.pendingAgentId.collectAsStateWithLifecycle()

    LaunchedEffect(pendingAgentId) {
        pendingAgentId?.let { agentId ->
            container.pendingAgentId.value = null
            navController.navigate(Routes.agent(agentId))
        }
    }

    NavHost(navController = navController, startDestination = Routes.INBOX) {
        composable(Routes.INBOX) {
            InboxScreen(
                container = container,
                onOpenAgent = { navController.navigate(Routes.agent(it)) },
                onNewAgent = { navController.navigate(Routes.NEW_AGENT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.NEW_AGENT) {
            NewAgentScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onLaunched = { agentId ->
                    navController.popBackStack()
                    navController.navigate(Routes.agent(agentId))
                },
            )
        }

        composable(
            route = Routes.AGENT,
            arguments = listOf(navArgument("agentId") { type = NavType.StringType }),
        ) { entry ->
            val agentId = entry.arguments?.getString("agentId").orEmpty()
            AgentDetailScreen(
                container = container,
                agentId = agentId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}

/** Asks once on first launch; the toggle in Settings handles the rest. */
@Composable
private fun RequestNotificationAccess() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notifications.canPost(context)
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
