package dev.agentsforcursor.data.net

import dev.agentsforcursor.model.Account
import dev.agentsforcursor.model.AgentDetail
import dev.agentsforcursor.model.AgentStatus
import dev.agentsforcursor.model.AgentSummary
import dev.agentsforcursor.model.Artifact
import dev.agentsforcursor.model.GitBranch
import dev.agentsforcursor.model.ModelInfo
import dev.agentsforcursor.model.ModelParam
import dev.agentsforcursor.model.ModelVariant
import dev.agentsforcursor.model.RepoRef
import dev.agentsforcursor.model.Run
import dev.agentsforcursor.model.RunStatus
import dev.agentsforcursor.model.TokenUsage
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class EnvDto(val type: String? = null, val name: String? = null)

@Serializable
data class RepoDto(
    val url: String,
    val startingRef: String? = null,
    val prUrl: String? = null,
)

@Serializable
data class AgentDto(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    val env: EnvDto? = null,
    val repos: List<RepoDto> = emptyList(),
    val workOnCurrentBranch: Boolean = false,
    val autoCreatePR: Boolean = false,
    val url: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val latestRunId: String? = null,
)

@Serializable
data class AgentListDto(
    val items: List<AgentDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class BranchDto(
    val repoUrl: String? = null,
    val branch: String? = null,
    val prUrl: String? = null,
)

@Serializable
data class GitDto(val branches: List<BranchDto> = emptyList())

@Serializable
data class RunDto(
    val id: String,
    val agentId: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val durationMs: Long? = null,
    val result: String? = null,
    val git: GitDto? = null,
)

@Serializable
data class RunListDto(
    val items: List<RunDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class RunEnvelopeDto(val run: RunDto)

@Serializable
data class CreateAgentResponseDto(val agent: AgentDto, val run: RunDto)

@Serializable
data class PromptDto(val text: String)

@Serializable
data class ModelParamDto(val id: String, val value: String)

@Serializable
data class ModelSelectionDto(
    val id: String,
    val params: List<ModelParamDto>? = null,
)

@Serializable
data class CreateAgentBodyDto(
    val prompt: PromptDto,
    val model: ModelSelectionDto? = null,
    val repos: List<RepoDto>? = null,
    val mode: String? = null,
    val autoCreatePR: Boolean? = null,
    val workOnCurrentBranch: Boolean? = null,
)

@Serializable
data class CreateRunBodyDto(
    val prompt: PromptDto,
    val mode: String? = null,
)

@Serializable
data class ModelVariantDto(
    val params: List<ModelParamDto> = emptyList(),
    val displayName: String? = null,
    val description: String? = null,
    val isDefault: Boolean = false,
)

@Serializable
data class ModelDto(
    val id: String,
    val displayName: String? = null,
    val description: String? = null,
    val variants: List<ModelVariantDto> = emptyList(),
)

@Serializable
data class ModelListDto(val items: List<ModelDto> = emptyList())

@Serializable
data class RepositoryDto(val url: String)

@Serializable
data class RepositoryListDto(val items: List<RepositoryDto> = emptyList())

@Serializable
data class ArtifactDto(
    val path: String,
    val sizeBytes: Long? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ArtifactListDto(val items: List<ArtifactDto> = emptyList())

@Serializable
data class ArtifactUrlDto(val url: String, val expiresAt: String? = null)

@Serializable
data class TokenUsageDto(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val totalTokens: Long = 0,
)

@Serializable
data class RunUsageDto(val id: String, val usage: TokenUsageDto = TokenUsageDto())

@Serializable
data class UsageDto(
    val totalUsage: TokenUsageDto = TokenUsageDto(),
    val runs: List<RunUsageDto> = emptyList(),
)

@Serializable
data class MeDto(
    val apiKeyName: String? = null,
    val userEmail: String? = null,
    val userFirstName: String? = null,
    val userLastName: String? = null,
)

@Serializable
data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
    val error: String? = null,
)

private fun String?.toInstantOrNull(): Instant? =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun AgentDto.toSummary(): AgentSummary = AgentSummary(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: "Untitled agent",
    status = AgentStatus.from(status),
    envType = env?.name ?: env?.type,
    url = url,
    createdAt = createdAt.toInstantOrNull(),
    updatedAt = updatedAt.toInstantOrNull(),
    latestRunId = latestRunId,
)

fun AgentDto.toDetail(): AgentDetail = AgentDetail(
    summary = toSummary(),
    repos = repos.map { RepoRef(url = it.url, startingRef = it.startingRef) },
    workOnCurrentBranch = workOnCurrentBranch,
    autoCreatePR = autoCreatePR,
)

fun BranchDto.toBranch(): GitBranch = GitBranch(
    repoUrl = repoUrl.orEmpty(),
    branch = branch,
    prUrl = prUrl,
)

fun RunDto.toRun(fallbackAgentId: String): Run = Run(
    id = id,
    agentId = agentId ?: fallbackAgentId,
    status = RunStatus.from(status),
    createdAt = createdAt.toInstantOrNull(),
    updatedAt = updatedAt.toInstantOrNull(),
    durationMs = durationMs,
    result = result,
    branches = git?.branches?.map { it.toBranch() }.orEmpty(),
)

fun ModelDto.toModelInfo(): ModelInfo = ModelInfo(
    id = id,
    displayName = displayName ?: id,
    description = description,
    variants = variants.map { variant ->
        ModelVariant(
            displayName = variant.displayName ?: displayName ?: id,
            params = variant.params.map { ModelParam(it.id, it.value) },
            isDefault = variant.isDefault,
        )
    },
)

fun ArtifactDto.toArtifact(): Artifact = Artifact(
    path = path,
    sizeBytes = sizeBytes,
    updatedAt = updatedAt.toInstantOrNull(),
)

fun TokenUsageDto.toTokenUsage(): TokenUsage = TokenUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cacheWriteTokens = cacheWriteTokens,
    cacheReadTokens = cacheReadTokens,
    totalTokens = totalTokens,
)

fun MeDto.toAccount(): Account = Account(
    apiKeyName = apiKeyName,
    userEmail = userEmail,
    userName = listOfNotNull(userFirstName, userLastName)
        .joinToString(" ")
        .takeIf { it.isNotBlank() },
)
