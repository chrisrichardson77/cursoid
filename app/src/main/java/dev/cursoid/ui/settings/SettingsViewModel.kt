package dev.cursoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cursoid.AppContainer
import dev.cursoid.model.Account
import dev.cursoid.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val account: Account? = null,
        val demo: Boolean = false,
        val notifications: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State(demo = container.isDemo))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(container.session, container.settings.notificationsEnabled) { session, notify ->
                session.demoMode to notify
            }.collect { (demo, notify) ->
                _state.update { it.copy(demo = demo, notifications = notify) }
            }
        }
        loadAccount()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            runCatching { container.repository.account() }
                .onSuccess { account -> _state.update { it.copy(account = account, error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun setDemo(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setDemoMode(enabled)
            loadAccount()
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { container.settings.setNotificationsEnabled(enabled) }
    }

    fun signOut() {
        viewModelScope.launch { container.settings.signOut() }
    }
}
