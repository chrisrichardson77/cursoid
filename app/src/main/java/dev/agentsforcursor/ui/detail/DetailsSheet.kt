package dev.agentsforcursor.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.agentsforcursor.model.Artifact
import dev.agentsforcursor.ui.components.SectionLabel
import dev.agentsforcursor.ui.theme.MonoStyle
import dev.agentsforcursor.util.TimeFormat
import dev.agentsforcursor.util.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(
    state: AgentDetailViewModel.State,
    onDismiss: () -> Unit,
    resolveArtifact: suspend (String) -> String?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Run details", style = MaterialTheme.typography.titleLarge)

            if (state.extrasLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Pushed branches")
                if (state.branches.isEmpty()) {
                    Text(
                        text = "Nothing pushed yet. Branches show up here once the agent commits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.branches.forEach { branch ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.CallSplit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = branch.branch ?: "(no branch yet)",
                                        style = MonoStyle,
                                        modifier = Modifier.weight(1f),
                                    )
                                    branch.branch?.let { name ->
                                        IconButton(
                                            onClick = { clipboard.setText(AnnotatedString(name)) },
                                        ) {
                                            Icon(
                                                Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy branch name",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = branch.repoUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                branch.prUrl?.let { prUrl ->
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(onClick = { openUrl(context, prUrl) }) {
                                        Icon(
                                            Icons.Outlined.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Review pull request")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Artifacts")
                if (state.artifacts.isEmpty()) {
                    Text(
                        text = "This agent hasn't saved screenshots, recordings, or logs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.artifacts.forEach { artifact ->
                        ArtifactCard(
                            artifact = artifact,
                            resolveArtifact = resolveArtifact,
                            onOpen = { url -> openUrl(context, url) },
                        )
                    }
                }
            }

            state.usage?.let { usage ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Token usage")
                    Text(
                        text = TimeFormat.compactTokens(usage.total.totalTokens) + " tokens across " +
                            "${usage.perRun.size} ${if (usage.perRun.size == 1) "run" else "runs"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    UsageRow("Input", usage.total.inputTokens)
                    UsageRow("Output", usage.total.outputTokens)
                    UsageRow("Cache write", usage.total.cacheWriteTokens)
                    UsageRow("Cache read", usage.total.cacheReadTokens)
                }
            }

            state.detail?.let { detail ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Agent")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = detail.summary.id,
                            style = MonoStyle,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(detail.summary.id))
                            },
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy agent ID",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = "Created ${TimeFormat.relative(detail.summary.createdAt)}" +
                            (detail.summary.envType?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(
    artifact: Artifact,
    resolveArtifact: suspend (String) -> String?,
    onOpen: (String) -> Unit,
) {
    val url by produceState<String?>(initialValue = null, artifact.path) {
        value = resolveArtifact(artifact.path)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (artifact.isImage && url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = artifact.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(artifact.fileName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = listOfNotNull(
                            artifact.sizeBytes?.let { "${it / 1024} KB" },
                            TimeFormat.relative(artifact.updatedAt),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                url?.let { resolved ->
                    IconButton(onClick = { onOpen(resolved) }) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "Open artifact",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRow(label: String, value: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = TimeFormat.compactTokens(value), style = MonoStyle)
    }
}
