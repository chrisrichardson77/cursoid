package dev.cursoid

import android.content.Context
import dev.cursoid.data.AgentsRepository
import dev.cursoid.data.DemoAgentsRepository
import dev.cursoid.data.RemoteAgentsRepository
import dev.cursoid.data.net.CursorApi
import dev.cursoid.data.store.Session
import dev.cursoid.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency graph. The app is small enough that a container beats a DI framework. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    val settings = SettingsStore(appContext)

    private val sessionState = MutableStateFlow(Session())

    val session: StateFlow<Session> = sessionState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val api = CursorApi(
        baseClient = httpClient,
        json = json,
        tokenProvider = { sessionState.value.apiKey },
    )

    private val remoteRepository: AgentsRepository = RemoteAgentsRepository(api)

    private val demoRepository: AgentsRepository = DemoAgentsRepository(
        artifactUri = { "android.resource://${appContext.packageName}/drawable/demo_artifact" },
    )

    /** Resolved per call so flipping demo mode takes effect immediately. */
    val repository: AgentsRepository
        get() = if (sessionState.value.demoMode) demoRepository else remoteRepository

    val isDemo: Boolean get() = sessionState.value.demoMode

    /** Set when the user taps a notification, consumed by the navigation host. */
    val pendingAgentId = MutableStateFlow<String?>(null)

    init {
        applicationScope.launch {
            settings.session.collect { sessionState.value = it }
        }
    }
}
