package dev.cursoid.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class TimeFormatTest {

    private val now: Instant = Instant.parse("2026-08-24T12:00:00Z")

    @Test
    fun `describes recent instants in relative terms`() {
        assertEquals("just now", TimeFormat.relative(now.minus(10, ChronoUnit.SECONDS), now))
        assertEquals("1 min ago", TimeFormat.relative(now.minus(80, ChronoUnit.SECONDS), now))
        assertEquals("12 min ago", TimeFormat.relative(now.minus(12, ChronoUnit.MINUTES), now))
        assertEquals("5 hr ago", TimeFormat.relative(now.minus(5, ChronoUnit.HOURS), now))
        assertEquals("yesterday", TimeFormat.relative(now.minus(30, ChronoUnit.HOURS), now))
        assertEquals("3 days ago", TimeFormat.relative(now.minus(3, ChronoUnit.DAYS), now))
    }

    @Test
    fun `falls back to a placeholder without a timestamp`() {
        assertEquals("—", TimeFormat.relative(null, now))
    }

    @Test
    fun `formats run durations`() {
        assertEquals("42s", TimeFormat.duration(42_000))
        assertEquals("3m 30s", TimeFormat.duration(210_000))
        assertEquals("1h 10m", TimeFormat.duration(4_200_000))
        assertEquals(null, TimeFormat.duration(0))
        assertEquals(null, TimeFormat.duration(null))
    }

    @Test
    fun `shortens token counts`() {
        assertEquals("640", TimeFormat.compactTokens(640))
        assertEquals("12.5k", TimeFormat.compactTokens(12_480))
        assertEquals("1.2M", TimeFormat.compactTokens(1_200_000))
    }
}
