package dev.agentsforcursor.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.agentsforcursor.util.rememberClipboardCopy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.agentsforcursor.AppContainer
import dev.agentsforcursor.model.TimelineItem
import dev.agentsforcursor.notify.RunTrackerService
import dev.agentsforcursor.ui.appViewModel
import dev.agentsforcursor.ui.components.ErrorCard
import dev.agentsforcursor.ui.components.RunStatusPill
import dev.agentsforcursor.ui.components.SkeletonList
import dev.agentsforcursor.ui.components.rememberVoiceInput
import dev.agentsforcursor.ui.theme.MonoStyle
import dev.agentsforcursor.util.TimeFormat
import dev.agentsforcursor.util.openUrl

@Composable
fun AgentDetailScreen(
    container: AppContainer,
    agentId: String,
    onBack: () -> Unit,
) {
    val viewModel = appViewModel(key = agentId) { AgentDetailViewModel(container, agentId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    AgentDetailContent(
        state = state,
        agentId = agentId,
        onBack = onBack,
        onSend = viewModel::send,
        onCancel = viewModel::cancel,
        onRetry = viewModel::load,
        onDismissError = viewModel::dismissNotice,
        onLoadExtras = viewModel::loadExtras,
        onSetArchived = viewModel::setArchived,
        onDelete = viewModel::delete,
        resolveArtifact = viewModel::artifactUrl,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailContent(
    state: AgentDetailViewModel.State,
    agentId: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onLoadExtras: () -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
    resolveArtifact: suspend (String) -> String?,
) {
    val context = LocalContext.current
    val copyToClipboard = rememberClipboardCopy()

    var menuOpen by remember { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()
    val timeline = state.timeline

    LaunchedEffect(timeline.size, state.live?.assistant?.length) {
        if (timeline.isNotEmpty()) {
            listState.animateScrollToItem(timeline.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.summary?.name ?: "Agent",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showDetails = true
                            onLoadExtras()
                        },
                    ) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = "Run details")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        state.detail?.summary?.url?.let { url ->
                            DropdownMenuItem(
                                text = { Text("Open in Cursor") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                                onClick = {
                                    menuOpen = false
                                    openUrl(context, url)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Copy agent ID") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = {
                                menuOpen = false
                                copyToClipboard(agentId)
                            },
                        )
                        state.activeRun?.let { run ->
                            DropdownMenuItem(
                                text = { Text("Notify me when done") },
                                leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                                onClick = {
                                    menuOpen = false
                                    RunTrackerService.start(
                                        context = context,
                                        agentId = agentId,
                                        runId = run.id,
                                        agentName = state.detail?.summary?.name ?: "Cloud agent",
                                    )
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (state.isArchived) "Restore" else "Archive") },
                            leadingIcon = { Icon(Icons.Outlined.Unarchive, null) },
                            onClick = {
                                menuOpen = false
                                onSetArchived(!state.isArchived)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete permanently") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                sending = state.sending,
                streaming = state.streaming || state.activeRun != null,
                archived = state.isArchived,
                onSend = {
                    onSend(draft)
                    draft = ""
                },
                onCancel = onCancel,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> SkeletonList()

                state.detail == null && state.error != null -> Column(Modifier.padding(16.dp)) {
                    ErrorCard(message = state.error, onRetry = onRetry)
                }

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item("header") {
                        AgentHeader(
                            state = state,
                            onOpenPullRequest = { openUrl(context, it) },
                        )
                    }

                    if (state.error != null) {
                        item("error") {
                            ErrorCard(message = state.error, onRetry = onDismissError)
                        }
                    }

                    items(
                        count = timeline.size,
                        key = { index -> timeline[index].key },
                    ) { index ->
                        TimelineRow(item = timeline[index])
                    }

                    if (state.streaming && state.live?.assistant.isNullOrEmpty()) {
                        item("thinking-indicator") { WorkingIndicator() }
                    }
                }
            }
        }
    }

    if (showDetails) {
        DetailsSheet(
            state = state,
            onDismiss = { showDetails = false },
            resolveArtifact = resolveArtifact,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this agent?") },
            text = {
                Text(
                    "This removes the agent and its conversation from Cursor for good. Branches " +
                        "and pull requests it already pushed are not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun AgentHeader(
    state: AgentDetailViewModel.State,
    onOpenPullRequest: (String) -> Unit,
) {
    val detail = state.detail ?: return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.runs.firstOrNull()?.let { RunStatusPill(it.status) }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = TimeFormat.relative(detail.summary.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            detail.repos.forEach { repo ->
                Text(
                    text = buildString {
                        append(repo.shortName)
                        repo.startingRef?.let { append(" · ").append(it) }
                    },
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val branch = state.branches.firstOrNull()
            when {
                branch != null -> {
                    branch.branch?.let { name ->
                        Text(
                            text = name,
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    branch.prUrl?.let { prUrl ->
                        OutlinedButton(onClick = { onOpenPullRequest(prUrl) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Review pull request")
                        }
                    }
                }

                detail.autoCreatePR -> Text(
                    text = "Opens a pull request when it finishes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
    when (item) {
        is TimelineItem.Prompt -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(0.88f),
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        is TimelineItem.Assistant -> SelectionContainer {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is TimelineItem.Thinking -> Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is TimelineItem.ToolCall -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.detail?.let {
                    Text(
                        text = it,
                        style = MonoStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        is TimelineItem.RunFooter -> Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RunStatusPill(item.status)
            Spacer(Modifier.width(10.dp))
            val duration = TimeFormat.duration(item.durationMs)
            Text(
                text = listOfNotNull(duration, TimeFormat.relative(item.at))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            HorizontalDivider(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WorkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Working…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    streaming: Boolean,
    archived: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val voice = rememberVoiceInput("Describe the follow-up") { spoken ->
        onDraftChange(if (draft.isBlank()) spoken else "$draft $spoken")
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            AnimatedVisibility(visible = streaming) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "This agent is mid-turn. Follow-ups queue until it finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }

            if (archived) {
                Text(
                    text = "Archived agents can't take new turns. Restore it to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    TextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        placeholder = { Text("Send a follow-up") },
                        maxLines = 5,
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 160.dp),
                    )
                    if (voice.available && draft.isBlank()) {
                        IconButton(onClick = voice.start) {
                            Icon(Icons.Outlined.Mic, contentDescription = "Dictate")
                        }
                    }
                    IconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() && !sending,
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}
