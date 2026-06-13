package org.freelift5.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import org.freelift5.app.FreeLiftApplication
import org.freelift5.app.MainActivity
import org.freelift5.app.R
import org.freelift5.app.notifications.NotificationChannels

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: return Result.failure()
        val app = applicationContext as FreeLiftApplication
        val settings = app.container.settingsStore.settings.first()
        val enabled = when (type) {
            TYPE_BODY -> settings.bodyReminderEnabled
            TYPE_WORKOUT ->
                settings.workoutRemindersEnabled &&
                    ZonedDateTime.now().dayOfWeek.value in settings.workoutReminderDays
            else -> false
        }
        if (!enabled) return Result.success()

        NotificationChannels.create(applicationContext)
        if (
            android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                Intent(applicationContext, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val (title, text) = if (type == TYPE_BODY) {
                "Body-weight check-in" to "Log a measurement when it is convenient."
            } else {
                "FreeLift5 workout" to "Your next 5x5 workout is ready."
            }
            NotificationManagerCompat.from(applicationContext).notify(
                if (type == TYPE_BODY) 602 else 601,
                NotificationCompat.Builder(
                    applicationContext,
                    NotificationChannels.REMINDER_CHANNEL_ID,
                )
                    .setSmallIcon(R.drawable.ic_notification_barbell)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }

        return Result.success()
    }

    companion object {
        const val KEY_TYPE = "reminder_type"
        const val TYPE_WORKOUT = "workout"
        const val TYPE_BODY = "body"
    }
}
