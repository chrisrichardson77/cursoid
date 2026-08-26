package dev.agentsforcursor.ui.newagent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agentsforcursor.AppContainer
import dev.agentsforcursor.model.AgentMode
import dev.agentsforcursor.model.CreateAgentRequest
import dev.agentsforcursor.model.ModelChoice
import dev.agentsforcursor.model.toChoices
import dev.agentsforcursor.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewAgentViewModel(private val container: AppContainer) : ViewModel() {

    data class Launched(val agentId: String, val runId: String, val agentName: String)

    data class State(
        val prompt: String = "",
        val repos: List<String> = emptyList(),
        val reposLoading: Boolean = true,
        val reposError: String? = null,
        val repoUrl: String? = null,
        val startingRef: String = "",
        val models: List<ModelChoice> = emptyList(),
        val selectedModel: ModelChoice? = null,
        val mode: AgentMode = AgentMode.AGENT,
        val autoCreatePR: Boolean = true,
        val workOnCurrentBranch: Boolean = false,
        val launching: Boolean = false,
        val error: String? = null,
        val launched: Launched? = null,
    ) {
        val canLaunch: Boolean get() = prompt.isNotBlank() && !launching
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        loadRepos()
        loadModels()
    }

    fun loadRepos() {
        _state.update { it.copy(reposLoading = true, reposError = null) }
        viewModelScope.launch {
            runCatching { container.repository.repositories() }
                .onSuccess { repos ->
                    _state.update {
                        it.copy(
                            repos = repos,
                            reposLoading = false,
                            repoUrl = it.repoUrl ?: repos.firstOrNull(),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(reposLoading = false, reposError = error.userMessage())
                    }
                }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            runCatching { container.repository.models().toChoices() }
                .onSuccess { choices ->
                    _state.update {
                        it.copy(
                            models = choices,
                            selectedModel = it.selectedModel
                                ?: choices.firstOrNull { choice -> choice.isDefault },
                        )
                    }
                }
        }
    }

    fun setPrompt(value: String) = _state.update { it.copy(prompt = value, error = null) }

    fun setRepo(url: String?) = _state.update { it.copy(repoUrl = url) }

    fun setStartingRef(value: String) = _state.update { it.copy(startingRef = value) }

    fun setModel(choice: ModelChoice?) = _state.update { it.copy(selectedModel = choice) }

    fun setMode(mode: AgentMode) = _state.update { it.copy(mode = mode) }

    fun setAutoCreatePR(value: Boolean) = _state.update { it.copy(autoCreatePR = value) }

    fun setWorkOnCurrentBranch(value: Boolean) =
        _state.update { it.copy(workOnCurrentBranch = value) }

    fun launch() {
        val current = _state.value
        if (!current.canLaunch) return

        _state.update { it.copy(launching = true, error = null) }
        viewModelScope.launch {
            val request = CreateAgentRequest(
                prompt = current.prompt.trim(),
                repoUrl = current.repoUrl,
                startingRef = current.startingRef.trim().ifBlank { null },
                modelId = current.selectedModel?.modelId,
                modelParams = current.selectedModel?.params.orEmpty(),
                mode = current.mode,
                autoCreatePR = current.autoCreatePR,
                workOnCurrentBranch = current.workOnCurrentBranch,
            )
            runCatching { container.repository.createAgent(request) }
                .onSuccess { (detail, run) ->
                    container.settings.rememberPrompt(run.id, request.prompt)
                    _state.update {
                        it.copy(
                            launching = false,
                            launched = Launched(
                                agentId = detail.summary.id,
                                runId = run.id,
                                agentName = detail.summary.name,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(launching = false, error = error.userMessage()) }
                }
        }
    }
}
