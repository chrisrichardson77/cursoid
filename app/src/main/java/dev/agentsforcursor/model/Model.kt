package dev.agentsforcursor.model

import java.time.Instant

enum class AgentStatus {
    ACTIVE,
    IDLE,
    ARCHIVED,
    UNKNOWN;

    companion object {
        fun from(raw: String?): AgentStatus = when (raw?.uppercase()) {
            "ACTIVE" -> ACTIVE
            "IDLE" -> IDLE
            "ARCHIVED" -> ARCHIVED
            else -> UNKNOWN
        }
    }
}

enum class RunStatus {
    CREATING,
    RUNNING,
    FINISHED,
    ERROR,
    CANCELLED,
    EXPIRED,
    UNKNOWN;

    val isTerminal: Boolean
        get() = this == FINISHED || this == ERROR || this == CANCELLED || this == EXPIRED

    val isActive: Boolean
        get() = this == CREATING || this == RUNNING

    companion object {
        fun from(raw: String?): RunStatus = when (raw?.uppercase()) {
            "CREATING" -> CREATING
            "RUNNING" -> RUNNING
            "FINISHED" -> FINISHED
            "ERROR" -> ERROR
            "CANCELLED", "CANCELED" -> CANCELLED
            "EXPIRED" -> EXPIRED
            else -> UNKNOWN
        }
    }
}

enum class AgentMode(val wireValue: String, val label: String) {
    AGENT("agent", "Agent"),
    PLAN("plan", "Plan"),
}

data class RepoRef(
    val url: String,
    val startingRef: String? = null,
) {
    /** `github.com/acme/payments` renders better than the full URL in tight spaces. */
    val shortName: String
        get() = url.removePrefix("https://").removePrefix("http://").removeSuffix(".git")
            .split('/')
            .takeLast(2)
            .joinToString("/")
}

data class GitBranch(
    val repoUrl: String,
    val branch: String? = null,
    val prUrl: String? = null,
)

data class AgentSummary(
    val id: String,
    val name: String,
    val status: AgentStatus,
    val envType: String?,
    val url: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val latestRunId: String?,
)

data class AgentDetail(
    val summary: AgentSummary,
    val repos: List<RepoRef> = emptyList(),
    val workOnCurrentBranch: Boolean = false,
    val autoCreatePR: Boolean = false,
)

data class Run(
    val id: String,
    val agentId: String,
    val status: RunStatus,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val durationMs: Long? = null,
    val result: String? = null,
    val branches: List<GitBranch> = emptyList(),
    /** The API never returns prompts; this is filled in from the local cache or demo data. */
    val prompt: String? = null,
)

data class ModelParam(val id: String, val value: String)

data class ModelVariant(
    val displayName: String,
    val params: List<ModelParam> = emptyList(),
    val isDefault: Boolean = false,
)

data class ModelInfo(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val variants: List<ModelVariant> = emptyList(),
)

/** A concrete, selectable entry in the model picker: one model plus one of its variants. */
data class ModelChoice(
    val modelId: String,
    val label: String,
    val description: String?,
    val params: List<ModelParam>,
    val isDefault: Boolean,
) {
    val key: String get() = modelId + params.joinToString(prefix = "|") { "${it.id}=${it.value}" }
}

fun List<ModelInfo>.toChoices(): List<ModelChoice> = flatMap { model ->
    if (model.variants.isEmpty()) {
        listOf(
            ModelChoice(
                modelId = model.id,
                label = model.displayName,
                description = model.description,
                params = emptyList(),
                isDefault = false,
            ),
        )
    } else {
        model.variants.map { variant ->
            val paramSuffix = variant.params
                .filter { it.value.equals("true", ignoreCase = true) }
                .joinToString(", ") { it.id.replaceFirstChar(Char::uppercase) }
            ModelChoice(
                modelId = model.id,
                label = if (paramSuffix.isBlank()) variant.displayName else "${variant.displayName} · $paramSuffix",
                description = variant.displayName.takeIf { it != model.displayName } ?: model.description,
                params = variant.params,
                isDefault = variant.isDefault,
            )
        }
    }
}

data class Artifact(
    val path: String,
    val sizeBytes: Long?,
    val updatedAt: Instant?,
) {
    val fileName: String get() = path.substringAfterLast('/')

    val isImage: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in
            setOf("png", "jpg", "jpeg", "gif", "webp")
}

data class TokenUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val totalTokens: Long = 0,
)

data class AgentUsage(
    val total: TokenUsage,
    val perRun: List<Pair<String, TokenUsage>> = emptyList(),
)

data class Account(
    val apiKeyName: String?,
    val userEmail: String?,
    val userName: String?,
)

data class CreateAgentRequest(
    val prompt: String,
    val repoUrl: String?,
    val startingRef: String?,
    val modelId: String?,
    val modelParams: List<ModelParam> = emptyList(),
    val mode: AgentMode = AgentMode.AGENT,
    val autoCreatePR: Boolean = false,
    val workOnCurrentBranch: Boolean = false,
)

data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)

/** One entry in an agent's rendered conversation. */
sealed interface TimelineItem {
    val key: String

    data class Prompt(val runId: String, val text: String, val at: Instant?) : TimelineItem {
        override val key: String get() = "prompt-$runId"
    }

    data class Assistant(val runId: String, val text: String) : TimelineItem {
        override val key: String get() = "assistant-$runId"
    }

    data class Thinking(val runId: String, val text: String) : TimelineItem {
        override val key: String get() = "thinking-$runId"
    }

    data class ToolCall(
        val runId: String,
        val callId: String,
        val name: String,
        val running: Boolean,
        val detail: String?,
    ) : TimelineItem {
        override val key: String get() = "tool-$runId-$callId"
    }

    data class RunFooter(
        val runId: String,
        val status: RunStatus,
        val durationMs: Long?,
        val at: Instant?,
    ) : TimelineItem {
        override val key: String get() = "footer-$runId"
    }
}

/** Normalized SSE events from `GET /v1/agents/{id}/runs/{runId}/stream`. */
sealed interface StreamEvent {
    val eventId: String?

    data class Status(val status: RunStatus, override val eventId: String? = null) : StreamEvent

    data class AssistantDelta(val text: String, override val eventId: String? = null) : StreamEvent

    data class ThinkingDelta(val text: String, override val eventId: String? = null) : StreamEvent

    data class ToolCall(
        val callId: String,
        val name: String,
        val running: Boolean,
        val detail: String?,
        override val eventId: String? = null,
    ) : StreamEvent

    data class Result(
        val status: RunStatus,
        val text: String?,
        val durationMs: Long?,
        val branches: List<GitBranch>,
        override val eventId: String? = null,
    ) : StreamEvent

    data class Failed(val code: String?, val message: String?, override val eventId: String? = null) : StreamEvent

    data class Done(override val eventId: String? = null) : StreamEvent
}
