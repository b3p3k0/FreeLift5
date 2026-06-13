package org.freelift5.app.reminders

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimesTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun selectsLaterTimeOnSameChosenDay() {
        val now = ZonedDateTime.of(2026, 6, 12, 9, 0, 0, 0, zone)

        val next = ReminderTimes.nextWorkoutReminder(
            now = now,
            selectedIsoWeekdays = setOf(5),
            minutesAfterMidnight = 18 * 60,
        )

        assertEquals(ZonedDateTime.of(2026, 6, 12, 18, 0, 0, 0, zone), next)
    }

    @Test
    fun rollsToNextChosenDayWhenTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 6, 12, 20, 0, 0, 0, zone)

        val next = ReminderTimes.nextWorkoutReminder(
            now = now,
            selectedIsoWeekdays = setOf(1, 3, 5),
            minutesAfterMidnight = 18 * 60,
        )

        assertEquals(ZonedDateTime.of(2026, 6, 15, 18, 0, 0, 0, zone), next)
    }
}

