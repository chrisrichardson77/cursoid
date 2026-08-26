package dev.cursoid.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cursoid.AppContainer
import dev.cursoid.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SignInViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val apiKey: String = "",
        val checking: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value, error = null) }
    }

    fun submit() {
        val key = _state.value.apiKey.trim()
        if (key.isEmpty() || _state.value.checking) return

        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            container.settings.setApiKey(key)
            // The API client reads the key from the shared session, so wait for it to land.
            withTimeoutOrNull(2_000) { container.session.first { it.apiKey == key } }

            val result = runCatching { container.repository.account() }
            if (result.isFailure) {
                container.settings.setApiKey(null)
                _state.update {
                    it.copy(checking = false, error = result.exceptionOrNull()?.userMessage())
                }
            }
            // On success the session flow flips `isSignedIn` and the app swaps in the inbox.
        }
    }

    fun useDemoData() {
        viewModelScope.launch {
            container.settings.setApiKey(null)
            container.settings.setDemoMode(true)
        }
    }
}
