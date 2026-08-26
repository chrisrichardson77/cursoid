package dev.agentsforcursor.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.agentsforcursor.AppContainer
import dev.agentsforcursor.notify.Notifications
import dev.agentsforcursor.ui.appViewModel
import dev.agentsforcursor.ui.components.ErrorCard
import dev.agentsforcursor.ui.components.SectionLabel
import dev.agentsforcursor.ui.theme.MonoStyle
import dev.agentsforcursor.util.openUrl

private const val DOCS_URL = "https://cursor.com/docs/cloud-agent/api/endpoints"

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotifications(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (state.demo) "Demo session" else "Connected account")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = state.account?.userName
                                ?: state.account?.userEmail
                                ?: if (state.demo) "Demo data" else "Cursor account",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.account?.userEmail?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.account?.apiKeyName?.let {
                            Spacer(Modifier.size(6.dp))
                            Text(text = "Key: $it", style = MonoStyle)
                        }
                    }
                }
                state.error?.let { ErrorCard(message = it) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Notifications")
                SettingSwitch(
                    title = "Tell me when a turn finishes",
                    subtitle = "Checks your agents in the background about every 15 minutes.",
                    checked = state.notifications,
                    onCheckedChange = { enabled ->
                        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            enabled &&
                            !Notifications.canPost(context)
                        if (needsPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotifications(enabled)
                        }
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Session")
                SettingSwitch(
                    title = "Demo data",
                    subtitle = "Browse a fake inbox without calling the Cursor API.",
                    checked = state.demo,
                    onCheckedChange = viewModel::setDemo,
                )
                OutlinedButton(
                    onClick = viewModel::signOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(if (state.demo) "Leave demo mode" else "Sign out and forget key")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("About")
                Text(
                    text = "An unofficial Android client for Cursor cloud agents, built on the " +
                        "public Cloud Agents API. Cursor ships a native iOS app; Android only has " +
                        "the web PWA, so this fills the gap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Not supported by the API: reviewing file-level diffs and remote " +
                        "controlling agents on your own machine. Those live in the desktop and " +
                        "iOS clients only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { openUrl(context, DOCS_URL) }) {
                    Text("Cloud Agents API docs")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
