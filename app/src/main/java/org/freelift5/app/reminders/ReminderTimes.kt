package org.freelift5.app.reminders

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ReminderTimes {
    fun nextWorkoutReminder(
        now: ZonedDateTime,
        selectedIsoWeekdays: Set<Int>,
        minutesAfterMidnight: Int,
    ): ZonedDateTime {
        require(selectedIsoWeekdays.isNotEmpty()) { "At least one weekday is required." }
        require(minutesAfterMidnight in 0 until 24 * 60) { "Reminder time is invalid." }

        val hour = minutesAfterMidnight / 60
        val minute = minutesAfterMidnight % 60
        for (daysAhead in 0..7) {
            val candidate = now.plusDays(daysAhead.toLong())
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
            if (
                candidate.dayOfWeek.value in selectedIsoWeekdays &&
                candidate.isAfter(now)
            ) {
                return candidate
            }
        }
        error("Unable to calculate a reminder time.")
    }

    fun delayMillis(now: ZonedDateTime, target: ZonedDateTime): Long =
        ChronoUnit.MILLIS.between(now, target).coerceAtLeast(0L)
}

