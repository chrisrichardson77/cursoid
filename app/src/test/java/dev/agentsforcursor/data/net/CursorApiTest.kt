package dev.agentsforcursor.data.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.agentsforcursor.data.RemoteAgentsRepository
import dev.agentsforcursor.model.AgentMode
import dev.agentsforcursor.model.AgentStatus
import dev.agentsforcursor.model.CreateAgentRequest
import dev.agentsforcursor.model.RunStatus
import dev.agentsforcursor.model.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Drives [CursorApi] against a real HTTP server on loopback, so request shapes, JSON mapping, error
 * translation, and the streaming path are all exercised rather than mocked.
 */
class CursorApiTest {

    private lateinit var server: HttpServer
    private lateinit var api: CursorApi
    private lateinit var repository: RemoteAgentsRepository

    private val requests = mutableListOf<RecordedRequest>()
    private var handler: (RecordedRequest) -> Response = { Response(404, "{}") }

    data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val authorization: String?,
        val accept: String?,
        val lastEventId: String?,
        val body: String,
    )

    data class Response(
        val status: Int,
        val body: String,
        val contentType: String = "application/json",
    )

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            val recorded = RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.query,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                accept = exchange.requestHeaders.getFirst("Accept"),
                lastEventId = exchange.requestHeaders.getFirst("Last-Event-ID"),
                body = exchange.requestBody.readBytes().decodeToString(),
            )
            requests += recorded

            val response = handler(recorded)
            val bytes = response.body.toByteArray()
            exchange.responseHeaders.add("Content-Type", response.contentType)
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
        api = CursorApi(
            baseClient = OkHttpClient(),
            json = json,
            tokenProvider = { "key_test" },
            baseUrl = "http://127.0.0.1:${server.address.port}/",
        )
        repository = RemoteAgentsRepository(api)
    }

    @After
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `sends a bearer token and maps the agent list`() = runBlocking {
        handler = {
            Response(
                200,
                """
                {
                  "items": [
                    {
                      "id": "bc-1",
                      "name": "Fix flaky checkout tests",
                      "status": "ACTIVE",
                      "env": { "type": "cloud" },
                      "url": "https://cursor.com/agents/bc-1",
                      "createdAt": "2026-08-26T08:00:00.000Z",
                      "updatedAt": "2026-08-26T08:30:00.000Z",
                      "latestRunId": "run-1"
                    }
                  ],
                  "nextCursor": "bc-2"
                }
                """.trimIndent(),
            )
        }

        val page = repository.agents(includeArchived = true)
        val request = requests.single()

        assertEquals("Bearer key_test", request.authorization)
        assertEquals("/v1/agents", request.path)
        assertTrue(request.query!!.contains("includeArchived=true"))
        assertEquals("bc-2", page.nextCursor)

        val agent = page.items.single()
        assertEquals("Fix flaky checkout tests", agent.name)
        assertEquals(AgentStatus.ACTIVE, agent.status)
        assertEquals("run-1", agent.latestRunId)
        assertEquals("2026-08-26T08:30:00Z", agent.updatedAt.toString())
    }

    @Test
    fun `omits the repos field for a no-repo agent and sends the selected model`() = runBlocking {
        handler = {
            Response(
                200,
                """
                {
                  "agent": { "id": "bc-9", "name": "Draft a plan", "status": "ACTIVE" },
                  "run": { "id": "run-9", "agentId": "bc-9", "status": "CREATING" }
                }
                """.trimIndent(),
            )
        }

        val (detail, run) = repository.createAgent(
            CreateAgentRequest(
                prompt = "Draft a migration plan",
                repoUrl = null,
                startingRef = null,
                modelId = "composer-2",
                mode = AgentMode.PLAN,
                autoCreatePR = false,
            ),
        )

        val body = requests.single().body
        assertTrue(body.contains(""""text":"Draft a migration plan""""))
        assertTrue(body.contains(""""id":"composer-2""""))
        assertTrue(body.contains(""""mode":"plan""""))
        assertTrue("repos should be absent entirely", !body.contains("repos"))
        assertEquals("bc-9", detail.summary.id)
        assertEquals(RunStatus.CREATING, run.status)
    }

    @Test
    fun `translates an unauthorized response into guidance`() = runBlocking {
        handler = { Response(401, """{"code":"unauthorized","message":"bad key"}""") }

        val error = runCatching { repository.account() }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertTrue((error as ApiException).isUnauthorized)
        assertTrue(error.message.contains("API Keys"))
    }

    @Test
    fun `surfaces a busy agent as its own case`() = runBlocking {
        handler = { Response(409, """{"code":"agent_busy","message":"still running"}""") }

        val error = runCatching {
            repository.followUp("bc-1", "also add tests", mode = null)
        }.exceptionOrNull() as ApiException

        assertTrue(error.isAgentBusy)
        assertTrue(error.message.contains("mid-turn") || error.message.contains("already working"))
    }

    @Test
    fun `treats an expired stream as a signal to read terminal state`() = runBlocking {
        handler = { Response(410, """{"code":"stream_expired"}""") }

        val error = runCatching {
            repository.stream("bc-1", "run-1").toList()
        }.exceptionOrNull() as ApiException

        assertTrue(error.isStreamExpired)
    }

    @Test
    fun `streams a turn and forwards the resume header`() = runBlocking {
        handler = { request ->
            if (request.path.endsWith("/stream")) {
                Response(
                    200,
                    buildString {
                        append("event: status\n")
                        append("""data: {"runId":"run-1","status":"RUNNING"}""").append("\n\n")
                        append("id: 1\n")
                        append("event: assistant\n")
                        append("""data: {"text":"Reading the test."}""").append("\n\n")
                        append("id: 2\n")
                        append("event: result\n")
                        append(
                            """data: {"runId":"run-1","status":"FINISHED","text":"Fixed.",""" +
                                """"durationMs":9000,"git":{"branches":[{"repoUrl":"github.com/acme/app",""" +
                                """"branch":"cursor/fix-1"}]}}""",
                        ).append("\n\n")
                        append("id: 3\n")
                        append("event: done\n")
                        append("data: {}").append("\n\n")
                    },
                    contentType = "text/event-stream",
                )
            } else {
                Response(404, "{}")
            }
        }

        val events = repository.stream("bc-1", "run-1", lastEventId = "0").toList()
        val request = requests.single()

        assertEquals("text/event-stream", request.accept)
        assertEquals("0", request.lastEventId)
        assertEquals("/v1/agents/bc-1/runs/run-1/stream", request.path)

        assertEquals(RunStatus.RUNNING, (events[0] as StreamEvent.Status).status)
        assertNull(events[0].eventId)
        assertEquals("Reading the test.", (events[1] as StreamEvent.AssistantDelta).text)
        assertEquals("1", events[1].eventId)

        val result = events[2] as StreamEvent.Result
        assertEquals(RunStatus.FINISHED, result.status)
        assertEquals(9_000L, result.durationMs)
        assertEquals("cursor/fix-1", result.branches.single().branch)
        assertTrue(events[3] is StreamEvent.Done)
    }

    @Test
    fun `serves the cached repository list when a refresh is rate limited`() = runBlocking {
        handler = { Response(200, """{"items":[{"url":"https://github.com/acme/app"}]}""") }
        assertEquals(listOf("https://github.com/acme/app"), repository.repositories())

        handler = { Response(429, """{"code":"rate_limited"}""") }
        assertEquals(listOf("https://github.com/acme/app"), repository.repositories())
    }
}
