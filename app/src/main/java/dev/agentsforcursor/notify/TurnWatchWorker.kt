package dev.agentsforcursor.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.agentsforcursor.AgentsApplication
import dev.agentsforcursor.model.AgentStatus
import dev.agentsforcursor.model.RunStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Polls the agent list in the background and posts a notification when an agent that was working
 * has handed control back. The API has no webhooks for user keys yet, so polling is the only
 * option; WorkManager's 15 minute floor keeps it cheap.
 */
class TurnWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? AgentsApplication)?.container ?: return Result.success()
        val settings = container.settings

        if (!settings.notificationsEnabled.first()) return Result.success()
        val session = container.session.value
        if (!session.isSignedIn || session.demoMode) return Result.success()

        val agents = runCatching { container.repository.agents(includeArchived = false).items }
            .getOrElse { return Result.retry() }

        val previous = settings.runSnapshot()
        val current = agents.associate { agent ->
            agent.id to "${agent.status.name}:${agent.latestRunId.orEmpty()}"
        }

        for (agent in agents) {
            val before = previous[agent.id] ?: continue
            val after = current.getValue(agent.id)
            val wasWorking = before.startsWith(AgentStatus.ACTIVE.name)
            val stillWorking = after.startsWith(AgentStatus.ACTIVE.name)
            if (!wasWorking || stillWorking) continue

            val runId = agent.latestRunId
            val run = runId?.let {
                runCatching { container.repository.run(agent.id, it) }.getOrNull()
            }
            Notifications.notifyTurnFinished(
                context = applicationContext,
                agentId = agent.id,
                agentName = agent.name,
                status = run?.status ?: RunStatus.FINISHED,
                summary = run?.result,
            )
        }

        settings.setRunSnapshot(current)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "turn-watch"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<TurnWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
