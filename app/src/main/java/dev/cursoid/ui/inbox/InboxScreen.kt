package dev.cursoid.ui.inbox

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cursoid.AppContainer
import dev.cursoid.model.AgentSummary
import dev.cursoid.ui.appViewModel
import dev.cursoid.ui.components.AgentStatusPill
import dev.cursoid.ui.components.EmptyState
import dev.cursoid.ui.components.ErrorCard
import dev.cursoid.ui.components.SkeletonList
import dev.cursoid.ui.theme.MonoStyle
import dev.cursoid.util.TimeFormat

@Composable
fun InboxScreen(
    container: AppContainer,
    onOpenAgent: (String) -> Unit,
    onNewAgent: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = appViewModel { InboxViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    InboxContent(
        state = state,
        onFilterChange = viewModel::setFilter,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpenAgent = onOpenAgent,
        onNewAgent = onNewAgent,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxContent(
    state: InboxViewModel.State,
    onFilterChange: (InboxViewModel.Filter) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenAgent: (String) -> Unit,
    onNewAgent: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agents")
                        val subtitle = when {
                            state.demo -> "Demo data"
                            state.workingCount == 1 -> "1 agent working"
                            state.workingCount > 1 -> "${state.workingCount} agents working"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewAgent,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New agent") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InboxViewModel.Filter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> SkeletonList()

                    state.error != null && state.agents.isEmpty() -> Column(
                        Modifier.padding(16.dp),
                    ) {
                        ErrorCard(message = state.error, onRetry = onRefresh)
                    }

                    state.visibleAgents.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = when (state.filter) {
                            InboxViewModel.Filter.ARCHIVED -> "Nothing archived"
                            InboxViewModel.Filter.WORKING -> "No agents working"
                            InboxViewModel.Filter.YOUR_TURN -> "Nothing waiting on you"
                            InboxViewModel.Filter.ALL -> "No agents yet"
                        },
                        body = when (state.filter) {
                            InboxViewModel.Filter.ALL ->
                                "Start one with a repo and a task, then close the app — it keeps " +
                                    "working and notifies you when the turn is done."

                            else -> "Switch to All to see the rest of your agents."
                        },
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.error != null) {
                            item {
                                ErrorCard(message = state.error, onRetry = onRefresh)
                            }
                        }
                        items(state.visibleAgents, key = AgentSummary::id) { agent ->
                            AgentRow(agent = agent, onClick = { onOpenAgent(agent.id) })
                        }
                        if (state.nextCursor != null) {
                            item {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    enabled = !state.loadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (state.loadingMore) "Loading…" else "Load older agents")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentRow(agent: AgentSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = agent.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgentStatusPill(agent.status)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = TimeFormat.relative(agent.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (agent.envType != null && agent.envType != "cloud") {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = agent.envType,
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
