package dev.agentsforcursor.data.net

import dev.agentsforcursor.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(
    val statusCode: Int,
    val errorCode: String?,
    override val message: String,
) : IOException(message) {

    val isUnauthorized: Boolean get() = statusCode == 401 || statusCode == 403

    val isRateLimited: Boolean get() = statusCode == 429

    val isAgentBusy: Boolean get() = errorCode == "agent_busy"

    val isStreamExpired: Boolean get() = statusCode == 410
}

/**
 * Thin client over the Cursor Cloud Agents API v1.
 *
 * Every call needs a user or service-account API key, supplied by [tokenProvider] and sent as a
 * bearer token.
 */
class CursorApi(
    baseClient: OkHttpClient,
    private val json: Json,
    private val tokenProvider: () -> String?,
    baseUrl: String = DEFAULT_BASE_URL,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    private val client: OkHttpClient = baseClient

    /** Streaming responses stay open for the length of a turn, so they opt out of read timeouts. */
    private val streamClient: OkHttpClient = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun me(): MeDto = json.decodeFromString(get("v1", "me"))

    suspend fun listAgents(limit: Int, cursor: String?, includeArchived: Boolean): AgentListDto =
        json.decodeFromString(
            get("v1", "agents") {
                addQueryParameter("limit", limit.toString())
                cursor?.let { addQueryParameter("cursor", it) }
                addQueryParameter("includeArchived", includeArchived.toString())
            },
        )

    suspend fun agent(id: String): AgentDto = json.decodeFromString(get("v1", "agents", id))

    suspend fun createAgent(body: CreateAgentBodyDto): CreateAgentResponseDto =
        json.decodeFromString(post(json.encodeToString(body), "v1", "agents"))

    suspend fun createRun(agentId: String, body: CreateRunBodyDto): RunEnvelopeDto =
        json.decodeFromString(post(json.encodeToString(body), "v1", "agents", agentId, "runs"))

    suspend fun listRuns(agentId: String, limit: Int = 50): RunListDto = json.decodeFromString(
        get("v1", "agents", agentId, "runs") { addQueryParameter("limit", limit.toString()) },
    )

    suspend fun run(agentId: String, runId: String): RunDto =
        json.decodeFromString(get("v1", "agents", agentId, "runs", runId))

    suspend fun cancelRun(agentId: String, runId: String) {
        post(null, "v1", "agents", agentId, "runs", runId, "cancel")
    }

    suspend fun archive(agentId: String) {
        post(null, "v1", "agents", agentId, "archive")
    }

    suspend fun unarchive(agentId: String) {
        post(null, "v1", "agents", agentId, "unarchive")
    }

    suspend fun delete(agentId: String) {
        execute(Request.Builder().url(url("v1", "agents", agentId)).delete().build())
    }

    suspend fun usage(agentId: String): UsageDto =
        json.decodeFromString(get("v1", "agents", agentId, "usage"))

    suspend fun artifacts(agentId: String): ArtifactListDto =
        json.decodeFromString(get("v1", "agents", agentId, "artifacts"))

    suspend fun artifactUrl(agentId: String, path: String): ArtifactUrlDto = json.decodeFromString(
        get("v1", "agents", agentId, "artifacts", "download") { addQueryParameter("path", path) },
    )

    suspend fun models(): ModelListDto = json.decodeFromString(get("v1", "models"))

    suspend fun repositories(): RepositoryListDto = json.decodeFromString(get("v1", "repositories"))

    fun streamRun(agentId: String, runId: String, lastEventId: String?): Flow<StreamEvent> = flow {
        val request = Request.Builder()
            .url(url("v1", "agents", agentId, "runs", runId, "stream"))
            .header("Accept", "text/event-stream")
            .header("Authorization", bearer())
            .apply { lastEventId?.let { header("Last-Event-ID", it) } }
            .build()

        val call = streamClient.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        call.execute().use { response ->
            val body = response.body
            if (!response.isSuccessful) {
                throw errorFor(response.code, body?.string().orEmpty())
            }
            val source = body?.source() ?: return@use
            val parser = SseParser()
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                val event = parser.feed(line)?.toStreamEvent(json) ?: continue
                emit(event)
                if (event is StreamEvent.Done) break
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun url(vararg segments: String, block: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder()
            .apply { segments.forEach { addPathSegment(it) } }
            .apply(block)
            .build()

    private fun bearer(): String {
        val token = tokenProvider()?.trim()
        if (token.isNullOrEmpty()) {
            throw ApiException(401, "missing_api_key", "Add a Cursor API key to continue.")
        }
        return "Bearer $token"
    }

    private suspend fun get(vararg segments: String, block: HttpUrl.Builder.() -> Unit = {}): String =
        execute(Request.Builder().url(url(*segments, block = block)).get().build())

    private suspend fun post(body: String?, vararg segments: String): String {
        val requestBody: RequestBody = (body ?: "").toRequestBody(jsonMediaType)
        return execute(Request.Builder().url(url(*segments)).post(requestBody).build())
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val authorized = request.newBuilder().header("Authorization", bearer()).build()
        val call = client.newCall(authorized)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFor(response.code, text)
            text
        }
    }

    private fun errorFor(status: Int, body: String): ApiException {
        val parsed = runCatching { json.decodeFromString<ApiErrorDto>(body) }.getOrNull()
        val code = parsed?.code ?: parsed?.error
        val serverMessage = parsed?.message?.takeIf { it.isNotBlank() }
        val message = when {
            status == 401 || status == 403 ->
                "That API key was rejected. Check it in Cursor Dashboard → API Keys."

            status == 404 -> serverMessage ?: "Not found."
            status == 409 && code == "agent_busy" ->
                "This agent is already working on a turn. Wait for it to finish or cancel it."

            status == 429 -> "Rate limited by the Cursor API. Try again in a minute."
            status == 410 -> "This run's live stream expired. Showing the final result instead."
            status >= 500 -> "The Cursor API is having trouble right now (HTTP $status)."
            else -> serverMessage ?: "Request failed with HTTP $status."
        }
        return ApiException(status, code, message)
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.cursor.com/"
    }
}
