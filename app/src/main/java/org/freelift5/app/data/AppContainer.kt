package org.freelift5.app.data

import android.content.Context

class AppContainer(context: Context) {
    val database: FreeLiftDatabase = FreeLiftDatabase.create(context)
    val settingsStore = SettingsStore(context)
    val repository = FreeLiftRepository(database, settingsStore)
}

