package dev.cursoid.data.net

import dev.cursoid.model.RunStatus
import dev.cursoid.model.StreamEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(stream: String): List<StreamEvent> {
        val parser = SseParser()
        return stream.split("\n").mapNotNull { line ->
            parser.feed(line)?.toStreamEvent(json)
        }
    }

    @Test
    fun `dispatches only on a blank line`() {
        val parser = SseParser()
        assertNull(parser.feed("event: assistant"))
        assertNull(parser.feed("""data: {"text":"hi"}"""))
        val frame = parser.feed("")
        assertEquals("assistant", frame?.event)
        assertEquals("""{"text":"hi"}""", frame?.data)
    }

    @Test
    fun `keeps the event id when present and drops it otherwise`() {
        val parser = SseParser()
        parser.feed("id: 1713033000000-0")
        parser.feed("event: assistant")
        parser.feed("""data: {"text":"a"}""")
        assertEquals("1713033000000-0", parser.feed("")?.id)

        parser.feed("event: status")
        parser.feed("""data: {"status":"RUNNING"}""")
        assertNull(parser.feed("")?.id)
    }

    @Test
    fun `joins multi-line data payloads`() {
        val parser = SseParser()
        parser.feed("event: assistant")
        parser.feed("data: {\"text\":\"line one")
        parser.feed("data: line two\"}")
        assertEquals("{\"text\":\"line one\nline two\"}", parser.feed("")?.data)
    }

    @Test
    fun `ignores comments and unknown fields`() {
        val parser = SseParser()
        assertNull(parser.feed(": connected"))
        assertNull(parser.feed("retry: 3000"))
        assertNull(parser.feed(""))
    }

    @Test
    fun `maps a full turn to typed events`() {
        val stream = """
            event: status
            data: {"runId":"run-1","status":"RUNNING"}

            id: 1
            event: assistant
            data: {"text":"Reading the test."}

            id: 2
            event: tool_call
            data: {"callId":"call-1","name":"read_file","status":"running","args":{"path":"a.kt"}}

            id: 3
            event: tool_call
            data: {"callId":"call-1","name":"read_file","status":"completed","args":{"path":"a.kt"}}

            id: 4
            event: heartbeat
            data: {}

            id: 5
            event: result
            data: {"runId":"run-1","status":"FINISHED","text":"Done.","durationMs":1200,"git":{"branches":[{"repoUrl":"github.com/acme/app","branch":"cursor/fix-1","prUrl":"https://github.com/acme/app/pull/7"}]}}

            id: 6
            event: done
            data: {}

        """.trimIndent()

        val events = parse(stream)

        assertEquals(RunStatus.RUNNING, (events[0] as StreamEvent.Status).status)
        assertEquals("Reading the test.", (events[1] as StreamEvent.AssistantDelta).text)

        val started = events[2] as StreamEvent.ToolCall
        assertTrue(started.running)
        assertEquals("read_file", started.name)
        assertEquals("a.kt", started.detail)

        assertTrue(!(events[3] as StreamEvent.ToolCall).running)

        val result = events[4] as StreamEvent.Result
        assertEquals(RunStatus.FINISHED, result.status)
        assertEquals("Done.", result.text)
        assertEquals(1_200L, result.durationMs)
        assertEquals("cursor/fix-1", result.branches.single().branch)
        assertEquals("https://github.com/acme/app/pull/7", result.branches.single().prUrl)

        assertTrue(events[5] is StreamEvent.Done)
    }

    @Test
    fun `summarizes terminal commands ahead of other arguments`() {
        val args = json.parseToJsonElement(
            """{"path":"x.kt","command":"pnpm test"}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("pnpm test", summarizeToolCall(args))
    }
}
