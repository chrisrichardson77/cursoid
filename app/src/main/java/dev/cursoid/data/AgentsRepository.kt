package dev.cursoid.data

import dev.cursoid.model.Account
import dev.cursoid.model.AgentDetail
import dev.cursoid.model.AgentMode
import dev.cursoid.model.AgentSummary
import dev.cursoid.model.AgentUsage
import dev.cursoid.model.Artifact
import dev.cursoid.model.CreateAgentRequest
import dev.cursoid.model.ModelInfo
import dev.cursoid.model.Page
import dev.cursoid.model.Run
import dev.cursoid.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface AgentsRepository {
    suspend fun account(): Account

    suspend fun agents(cursor: String? = null, includeArchived: Boolean = false): Page<AgentSummary>

    suspend fun agent(id: String): AgentDetail

    suspend fun runs(agentId: String): List<Run>

    suspend fun run(agentId: String, runId: String): Run

    suspend fun createAgent(request: CreateAgentRequest): Pair<AgentDetail, Run>

    suspend fun followUp(agentId: String, prompt: String, mode: AgentMode?): Run

    suspend fun cancel(agentId: String, runId: String)

    suspend fun setArchived(agentId: String, archived: Boolean)

    suspend fun delete(agentId: String)

    suspend fun models(): List<ModelInfo>

    suspend fun repositories(): List<String>

    suspend fun artifacts(agentId: String): List<Artifact>

    suspend fun artifactUrl(agentId: String, path: String): String

    suspend fun usage(agentId: String): AgentUsage

    fun stream(agentId: String, runId: String, lastEventId: String? = null): Flow<StreamEvent>
}
