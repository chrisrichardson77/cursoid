package dev.agentsforcursor.notify

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.agentsforcursor.AgentsApplication
import dev.agentsforcursor.model.StreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Follows one run to completion while the app is in the background, updating an ongoing
 * notification as the agent works and posting a final one when the turn ends.
 */
class RunTrackerService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var streamJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val agentId = intent?.getStringExtra(EXTRA_AGENT_ID)
        val runId = intent?.getStringExtra(EXTRA_RUN_ID)
        val agentName = intent?.getStringExtra(EXTRA_AGENT_NAME) ?: "Cloud agent"

        if (agentId == null || runId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWith(agentId, agentName, "Starting up…")

        streamJob?.cancel()
        streamJob = scope.launch {
            val container = (application as AgentsApplication).container
            var lastLine = "Working…"
            container.repository.stream(agentId, runId)
                .catch { error ->
                    update(agentId, agentName, error.message ?: "Lost the connection.", finished = true)
                }
                .collect { event ->
                    when (event) {
                        is StreamEvent.AssistantDelta -> {
                            lastLine = event.text.trim().takeIf { it.isNotEmpty() } ?: lastLine
                            update(agentId, agentName, lastLine, finished = false)
                        }

                        is StreamEvent.ToolCall -> {
                            lastLine = listOfNotNull(event.name, event.detail).joinToString(" · ")
                            update(agentId, agentName, lastLine, finished = false)
                        }

                        is StreamEvent.Result -> {
                            Notifications.notifyTurnFinished(
                                context = this@RunTrackerService,
                                agentId = agentId,
                                agentName = agentName,
                                status = event.status,
                                summary = event.text,
                            )
                            stopTracking()
                        }

                        is StreamEvent.Done -> stopTracking()
                        else -> Unit
                    }
                }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWith(agentId: String, agentName: String, line: String) {
        val notification = Notifications.trackingNotification(
            context = this,
            agentId = agentId,
            agentName = agentName,
            line = line,
            finished = false,
        )
        ServiceCompat.startForeground(
            this,
            Notifications.TRACKING_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun update(agentId: String, agentName: String, line: String, finished: Boolean) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(
            Notifications.TRACKING_NOTIFICATION_ID,
            Notifications.trackingNotification(this, agentId, agentName, line, finished),
        )
    }

    private fun stopTracking() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val EXTRA_AGENT_ID = "agentId"
        private const val EXTRA_RUN_ID = "runId"
        private const val EXTRA_AGENT_NAME = "agentName"

        fun start(context: Context, agentId: String, runId: String, agentName: String) {
            val intent = Intent(context, RunTrackerService::class.java).apply {
                putExtra(EXTRA_AGENT_ID, agentId)
                putExtra(EXTRA_RUN_ID, runId)
                putExtra(EXTRA_AGENT_NAME, agentName)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RunTrackerService::class.java))
        }
    }
}
