package dev.cursoid.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cursoid-settings")

data class Session(
    val apiKey: String? = null,
    val demoMode: Boolean = false,
) {
    val isSignedIn: Boolean get() = demoMode || !apiKey.isNullOrBlank()
}

class SettingsStore(context: Context) {
    private val store = context.dataStore

    private val json = Json { ignoreUnknownKeys = true }

    val session: Flow<Session> = store.data.map { prefs ->
        Session(
            apiKey = prefs[KEY_API_KEY]?.let { Secrets.decrypt(it) },
            demoMode = prefs[KEY_DEMO_MODE] ?: false,
        )
    }

    val notificationsEnabled: Flow<Boolean> = store.data.map { it[KEY_NOTIFICATIONS] ?: true }

    /**
     * Runs only expose their prompt to the client that sent them, so prompts sent from this device
     * are cached locally to render both sides of the conversation.
     */
    val prompts: Flow<Map<String, String>> = store.data.map { prefs ->
        prefs[KEY_PROMPTS]?.let { decodeMap(it) } ?: emptyMap()
    }

    suspend fun setApiKey(apiKey: String?) {
        store.edit { prefs ->
            if (apiKey.isNullOrBlank()) {
                prefs.remove(KEY_API_KEY)
            } else {
                prefs[KEY_API_KEY] = Secrets.encrypt(apiKey.trim())
            }
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        store.edit { it[KEY_DEMO_MODE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun signOut() {
        store.edit { prefs ->
            prefs.remove(KEY_API_KEY)
            prefs.remove(KEY_DEMO_MODE)
            prefs.remove(KEY_PROMPTS)
            prefs.remove(KEY_RUN_SNAPSHOT)
        }
    }

    suspend fun rememberPrompt(runId: String, prompt: String) {
        store.edit { prefs ->
            val current = prefs[KEY_PROMPTS]?.let { decodeMap(it) } ?: emptyMap()
            val trimmed = (current + (runId to prompt)).entries
                .sortedByDescending { it.key }
                .take(MAX_CACHED_PROMPTS)
                .associate { it.key to it.value }
            prefs[KEY_PROMPTS] = json.encodeToString(trimmed)
        }
    }

    suspend fun runSnapshot(): Map<String, String> =
        store.data.first()[KEY_RUN_SNAPSHOT]?.let { decodeMap(it) } ?: emptyMap()

    suspend fun setRunSnapshot(snapshot: Map<String, String>) {
        store.edit { it[KEY_RUN_SNAPSHOT] = json.encodeToString(snapshot) }
    }

    private fun decodeMap(raw: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    private companion object {
        const val MAX_CACHED_PROMPTS = 200

        val KEY_API_KEY = stringPreferencesKey("api_key_cipher")
        val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_PROMPTS = stringPreferencesKey("prompt_cache")
        val KEY_RUN_SNAPSHOT = stringPreferencesKey("run_snapshot")
    }
}
