package dev.agentsforcursor

import android.app.Application
import dev.agentsforcursor.notify.Notifications
import dev.agentsforcursor.notify.TurnWatchWorker

class AgentsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        TurnWatchWorker.ensureScheduled(this)
    }
}
