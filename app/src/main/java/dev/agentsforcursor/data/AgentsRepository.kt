package dev.agentsforcursor.data

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
