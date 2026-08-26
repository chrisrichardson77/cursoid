package dev.cursoid.data

import dev.cursoid.model.Account
import dev.cursoid.model.AgentDetail
import dev.cursoid.model.AgentMode
import dev.cursoid.model.AgentStatus
import dev.cursoid.model.AgentSummary
import dev.cursoid.model.AgentUsage
import dev.cursoid.model.Artifact
import dev.cursoid.model.CreateAgentRequest
import dev.cursoid.model.GitBranch
import dev.cursoid.model.ModelInfo
import dev.cursoid.model.ModelParam
import dev.cursoid.model.ModelVariant
import dev.cursoid.model.Page
import dev.cursoid.model.RepoRef
import dev.cursoid.model.Run
import dev.cursoid.model.RunStatus
import dev.cursoid.model.StreamEvent
import dev.cursoid.model.TokenUsage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * A self-contained stand-in for the Cursor API so the app is fully explorable without an API key.
 * Mutations are kept in memory, and the active agent's turn is streamed with realistic pacing.
 */
class DemoAgentsRepository(
    private val artifactUri: (String) -> String,
) : AgentsRepository {

    private val mutex = Mutex()
    private val now: Instant = Instant.now()
    private var agents: MutableList<AgentDetail> = seedAgents()
    private var runs: MutableMap<String, MutableList<Run>> = seedRuns()

    override suspend fun account(): Account = Account(
        apiKeyName = "Demo key",
        userEmail = "you@example.com",
        userName = "Demo session",
    )

    override suspend fun agents(cursor: String?, includeArchived: Boolean): Page<AgentSummary> =
        mutex.withLock {
            Page(
                items = agents
                    .filter { includeArchived || it.summary.status != AgentStatus.ARCHIVED }
                    .map { it.summary },
            )
        }

    override suspend fun agent(id: String): AgentDetail = mutex.withLock {
        agents.firstOrNull { it.summary.id == id } ?: error("No demo agent $id")
    }

    override suspend fun runs(agentId: String): List<Run> = mutex.withLock {
        runs[agentId]?.toList().orEmpty()
    }

    override suspend fun run(agentId: String, runId: String): Run = mutex.withLock {
        runs[agentId]?.firstOrNull { it.id == runId } ?: error("No demo run $runId")
    }

    override suspend fun createAgent(request: CreateAgentRequest): Pair<AgentDetail, Run> =
        mutex.withLock {
            val id = "bc-demo-${UUID.randomUUID()}"
            val runId = "run-demo-${UUID.randomUUID()}"
            val detail = AgentDetail(
                summary = AgentSummary(
                    id = id,
                    name = request.prompt.lineSequence().first().take(60),
                    status = AgentStatus.ACTIVE,
                    envType = "cloud",
                    url = "https://cursor.com/agents/$id",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    latestRunId = runId,
                ),
                repos = request.repoUrl?.let { listOf(RepoRef(it, request.startingRef)) }.orEmpty(),
                autoCreatePR = request.autoCreatePR,
                workOnCurrentBranch = request.workOnCurrentBranch,
            )
            val run = Run(
                id = runId,
                agentId = id,
                status = RunStatus.RUNNING,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                prompt = request.prompt,
            )
            agents.add(0, detail)
            runs[id] = mutableListOf(run)
            detail to run
        }

    override suspend fun followUp(agentId: String, prompt: String, mode: AgentMode?): Run =
        mutex.withLock {
            val runId = "run-demo-${UUID.randomUUID()}"
            val run = Run(
                id = runId,
                agentId = agentId,
                status = RunStatus.RUNNING,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                prompt = prompt,
            )
            runs.getOrPut(agentId) { mutableListOf() }.add(0, run)
            replaceAgent(agentId) { detail ->
                detail.copy(
                    summary = detail.summary.copy(
                        status = AgentStatus.ACTIVE,
                        latestRunId = runId,
                        updatedAt = Instant.now(),
                    ),
                )
            }
            run
        }

    override suspend fun cancel(agentId: String, runId: String) = mutex.withLock {
        runs[agentId]?.replaceAll { run ->
            if (run.id == runId) run.copy(status = RunStatus.CANCELLED) else run
        }
        replaceAgent(agentId) { it.copy(summary = it.summary.copy(status = AgentStatus.IDLE)) }
    }

    override suspend fun setArchived(agentId: String, archived: Boolean) = mutex.withLock {
        replaceAgent(agentId) { detail ->
            detail.copy(
                summary = detail.summary.copy(
                    status = if (archived) AgentStatus.ARCHIVED else AgentStatus.IDLE,
                ),
            )
        }
    }

    override suspend fun delete(agentId: String) {
        mutex.withLock {
            agents.removeAll { it.summary.id == agentId }
            runs.remove(agentId)
        }
    }

    override suspend fun models(): List<ModelInfo> = listOf(
        ModelInfo(
            id = "composer-2",
            displayName = "Composer 2",
            description = "Fastest for scoped edits and repetitive refactors.",
            variants = listOf(
                ModelVariant("Composer 2", listOf(ModelParam("fast", "true")), isDefault = true),
                ModelVariant("Composer 2", listOf(ModelParam("fast", "false"))),
            ),
        ),
        ModelInfo(
            id = "claude-4.6-sonnet-thinking",
            displayName = "Claude 4.6 Sonnet (Thinking)",
            description = "Balanced default for multi-file work.",
            variants = listOf(ModelVariant("Claude 4.6 Sonnet (Thinking)", isDefault = false)),
        ),
        ModelInfo(
            id = "gpt-5-codex",
            displayName = "GPT-5 Codex",
            description = "Strong at long debugging sessions.",
            variants = listOf(ModelVariant("GPT-5 Codex")),
        ),
    )

    override suspend fun repositories(): List<String> = listOf(
        "https://github.com/acme/storefront",
        "https://github.com/acme/payments-service",
        "https://github.com/acme/design-system",
        "https://github.com/acme/infra",
    )

    override suspend fun artifacts(agentId: String): List<Artifact> =
        if (agentId == LATENCY_AGENT_ID) {
            listOf(
                Artifact("artifacts/p95-before-after.png", 184_320, now.minus(3, ChronoUnit.HOURS)),
                Artifact("artifacts/trace-summary.txt", 2_048, now.minus(3, ChronoUnit.HOURS)),
            )
        } else {
            emptyList()
        }

    override suspend fun artifactUrl(agentId: String, path: String): String = artifactUri(path)

    override suspend fun usage(agentId: String): AgentUsage {
        val agentRuns = runs(agentId)
        val perRun = agentRuns.mapIndexed { index, run ->
            run.id to TokenUsage(
                inputTokens = 6_000L + index * 1_400,
                outputTokens = 1_500L + index * 220,
                cacheWriteTokens = 7_100L + index * 900,
                cacheReadTokens = 21_300L + index * 3_100,
                totalTokens = 35_900L + index * 5_620,
            )
        }
        return AgentUsage(
            total = TokenUsage(
                inputTokens = perRun.sumOf { it.second.inputTokens },
                outputTokens = perRun.sumOf { it.second.outputTokens },
                cacheWriteTokens = perRun.sumOf { it.second.cacheWriteTokens },
                cacheReadTokens = perRun.sumOf { it.second.cacheReadTokens },
                totalTokens = perRun.sumOf { it.second.totalTokens },
            ),
            perRun = perRun,
        )
    }

    override fun stream(agentId: String, runId: String, lastEventId: String?): Flow<StreamEvent> =
        flow {
            emit(StreamEvent.Status(RunStatus.RUNNING))
            delay(400)
            emit(StreamEvent.ThinkingDelta("Reproducing the failure before changing anything."))
            delay(700)
            for (chunk in ASSISTANT_CHUNKS) {
                emit(StreamEvent.AssistantDelta(chunk))
                delay(260)
            }
            emit(
                StreamEvent.ToolCall(
                    callId = "call-1",
                    name = "run_terminal_cmd",
                    running = true,
                    detail = "pnpm vitest run checkout --reporter=dot",
                ),
            )
            delay(1_200)
            emit(
                StreamEvent.ToolCall(
                    callId = "call-1",
                    name = "run_terminal_cmd",
                    running = false,
                    detail = "pnpm vitest run checkout --reporter=dot",
                ),
            )
            delay(500)
            emit(
                StreamEvent.ToolCall(
                    callId = "call-2",
                    name = "read_file",
                    running = false,
                    detail = "src/checkout/useCart.test.ts",
                ),
            )
            delay(900)
            emit(StreamEvent.AssistantDelta(" The timer was never advanced, so the assertion raced the debounce."))
            delay(800)
            val branch = GitBranch(
                repoUrl = "github.com/acme/storefront",
                branch = "cursor/fix-flaky-checkout-tests-4f2a",
                prUrl = null,
            )
            emit(
                StreamEvent.Result(
                    status = RunStatus.FINISHED,
                    text = "Replaced the real timers in the checkout suite with fake timers and " +
                        "advanced them explicitly. 40 consecutive runs passed locally.",
                    durationMs = 214_000,
                    branches = listOf(branch),
                ),
            )
            mutex.withLock {
                runs[agentId]?.replaceAll { run ->
                    if (run.id == runId) {
                        run.copy(
                            status = RunStatus.FINISHED,
                            durationMs = 214_000,
                            result = "Replaced the real timers in the checkout suite with fake " +
                                "timers and advanced them explicitly. 40 consecutive runs passed locally.",
                            branches = listOf(branch),
                        )
                    } else {
                        run
                    }
                }
                replaceAgent(agentId) {
                    it.copy(summary = it.summary.copy(status = AgentStatus.IDLE))
                }
            }
            emit(StreamEvent.Done())
        }

    private fun replaceAgent(agentId: String, transform: (AgentDetail) -> AgentDetail) {
        val index = agents.indexOfFirst { it.summary.id == agentId }
        if (index >= 0) agents[index] = transform(agents[index])
    }

    private fun seedAgents(): MutableList<AgentDetail> = mutableListOf(
        demoAgent(
            id = FLAKY_AGENT_ID,
            name = "Fix flaky checkout tests",
            status = AgentStatus.ACTIVE,
            repo = "https://github.com/acme/storefront",
            ref = "main",
            minutesAgo = 4,
            latestRunId = FLAKY_RUN_ID,
            autoCreatePR = true,
        ),
        demoAgent(
            id = DARK_MODE_AGENT_ID,
            name = "Add dark mode to the settings screen",
            status = AgentStatus.IDLE,
            repo = "https://github.com/acme/design-system",
            ref = "main",
            minutesAgo = 52,
            latestRunId = DARK_MODE_RUN_ID,
            autoCreatePR = true,
        ),
        demoAgent(
            id = LATENCY_AGENT_ID,
            name = "Investigate p95 latency regression",
            status = AgentStatus.IDLE,
            repo = "https://github.com/acme/payments-service",
            ref = "main",
            minutesAgo = 185,
            latestRunId = LATENCY_RUN_ID,
        ),
        demoAgent(
            id = ANALYTICS_AGENT_ID,
            name = "Migrate analytics to typed events",
            status = AgentStatus.IDLE,
            repo = "https://github.com/acme/storefront",
            ref = "main",
            minutesAgo = 1_500,
            latestRunId = ANALYTICS_RUN_ID,
        ),
        demoAgent(
            id = TOOLCHAIN_AGENT_ID,
            name = "Bump the Rust toolchain to 1.90",
            status = AgentStatus.ARCHIVED,
            repo = "https://github.com/acme/infra",
            ref = "main",
            minutesAgo = 6_000,
            latestRunId = TOOLCHAIN_RUN_ID,
        ),
    )

    private fun demoAgent(
        id: String,
        name: String,
        status: AgentStatus,
        repo: String,
        ref: String,
        minutesAgo: Long,
        latestRunId: String,
        autoCreatePR: Boolean = false,
    ) = AgentDetail(
        summary = AgentSummary(
            id = id,
            name = name,
            status = status,
            envType = "cloud",
            url = "https://cursor.com/agents/$id",
            createdAt = now.minus(minutesAgo, ChronoUnit.MINUTES),
            updatedAt = now.minus(minutesAgo / 2, ChronoUnit.MINUTES),
            latestRunId = latestRunId,
        ),
        repos = listOf(RepoRef(repo, ref)),
        autoCreatePR = autoCreatePR,
    )

    private fun seedRuns(): MutableMap<String, MutableList<Run>> = mutableMapOf(
        FLAKY_AGENT_ID to mutableListOf(
            Run(
                id = FLAKY_RUN_ID,
                agentId = FLAKY_AGENT_ID,
                status = RunStatus.RUNNING,
                createdAt = now.minus(4, ChronoUnit.MINUTES),
                updatedAt = now,
                prompt = "The checkout tests fail about one run in five on CI. Find the race and fix it.",
            ),
        ),
        DARK_MODE_AGENT_ID to mutableListOf(
            Run(
                id = DARK_MODE_RUN_ID,
                agentId = DARK_MODE_AGENT_ID,
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
                prompt = "Add a dark mode toggle to the settings screen and make sure the tokens flow through.",
            ),
        ),
        LATENCY_AGENT_ID to mutableListOf(
            Run(
                id = LATENCY_RUN_ID,
                agentId = LATENCY_AGENT_ID,
                status = RunStatus.FINISHED,
                createdAt = now.minus(185, ChronoUnit.MINUTES),
                updatedAt = now.minus(160, ChronoUnit.MINUTES),
                durationMs = 1_502_000,
                result = "The regression traces to the N+1 lookup added in #2214. I captured a " +
                    "before/after flame chart and left the fix behind a feature flag.",
                branches = listOf(
                    GitBranch(
                        repoUrl = "github.com/acme/payments-service",
                        branch = "cursor/p95-n-plus-one-7cd1",
                    ),
                ),
                prompt = "p95 on /charges doubled after last Thursday's deploy. Find out why.",
            ),
        ),
        ANALYTICS_AGENT_ID to mutableListOf(
            Run(
                id = ANALYTICS_RUN_ID,
                agentId = ANALYTICS_AGENT_ID,
                status = RunStatus.ERROR,
                createdAt = now.minus(1_500, ChronoUnit.MINUTES),
                updatedAt = now.minus(1_480, ChronoUnit.MINUTES),
                durationMs = 402_000,
                result = "Stopped early: the analytics schema package is a private registry " +
                    "dependency and the install step could not authenticate.",
                prompt = "Move every analytics call over to the typed event helpers.",
            ),
        ),
        TOOLCHAIN_AGENT_ID to mutableListOf(
            Run(
                id = TOOLCHAIN_RUN_ID,
                agentId = TOOLCHAIN_AGENT_ID,
                status = RunStatus.FINISHED,
                createdAt = now.minus(6_000, ChronoUnit.MINUTES),
                updatedAt = now.minus(5_980, ChronoUnit.MINUTES),
                durationMs = 980_000,
                result = "Bumped the toolchain, fixed three new clippy lints, and refreshed the " +
                    "CI cache key.",
                branches = listOf(
                    GitBranch(
                        repoUrl = "github.com/acme/infra",
                        branch = "cursor/rust-1-90-2ab8",
                        prUrl = "https://github.com/acme/infra/pull/77",
                    ),
                ),
                prompt = "Bump the Rust toolchain to 1.90 and fix whatever breaks.",
            ),
        ),
    )

    private companion object {
        const val FLAKY_AGENT_ID = "bc-demo-flaky-checkout"
        const val DARK_MODE_AGENT_ID = "bc-demo-dark-mode"
        const val LATENCY_AGENT_ID = "bc-demo-latency"
        const val ANALYTICS_AGENT_ID = "bc-demo-analytics"
        const val TOOLCHAIN_AGENT_ID = "bc-demo-toolchain"

        const val FLAKY_RUN_ID = "run-demo-flaky-1"
        const val DARK_MODE_RUN_ID = "run-demo-dark-1"
        const val LATENCY_RUN_ID = "run-demo-latency-1"
        const val ANALYTICS_RUN_ID = "run-demo-analytics-1"
        const val TOOLCHAIN_RUN_ID = "run-demo-toolchain-1"

        val ASSISTANT_CHUNKS = listOf(
            "Reproduced it on the fifth attempt.",
            " The checkout suite uses real timers while the cart debounces for 300ms,",
            " so the assertion sometimes lands before the state settles.",
        )
    }
}
