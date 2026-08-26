package dev.agentsforcursor.data

import dev.agentsforcursor.data.net.CreateAgentBodyDto
import dev.agentsforcursor.data.net.CreateRunBodyDto
import dev.agentsforcursor.data.net.CursorApi
import dev.agentsforcursor.data.net.ModelParamDto
import dev.agentsforcursor.data.net.ModelSelectionDto
import dev.agentsforcursor.data.net.PromptDto
import dev.agentsforcursor.data.net.RepoDto
import dev.agentsforcursor.data.net.toAccount
import dev.agentsforcursor.data.net.toArtifact
import dev.agentsforcursor.data.net.toDetail
import dev.agentsforcursor.data.net.toModelInfo
import dev.agentsforcursor.data.net.toRun
import dev.agentsforcursor.data.net.toSummary
import dev.agentsforcursor.data.net.toTokenUsage
import dev.agentsforcursor.model.Account
import dev.agentsforcursor.model.AgentDetail
import dev.agentsforcursor.model.AgentMode
import dev.agentsforcursor.model.AgentSummary
import dev.agentsforcursor.model.AgentUsage
import dev.agentsforcursor.model.Artifact
import dev.agentsforcursor.model.CreateAgentRequest
import dev.agentsforcursor.model.ModelInfo
import dev.agentsforcursor.model.Page
import dev.agentsforcursor.model.Run
import dev.agentsforcursor.model.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class RemoteAgentsRepository(private val api: CursorApi) : AgentsRepository {

    /** `GET /v1/repositories` is limited to one call per user per minute, so it is cached hard. */
    private val repositoryCache = TimedCache<List<String>>(10.minutes)
    private val modelCache = TimedCache<List<ModelInfo>>(30.minutes)

    override suspend fun account(): Account = api.me().toAccount()

    override suspend fun agents(cursor: String?, includeArchived: Boolean): Page<AgentSummary> {
        val response = api.listAgents(limit = 30, cursor = cursor, includeArchived = includeArchived)
        return Page(
            items = response.items.map { it.toSummary() },
            nextCursor = response.nextCursor,
        )
    }

    override suspend fun agent(id: String): AgentDetail = api.agent(id).toDetail()

    override suspend fun runs(agentId: String): List<Run> =
        api.listRuns(agentId).items.map { it.toRun(agentId) }

    override suspend fun run(agentId: String, runId: String): Run =
        api.run(agentId, runId).toRun(agentId)

    override suspend fun createAgent(request: CreateAgentRequest): Pair<AgentDetail, Run> {
        val body = CreateAgentBodyDto(
            prompt = PromptDto(request.prompt),
            model = request.modelId?.let { id ->
                ModelSelectionDto(
                    id = id,
                    params = request.modelParams
                        .map { ModelParamDto(it.id, it.value) }
                        .takeIf { it.isNotEmpty() },
                )
            },
            repos = request.repoUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(RepoDto(url = it, startingRef = request.startingRef?.ifBlank { null })) },
            mode = request.mode.wireValue,
            autoCreatePR = request.autoCreatePR,
            workOnCurrentBranch = request.workOnCurrentBranch.takeIf { it },
        )
        val response = api.createAgent(body)
        return response.agent.toDetail() to response.run.toRun(response.agent.id)
    }

    override suspend fun followUp(agentId: String, prompt: String, mode: AgentMode?): Run =
        api.createRun(
            agentId = agentId,
            body = CreateRunBodyDto(prompt = PromptDto(prompt), mode = mode?.wireValue),
        ).run.toRun(agentId)

    override suspend fun cancel(agentId: String, runId: String) = api.cancelRun(agentId, runId)

    override suspend fun setArchived(agentId: String, archived: Boolean) {
        if (archived) api.archive(agentId) else api.unarchive(agentId)
    }

    override suspend fun delete(agentId: String) = api.delete(agentId)

    override suspend fun models(): List<ModelInfo> = modelCache.get {
        api.models().items.map { it.toModelInfo() }
    }

    override suspend fun repositories(): List<String> = repositoryCache.get {
        api.repositories().items.map { it.url }
    }

    override suspend fun artifacts(agentId: String): List<Artifact> =
        api.artifacts(agentId).items.map { it.toArtifact() }

    override suspend fun artifactUrl(agentId: String, path: String): String =
        api.artifactUrl(agentId, path).url

    override suspend fun usage(agentId: String): AgentUsage {
        val response = api.usage(agentId)
        return AgentUsage(
            total = response.totalUsage.toTokenUsage(),
            perRun = response.runs.map { it.id to it.usage.toTokenUsage() },
        )
    }

    override fun stream(agentId: String, runId: String, lastEventId: String?): Flow<StreamEvent> =
        api.streamRun(agentId, runId, lastEventId)
}

/** Serves the last successful value until it goes stale, and again if a refresh fails. */
internal class TimedCache<T>(private val ttl: Duration) {
    private var value: T? = null
    private var loadedAtMillis: Long = 0

    suspend fun get(load: suspend () -> T): T {
        val cached = value
        val fresh = System.currentTimeMillis() - loadedAtMillis < ttl.inWholeMilliseconds
        if (cached != null && fresh) return cached
        return try {
            load().also {
                value = it
                loadedAtMillis = System.currentTimeMillis()
            }
        } catch (error: Throwable) {
            cached ?: throw error
        }
    }
}
