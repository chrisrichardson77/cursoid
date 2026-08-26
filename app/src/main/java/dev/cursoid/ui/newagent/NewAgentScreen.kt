package dev.cursoid.ui.newagent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cursoid.AppContainer
import dev.cursoid.model.AgentMode
import dev.cursoid.model.ModelChoice
import dev.cursoid.ui.appViewModel
import dev.cursoid.ui.components.ErrorCard
import dev.cursoid.ui.components.SectionLabel
import dev.cursoid.ui.components.rememberVoiceInput
import dev.cursoid.ui.theme.MonoStyle

@Composable
fun NewAgentScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLaunched: (String) -> Unit,
) {
    val viewModel = appViewModel { NewAgentViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.launched) {
        state.launched?.let { onLaunched(it.agentId) }
    }

    NewAgentContent(
        state = state,
        onBack = onBack,
        onPromptChange = viewModel::setPrompt,
        onRepoChange = viewModel::setRepo,
        onReloadRepos = viewModel::loadRepos,
        onStartingRefChange = viewModel::setStartingRef,
        onModelChange = viewModel::setModel,
        onModeChange = viewModel::setMode,
        onAutoCreatePRChange = viewModel::setAutoCreatePR,
        onWorkOnCurrentBranchChange = viewModel::setWorkOnCurrentBranch,
        onLaunch = viewModel::launch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAgentContent(
    state: NewAgentViewModel.State,
    onBack: () -> Unit,
    onPromptChange: (String) -> Unit,
    onRepoChange: (String?) -> Unit,
    onReloadRepos: () -> Unit,
    onStartingRefChange: (String) -> Unit,
    onModelChange: (ModelChoice?) -> Unit,
    onModeChange: (AgentMode) -> Unit,
    onAutoCreatePRChange: (Boolean) -> Unit,
    onWorkOnCurrentBranchChange: (Boolean) -> Unit,
    onLaunch: () -> Unit,
) {
    var repoSheet by remember { mutableStateOf(false) }
    var modelSheet by remember { mutableStateOf(false) }

    val voice = rememberVoiceInput("Describe the task") { spoken ->
        onPromptChange(if (state.prompt.isBlank()) spoken else "${state.prompt} $spoken")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New agent") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = onLaunch,
                        enabled = state.canLaunch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (state.launching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Launching…")
                        } else {
                            Text("Launch agent")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Task")
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChange,
                    placeholder = {
                        Text("What should the agent do? Include the acceptance criteria.")
                    },
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = if (voice.available) {
                        {
                            IconButton(onClick = voice.start) {
                                Icon(Icons.Outlined.Mic, contentDescription = "Dictate")
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Repository")
                PickerRow(
                    label = state.repoUrl?.removePrefix("https://") ?: "Choose a repository",
                    onClick = { repoSheet = true },
                )
                if (state.reposError != null) {
                    Text(
                        text = "Couldn't load your repositories: ${state.reposError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.startingRef,
                    onValueChange = onStartingRefChange,
                    label = { Text("Starting branch") },
                    placeholder = { Text("Defaults to the repo's default branch") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Model")
                PickerRow(
                    label = state.selectedModel?.label ?: "Account default",
                    onClick = { modelSheet = true },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Mode")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AgentMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AgentMode.entries.size,
                            ),
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text(
                    text = if (state.mode == AgentMode.PLAN) {
                        "Plan explores the codebase and drafts an approach before writing code."
                    } else {
                        "Agent implements the change directly and pushes a branch."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("When it finishes")
                ToggleRow(
                    title = "Open a pull request",
                    subtitle = "Cursor opens the PR as soon as the first turn completes.",
                    checked = state.autoCreatePR,
                    onCheckedChange = onAutoCreatePRChange,
                )
                ToggleRow(
                    title = "Commit to the starting branch",
                    subtitle = "Off by default, which pushes to a fresh cursor/… branch instead.",
                    checked = state.workOnCurrentBranch,
                    onCheckedChange = onWorkOnCurrentBranchChange,
                )
            }

            state.error?.let { ErrorCard(message = it) }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (repoSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { repoSheet = false }, sheetState = sheetState) {
            RepoPicker(
                repos = state.repos,
                loading = state.reposLoading,
                error = state.reposError,
                selected = state.repoUrl,
                onRetry = onReloadRepos,
                onSelect = { url ->
                    onRepoChange(url)
                    repoSheet = false
                },
            )
        }
    }

    if (modelSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { modelSheet = false }, sheetState = sheetState) {
            ModelPicker(
                choices = state.models,
                selected = state.selectedModel,
                onSelect = { choice ->
                    onModelChange(choice)
                    modelSheet = false
                },
            )
        }
    }
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
    }
}

@Composable
private fun ToggleRow(
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

@Composable
private fun RepoPicker(
    repos: List<String>,
    loading: Boolean,
    error: String?,
    selected: String?,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var manual by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Repository", style = MaterialTheme.typography.titleLarge)

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Loading repositories…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (error != null) {
            ErrorCard(message = error, onRetry = onRetry)
        }

        if (repos.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Filter") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repos
                    .filter { it.contains(query, ignoreCase = true) }
                    .forEach { url ->
                        Surface(
                            onClick = { onSelect(url) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (url == selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = url.removePrefix("https://"),
                                style = MonoStyle,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
            }
        }

        Text(
            text = "Or paste a repository URL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                placeholder = { Text("https://github.com/org/repo") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { onSelect(manual.trim()) },
                enabled = manual.isNotBlank(),
            ) { Text("Use") }
        }

        if (!loading && repos.isEmpty() && error == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "No repositories came back from Cursor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Reload repositories")
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(
    choices: List<ModelChoice>,
    selected: ModelChoice?,
    onSelect: (ModelChoice?) -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Model", style = MaterialTheme.typography.titleLarge)

        ModelOption(
            label = "Account default",
            description = "Uses whatever your Cursor account or team has configured.",
            selected = selected == null,
            onClick = { onSelect(null) },
        )

        choices.forEach { choice ->
            ModelOption(
                label = choice.label,
                description = choice.description,
                selected = selected?.key == choice.key,
                onClick = { onSelect(choice) },
            )
        }
    }
}

@Composable
private fun ModelOption(
    label: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
