package org.freelift5.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.freelift5.app.data.AppContainer
import org.freelift5.app.notifications.NotificationChannels

class FreeLiftApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.repository.seedBuiltInExercises()
        }
    }
}

