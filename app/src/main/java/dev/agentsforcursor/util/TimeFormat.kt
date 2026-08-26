package dev.agentsforcursor.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {

    private val dayFormatter = DateTimeFormatter.ofPattern("d MMM")
    private val dayYearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun relative(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return "—"
        val seconds = Duration.between(instant, now).seconds
        return when {
            seconds < 0 -> "just now"
            seconds < 45 -> "just now"
            seconds < 90 -> "1 min ago"
            seconds < 3_600 -> "${seconds / 60} min ago"
            seconds < 7_200 -> "1 hr ago"
            seconds < 86_400 -> "${seconds / 3_600} hr ago"
            seconds < 172_800 -> "yesterday"
            seconds < 604_800 -> "${seconds / 86_400} days ago"
            else -> {
                val zoned = instant.atZone(ZoneId.systemDefault())
                val nowZoned = now.atZone(ZoneId.systemDefault())
                val formatter = if (zoned.year == nowZoned.year) dayFormatter else dayYearFormatter
                formatter.format(zoned)
            }
        }
    }

    fun duration(millis: Long?): String? {
        if (millis == null || millis <= 0) return null
        val totalSeconds = millis / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun compactTokens(count: Long): String = when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}
