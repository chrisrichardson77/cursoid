package dev.agentsforcursor.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.agentsforcursor.MainActivity
import dev.agentsforcursor.R
import dev.agentsforcursor.model.RunStatus

object Notifications {

    const val CHANNEL_TURNS = "turn-finished"
    const val CHANNEL_TRACKING = "agent-tracking"
    const val EXTRA_AGENT_ID = "agentId"

    const val TRACKING_NOTIFICATION_ID = 4_100

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TURNS,
                context.getString(R.string.channel_turn_done_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_turn_done_description)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                context.getString(R.string.channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_tracking_description)
                setShowBadge(false)
            },
        )
    }

    fun canPost(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    fun openAgentIntent(context: Context, agentId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_AGENT_ID, agentId)
        }
        return PendingIntent.getActivity(
            context,
            agentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifyTurnFinished(
        context: Context,
        agentId: String,
        agentName: String,
        status: RunStatus,
        summary: String?,
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val headline = when (status) {
            RunStatus.ERROR -> "Stopped with an error"
            RunStatus.CANCELLED -> "Turn cancelled"
            RunStatus.EXPIRED -> "Turn expired"
            else -> "Finished a turn"
        }
        val body = summary?.takeIf { it.isNotBlank() } ?: "Open the agent to review the changes."

        val notification = NotificationCompat.Builder(context, CHANNEL_TURNS)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("$agentName · $headline")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAgentIntent(context, agentId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        NotificationManagerCompat.from(context).notify(agentId.hashCode(), notification)
    }

    /**
     * The ongoing notification that follows a working agent. This is the closest Android analogue
     * to the iOS app's Live Activity.
     */
    fun trackingNotification(
        context: Context,
        agentId: String,
        agentName: String,
        line: String,
        finished: Boolean,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_TRACKING)
        .setSmallIcon(R.drawable.ic_stat_agent)
        .setContentTitle(agentName)
        .setContentText(line)
        .setStyle(NotificationCompat.BigTextStyle().bigText(line))
        .setContentIntent(openAgentIntent(context, agentId))
        .setOngoing(!finished)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .apply { if (!finished) setProgress(0, 0, true) }
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()
}
