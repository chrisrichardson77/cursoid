package dev.cursoid.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cursoid.AppContainer
import dev.cursoid.model.AgentStatus
import dev.cursoid.model.AgentSummary
import dev.cursoid.util.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InboxViewModel(private val container: AppContainer) : ViewModel() {

    enum class Filter(val label: String) {
        ALL("All"),
        WORKING("Working"),
        YOUR_TURN("Your turn"),
        ARCHIVED("Archived"),
    }

    data class State(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val agents: List<AgentSummary> = emptyList(),
        val filter: Filter = Filter.ALL,
        val nextCursor: String? = null,
        val error: String? = null,
        val demo: Boolean = false,
    ) {
        val visibleAgents: List<AgentSummary>
            get() = agents.filter { agent ->
                when (filter) {
                    Filter.ALL -> agent.status != AgentStatus.ARCHIVED
                    Filter.WORKING -> agent.status == AgentStatus.ACTIVE
                    Filter.YOUR_TURN -> agent.status == AgentStatus.IDLE
                    Filter.ARCHIVED -> agent.status == AgentStatus.ARCHIVED
                }
            }

        val workingCount: Int get() = agents.count { it.status == AgentStatus.ACTIVE }
    }

    private val _state = MutableStateFlow(State(demo = container.isDemo))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (_state.value.workingCount > 0 && !_state.value.refreshing) {
                    load(initial = false, silent = true)
                }
            }
        }
    }

    fun setFilter(filter: Filter) {
        _state.update { it.copy(filter = filter) }
    }

    fun refresh() = load(initial = false)

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { container.repository.agents(cursor = cursor, includeArchived = true) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            agents = (it.agents + page.items).distinctBy(AgentSummary::id),
                            nextCursor = page.nextCursor,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loadingMore = false, error = error.userMessage()) }
                }
        }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        _state.update {
            it.copy(
                loading = initial,
                refreshing = !initial && !silent,
                error = if (silent) it.error else null,
                demo = container.isDemo,
            )
        }
        viewModelScope.launch {
            runCatching { container.repository.agents(includeArchived = true) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            agents = page.items,
                            nextCursor = page.nextCursor,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = error.userMessage(),
                        )
                    }
                }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 15_000L
    }
}
