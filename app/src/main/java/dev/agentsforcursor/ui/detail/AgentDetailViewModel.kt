package dev.agentsforcursor.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agentsforcursor.AppContainer
import dev.agentsforcursor.data.net.ApiException
import dev.agentsforcursor.model.AgentDetail
import dev.agentsforcursor.model.AgentStatus
import dev.agentsforcursor.model.AgentUsage
import dev.agentsforcursor.model.Artifact
import dev.agentsforcursor.model.GitBranch
import dev.agentsforcursor.model.Run
import dev.agentsforcursor.model.RunStatus
import dev.agentsforcursor.model.StreamEvent
import dev.agentsforcursor.model.TimelineItem
import dev.agentsforcursor.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgentDetailViewModel(
    private val container: AppContainer,
    private val agentId: String,
) : ViewModel() {

    data class LiveTurn(
        val runId: String,
        val assistant: String = "",
        val thinking: String = "",
        val tools: List<TimelineItem.ToolCall> = emptyList(),
    )

    data class State(
        val loading: Boolean = true,
        val detail: AgentDetail? = null,
        val runs: List<Run> = emptyList(),
        val prompts: Map<String, String> = emptyMap(),
        val live: LiveTurn? = null,
        val streaming: Boolean = false,
        val sending: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val artifacts: List<Artifact> = emptyList(),
        val usage: AgentUsage? = null,
        val extrasLoading: Boolean = false,
        val deleted: Boolean = false,
    ) {
        val activeRun: Run?
            get() = runs.firstOrNull { it.status.isActive }

        val isArchived: Boolean get() = detail?.summary?.status == AgentStatus.ARCHIVED

        val branches: List<GitBranch>
            get() = runs.flatMap { it.branches }.distinctBy { it.branch to it.repoUrl }

        val timeline: List<TimelineItem>
            get() = runs
                .sortedBy { it.createdAt }
                .flatMap { run -> itemsFor(run) }

        private fun itemsFor(run: Run): List<TimelineItem> = buildList {
            val prompt = run.prompt ?: prompts[run.id]
            if (!prompt.isNullOrBlank()) {
                add(TimelineItem.Prompt(run.id, prompt, run.createdAt))
            }

            val liveTurn = live?.takeIf { it.runId == run.id }
            if (liveTurn != null) {
                if (liveTurn.thinking.isNotBlank()) {
                    add(TimelineItem.Thinking(run.id, liveTurn.thinking))
                }
                addAll(liveTurn.tools)
                if (liveTurn.assistant.isNotBlank()) {
                    add(TimelineItem.Assistant(run.id, liveTurn.assistant))
                }
            } else if (!run.result.isNullOrBlank()) {
                add(TimelineItem.Assistant(run.id, run.result))
            }

            if (run.status.isTerminal) {
                add(TimelineItem.RunFooter(run.id, run.status, run.durationMs, run.updatedAt))
            }
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.prompts.collect { cached ->
                _state.update { it.copy(prompts = cached) }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching {
                val detail = container.repository.agent(agentId)
                val runs = container.repository.runs(agentId)
                detail to runs
            }
                .onSuccess { (detail, runs) ->
                    _state.update {
                        it.copy(loading = false, detail = detail, runs = runs, error = null)
                    }
                    runs.firstOrNull { run -> run.status.isActive }?.let { watch(it.id) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.userMessage()) }
                }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null, error = null) }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.sending) return

        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { container.repository.followUp(agentId, prompt, mode = null) }
                .onSuccess { run ->
                    container.settings.rememberPrompt(run.id, prompt)
                    _state.update {
                        it.copy(
                            sending = false,
                            runs = (listOf(run.copy(prompt = prompt)) + it.runs)
                                .distinctBy(Run::id),
                            detail = it.detail?.let { detail ->
                                detail.copy(
                                    summary = detail.summary.copy(status = AgentStatus.ACTIVE),
                                )
                            },
                        )
                    }
                    watch(run.id)
                }
                .onFailure { error ->
                    _state.update { it.copy(sending = false, error = error.userMessage()) }
                }
        }
    }

    fun cancel() {
        val run = _state.value.activeRun ?: return
        viewModelScope.launch {
            runCatching { container.repository.cancel(agentId, run.id) }
                .onSuccess {
                    streamJob?.cancel()
                    _state.update { it.copy(streaming = false, live = null) }
                    load()
                }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            runCatching { container.repository.setArchived(agentId, archived) }
                .onSuccess {
                    _state.update {
                        it.copy(notice = if (archived) "Agent archived." else "Agent restored.")
                    }
                    load()
                }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { container.repository.delete(agentId) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun loadExtras() {
        if (_state.value.extrasLoading) return
        _state.update { it.copy(extrasLoading = true) }
        viewModelScope.launch {
            val artifacts = runCatching { container.repository.artifacts(agentId) }
                .getOrDefault(emptyList())
            val usage = runCatching { container.repository.usage(agentId) }.getOrNull()
            _state.update {
                it.copy(extrasLoading = false, artifacts = artifacts, usage = usage)
            }
        }
    }

    suspend fun artifactUrl(path: String): String? =
        runCatching { container.repository.artifactUrl(agentId, path) }.getOrNull()

    private fun watch(runId: String) {
        streamJob?.cancel()
        pollJob?.cancel()
        _state.update { it.copy(streaming = true, live = LiveTurn(runId = runId)) }

        streamJob = viewModelScope.launch {
            var lastEventId: String? = null
            var attempt = 0
            while (true) {
                try {
                    container.repository.stream(agentId, runId, lastEventId).collect { event ->
                        event.eventId?.let { lastEventId = it }
                        apply(runId, event)
                    }
                    finishStream(runId)
                    return@launch
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    // A 410 means the retention window closed; the run's terminal state is
                    // authoritative from that point on.
                    if (error is ApiException && error.isStreamExpired) {
                        finishStream(runId)
                        return@launch
                    }
                    attempt++
                    if (attempt > MAX_STREAM_ATTEMPTS) {
                        _state.update { it.copy(streaming = false, error = error.userMessage()) }
                        poll(runId)
                        return@launch
                    }
                    delay(RECONNECT_DELAY_MS * attempt)
                }
            }
        }
    }

    private fun apply(runId: String, event: StreamEvent) {
        when (event) {
            is StreamEvent.AssistantDelta -> _state.update { state ->
                state.copy(
                    live = (state.live ?: LiveTurn(runId)).let {
                        it.copy(assistant = it.assistant + event.text)
                    },
                )
            }

            is StreamEvent.ThinkingDelta -> _state.update { state ->
                state.copy(
                    live = (state.live ?: LiveTurn(runId)).let {
                        it.copy(thinking = it.thinking + event.text)
                    },
                )
            }

            is StreamEvent.ToolCall -> _state.update { state ->
                val live = state.live ?: LiveTurn(runId)
                val item = TimelineItem.ToolCall(
                    runId = runId,
                    callId = event.callId,
                    name = event.name,
                    running = event.running,
                    detail = event.detail,
                )
                val tools = live.tools.filterNot { it.callId == event.callId } + item
                state.copy(live = live.copy(tools = tools))
            }

            is StreamEvent.Result -> _state.update { state ->
                state.copy(
                    runs = state.runs.map { run ->
                        if (run.id == runId) {
                            run.copy(
                                status = event.status,
                                result = event.text ?: run.result,
                                durationMs = event.durationMs ?: run.durationMs,
                                branches = event.branches.ifEmpty { run.branches },
                            )
                        } else {
                            run
                        }
                    },
                    live = null,
                    streaming = false,
                )
            }

            is StreamEvent.Failed -> _state.update {
                it.copy(error = event.message ?: "The stream reported an error.")
            }

            is StreamEvent.Status -> _state.update { state ->
                state.copy(
                    runs = state.runs.map { run ->
                        if (run.id == runId) run.copy(status = event.status) else run
                    },
                )
            }

            is StreamEvent.Done -> Unit
        }
    }

    private fun finishStream(runId: String) {
        _state.update { it.copy(streaming = false, live = null) }
        viewModelScope.launch {
            runCatching { container.repository.run(agentId, runId) }.onSuccess { fresh ->
                mergeRun(fresh)
            }
            runCatching { container.repository.agent(agentId) }.onSuccess { detail ->
                _state.update { it.copy(detail = detail) }
            }
        }
    }

    /** Fallback for when the SSE stream is unavailable: poll until the run reaches a terminal state. */
    private fun poll(runId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val run = runCatching { container.repository.run(agentId, runId) }.getOrNull()
                    ?: continue
                mergeRun(run)
                if (run.status.isTerminal) {
                    runCatching { container.repository.agent(agentId) }.onSuccess { detail ->
                        _state.update { it.copy(detail = detail) }
                    }
                    break
                }
            }
        }
    }

    private fun mergeRun(fresh: Run) {
        _state.update { state ->
            val known = state.runs.any { it.id == fresh.id }
            val runs = if (known) {
                state.runs.map { run ->
                    if (run.id == fresh.id) {
                        fresh.copy(prompt = run.prompt ?: fresh.prompt)
                    } else {
                        run
                    }
                }
            } else {
                listOf(fresh) + state.runs
            }
            state.copy(runs = runs)
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val RECONNECT_DELAY_MS = 1_500L
        const val MAX_STREAM_ATTEMPTS = 3
    }
}
