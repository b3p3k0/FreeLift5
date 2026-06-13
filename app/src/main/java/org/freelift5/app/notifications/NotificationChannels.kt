package org.freelift5.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.freelift5.app.R

object NotificationChannels {
    const val TIMER_CHANNEL_ID = "rest_timers"
    const val REMINDER_CHANNEL_ID = "workout_reminders"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    TIMER_CHANNEL_ID,
                    context.getString(R.string.timer_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Persistent countdowns for user-started rest timers"
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Optional workout and body-weight reminders"
                },
            ),
        )
    }
}
