package org.freelift5.app.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.freelift5.app.data.AppSettings

class ReminderScheduler(private val context: Context) {
    fun apply(settings: AppSettings) {
        val manager = WorkManager.getInstance(context)
        if (settings.workoutRemindersEnabled && settings.workoutReminderDays.isNotEmpty()) {
            val now = ZonedDateTime.now()
            val next = ReminderTimes.nextWorkoutReminder(
                now,
                settings.workoutReminderDays,
                settings.workoutReminderMinutes,
            )
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(ReminderTimes.delayMillis(now, next), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.KEY_TYPE to ReminderWorker.TYPE_WORKOUT))
                .build()
            manager.enqueueUniquePeriodicWork(
                WORKOUT_WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request,
            )
        } else {
            manager.cancelUniqueWork(WORKOUT_WORK_NAME)
        }

        if (settings.bodyReminderEnabled) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                settings.bodyReminderIntervalDays.coerceAtLeast(1).toLong(),
                TimeUnit.DAYS,
            )
                .setInitialDelay(
                    settings.bodyReminderIntervalDays.toLong(),
                    TimeUnit.DAYS,
                )
                .setInputData(workDataOf(ReminderWorker.KEY_TYPE to ReminderWorker.TYPE_BODY))
                .build()
            manager.enqueueUniquePeriodicWork(
                BODY_WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request,
            )
        } else {
            manager.cancelUniqueWork(BODY_WORK_NAME)
        }
    }

    companion object {
        private const val WORKOUT_WORK_NAME = "workout-reminder"
        private const val BODY_WORK_NAME = "body-weight-reminder"
    }
}
