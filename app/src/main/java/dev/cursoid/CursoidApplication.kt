package dev.cursoid

import android.app.Application
import dev.cursoid.notify.Notifications
import dev.cursoid.notify.TurnWatchWorker

class CursoidApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        TurnWatchWorker.ensureScheduled(this)
    }
}
