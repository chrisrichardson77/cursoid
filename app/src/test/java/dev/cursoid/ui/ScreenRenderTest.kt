package dev.cursoid.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.cursoid.model.Account
import dev.cursoid.model.AgentDetail
import dev.cursoid.model.AgentMode
import dev.cursoid.model.AgentStatus
import dev.cursoid.model.AgentSummary
import dev.cursoid.model.Artifact
import dev.cursoid.model.GitBranch
import dev.cursoid.model.ModelChoice
import dev.cursoid.model.RepoRef
import dev.cursoid.model.Run
import dev.cursoid.model.RunStatus
import dev.cursoid.model.TimelineItem
import dev.cursoid.model.TokenUsage
import dev.cursoid.model.AgentUsage
import dev.cursoid.ui.detail.AgentDetailContent
import dev.cursoid.ui.detail.AgentDetailViewModel
import dev.cursoid.ui.inbox.InboxContent
import dev.cursoid.ui.inbox.InboxViewModel
import dev.cursoid.ui.newagent.NewAgentContent
import dev.cursoid.ui.newagent.NewAgentViewModel
import dev.cursoid.ui.settings.SettingsContent
import dev.cursoid.ui.settings.SettingsViewModel
import dev.cursoid.ui.signin.SignInContent
import dev.cursoid.ui.signin.SignInViewModel
import dev.cursoid.ui.theme.CursoidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Renders every screen on the JVM and writes a PNG per state. These double as a smoke test: a
 * composable that throws during layout fails the build.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val now: Instant = Instant.now()

    @Test
    fun signIn() = capture("01-sign-in") {
        SignInContent(
            state = SignInViewModel.State(),
            onApiKeyChange = {},
            onSubmit = {},
            onUseDemoData = {},
        )
    }

    @Test
    fun signInRejectedKey() = capture("02-sign-in-error") {
        SignInContent(
            state = SignInViewModel.State(
                apiKey = "key_9f2c41a8d7",
                error = "That API key was rejected. Check it in Cursor Dashboard → API Keys.",
            ),
            onApiKeyChange = {},
            onSubmit = {},
            onUseDemoData = {},
        )
    }

    @Test
    fun inbox() = capture("03-inbox") {
        InboxContent(
            state = InboxViewModel.State(loading = false, agents = agents()),
            onFilterChange = {},
            onRefresh = {},
            onLoadMore = {},
            onOpenAgent = {},
            onNewAgent = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun inboxLoading() = capture("04-inbox-loading") {
        InboxContent(
            state = InboxViewModel.State(loading = true),
            onFilterChange = {},
            onRefresh = {},
            onLoadMore = {},
            onOpenAgent = {},
            onNewAgent = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun inboxEmpty() = capture("05-inbox-empty") {
        InboxContent(
            state = InboxViewModel.State(loading = false, agents = emptyList()),
            onFilterChange = {},
            onRefresh = {},
            onLoadMore = {},
            onOpenAgent = {},
            onNewAgent = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun inboxOffline() = capture("06-inbox-offline") {
        InboxContent(
            state = InboxViewModel.State(
                loading = false,
                agents = emptyList(),
                error = "No connection to the Cursor API. Check your network and try again.",
            ),
            onFilterChange = {},
            onRefresh = {},
            onLoadMore = {},
            onOpenAgent = {},
            onNewAgent = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun agentMidTurn() = capture("07-agent-streaming") {
        AgentDetailContent(
            state = streamingState(),
            agentId = "bc-1",
            onBack = {},
            onSend = {},
            onCancel = {},
            onRetry = {},
            onDismissError = {},
            onLoadExtras = {},
            onSetArchived = {},
            onDelete = {},
            resolveArtifact = { null },
        )
    }

    @Test
    fun agentFinished() = capture("08-agent-finished") {
        AgentDetailContent(
            state = finishedState(),
            agentId = "bc-2",
            onBack = {},
            onSend = {},
            onCancel = {},
            onRetry = {},
            onDismissError = {},
            onLoadExtras = {},
            onSetArchived = {},
            onDelete = {},
            resolveArtifact = { null },
        )
    }

    @Test
    fun newAgent() = capture("09-new-agent") {
        NewAgentContent(
            state = NewAgentViewModel.State(
                prompt = "The checkout tests fail about one run in five on CI. Find the race and fix it.",
                repos = listOf("https://github.com/acme/storefront"),
                reposLoading = false,
                repoUrl = "https://github.com/acme/storefront",
                startingRef = "main",
                models = listOf(composerChoice()),
                selectedModel = composerChoice(),
                mode = AgentMode.AGENT,
            ),
            onBack = {},
            onPromptChange = {},
            onRepoChange = {},
            onReloadRepos = {},
            onStartingRefChange = {},
            onModelChange = {},
            onModeChange = {},
            onAutoCreatePRChange = {},
            onWorkOnCurrentBranchChange = {},
            onLaunch = {},
        )
    }

    @Test
    fun settings() = capture("10-settings") {
        SettingsContent(
            state = SettingsViewModel.State(
                account = Account(
                    apiKeyName = "Pixel",
                    userEmail = "you@example.com",
                    userName = "Chris Richardson",
                ),
                demo = false,
                notifications = true,
            ),
            onBack = {},
            onNotificationsChange = {},
            onDemoChange = {},
            onSignOut = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            CursoidTheme(darkTheme = true) { content() }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun agents(): List<AgentSummary> = listOf(
        summary("bc-1", "Fix flaky checkout tests", AgentStatus.ACTIVE, 4),
        summary("bc-2", "Add dark mode to the settings screen", AgentStatus.IDLE, 52),
        summary("bc-3", "Investigate p95 latency regression", AgentStatus.IDLE, 185),
        summary("bc-4", "Migrate analytics to typed events", AgentStatus.IDLE, 1_500),
        summary("bc-5", "Bump the Rust toolchain to 1.90", AgentStatus.ARCHIVED, 6_000),
    )

    private fun summary(
        id: String,
        name: String,
        status: AgentStatus,
        minutesAgo: Long,
    ) = AgentSummary(
        id = id,
        name = name,
        status = status,
        envType = "cloud",
        url = "https://cursor.com/agents/$id",
        createdAt = now.minus(minutesAgo, ChronoUnit.MINUTES),
        updatedAt = now.minus(minutesAgo / 2, ChronoUnit.MINUTES),
        latestRunId = "run-$id",
    )

    private fun streamingState() = AgentDetailViewModel.State(
        loading = false,
        detail = AgentDetail(
            summary = summary("bc-1", "Fix flaky checkout tests", AgentStatus.ACTIVE, 4),
            repos = listOf(RepoRef("https://github.com/acme/storefront", "main")),
            autoCreatePR = true,
        ),
        runs = listOf(
            Run(
                id = "run-bc-1",
                agentId = "bc-1",
                status = RunStatus.RUNNING,
                createdAt = now.minus(4, ChronoUnit.MINUTES),
                updatedAt = now,
                prompt = "The checkout tests fail about one run in five on CI. Find the race and fix it.",
            ),
        ),
        live = AgentDetailViewModel.LiveTurn(
            runId = "run-bc-1",
            thinking = "Reproducing the failure before changing anything.",
            assistant = "Reproduced it on the fifth attempt. The checkout suite uses real timers " +
                "while the cart debounces for 300ms, so the assertion sometimes lands before the " +
                "state settles.",
            tools = listOf(
                TimelineItem.ToolCall(
                    runId = "run-bc-1",
                    callId = "call-1",
                    name = "run_terminal_cmd",
                    running = false,
                    detail = "pnpm vitest run checkout --reporter=dot",
                ),
                TimelineItem.ToolCall(
                    runId = "run-bc-1",
                    callId = "call-2",
                    name = "read_file",
                    running = true,
                    detail = "src/checkout/useCart.test.ts",
                ),
            ),
        ),
        streaming = true,
    )

    private fun finishedState() = AgentDetailViewModel.State(
        loading = false,
        detail = AgentDetail(
            summary = summary("bc-2", "Add dark mode to the settings screen", AgentStatus.IDLE, 52),
            repos = listOf(RepoRef("https://github.com/acme/design-system", "main")),
            autoCreatePR = true,
        ),
        runs = listOf(
            Run(
                id = "run-bc-2",
                agentId = "bc-2",
                status = RunStatus.FINISHED,
                createdAt = now.minus(52, ChronoUnit.MINUTES),
                updatedAt = now.minus(31, ChronoUnit.MINUTES),
                durationMs = 1_268_000,
                result = "Added a theme token layer, wired the settings toggle to it, and updated " +
                    "the 14 components that hard-coded light colors. Screenshot tests pass.",
                branches = listOf(
                    GitBranch(
                        repoUrl = "github.com/acme/design-system",
                        branch = "cursor/settings-dark-mode-91bd",
                        prUrl = "https://github.com/acme/design-system/pull/418",
                    ),
                ),
                prompt = "Add a dark mode toggle to the settings screen and make sure the tokens " +
                    "flow through.",
            ),
        ),
        artifacts = listOf(
            Artifact("artifacts/settings-dark.png", 184_320, now.minus(35, ChronoUnit.MINUTES)),
        ),
        usage = AgentUsage(
            total = TokenUsage(12_480, 3_110, 18_200, 42_600, 76_390),
            perRun = listOf("run-bc-2" to TokenUsage(12_480, 3_110, 18_200, 42_600, 76_390)),
        ),
    )

    private fun composerChoice() = ModelChoice(
        modelId = "composer-2",
        label = "Composer 2 · Fast",
        description = "Fastest for scoped edits and repetitive refactors.",
        params = emptyList(),
        isDefault = true,
    )
}
