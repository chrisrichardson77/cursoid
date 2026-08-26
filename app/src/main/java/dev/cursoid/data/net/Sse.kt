package dev.cursoid.data.net

import dev.cursoid.model.GitBranch
import dev.cursoid.model.RunStatus
import dev.cursoid.model.StreamEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class SseFrame(
    val id: String?,
    val event: String?,
    val data: String,
)

/**
 * Line-oriented `text/event-stream` decoder. Feed it one line at a time (without the trailing
 * newline); it returns a frame each time a dispatch-triggering blank line arrives.
 */
class SseParser {
    private var id: String? = null
    private var event: String? = null
    private val data = StringBuilder()

    fun feed(line: String): SseFrame? {
        if (line.isEmpty()) {
            if (event == null && data.isEmpty()) {
                reset()
                return null
            }
            val frame = SseFrame(id = id, event = event, data = data.toString())
            reset()
            return frame
        }
        if (line.startsWith(":")) return null

        val separator = line.indexOf(':')
        val field = if (separator == -1) line else line.substring(0, separator)
        val value = if (separator == -1) "" else line.substring(separator + 1).removePrefix(" ")

        when (field) {
            "id" -> id = value
            "event" -> event = value
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
        }
        return null
    }

    private fun reset() {
        id = null
        event = null
        data.setLength(0)
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

/** Renders a tool call's arguments as one readable line, mirroring how Cursor labels tool calls. */
fun summarizeToolCall(args: JsonObject?): String? {
    if (args == null) return null
    val interesting = listOf(
        "command", "path", "file_path", "target_file", "relative_workspace_path",
        "query", "pattern", "url", "prompt", "description", "name",
    )
    for (key in interesting) {
        val value = args.string(key)?.trim()
        if (!value.isNullOrBlank()) return value.take(200)
    }
    return args.entries
        .take(3)
        .joinToString(", ") { (key, value) ->
            "$key=${value.toString().removeSurrounding("\"").take(60)}"
        }
        .takeIf { it.isNotBlank() }
        ?.take(200)
}

private fun parseBranches(payload: JsonObject?): List<GitBranch> {
    val branches = (payload?.get("git") as? JsonObject)?.get("branches") as? JsonArray
        ?: return emptyList()
    return branches.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        GitBranch(
            repoUrl = obj.string("repoUrl").orEmpty(),
            branch = obj.string("branch"),
            prUrl = obj.string("prUrl"),
        )
    }
}

fun SseFrame.toStreamEvent(json: Json): StreamEvent? {
    val payload = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
    return when (event) {
        "status" -> StreamEvent.Status(RunStatus.from(payload?.string("status")), id)
        "assistant" -> payload?.string("text")
            ?.takeIf { it.isNotEmpty() }
            ?.let { StreamEvent.AssistantDelta(it, id) }

        "thinking" -> payload?.string("text")
            ?.takeIf { it.isNotEmpty() }
            ?.let { StreamEvent.ThinkingDelta(it, id) }

        "tool_call" -> {
            val callId = payload?.string("callId") ?: return null
            StreamEvent.ToolCall(
                callId = callId,
                name = payload.string("name") ?: "tool",
                running = payload.string("status") != "completed",
                detail = summarizeToolCall(payload["args"] as? JsonObject),
                eventId = id,
            )
        }

        "result" -> StreamEvent.Result(
            status = RunStatus.from(payload?.string("status")),
            text = payload?.string("text"),
            durationMs = payload?.long("durationMs"),
            branches = parseBranches(payload),
            eventId = id,
        )

        "error" -> StreamEvent.Failed(payload?.string("code"), payload?.string("message"), id)
        "done" -> StreamEvent.Done(id)
        else -> null
    }
}
