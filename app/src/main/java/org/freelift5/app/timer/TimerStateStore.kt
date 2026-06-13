package org.freelift5.app.timer

import android.content.Context
import androidx.core.content.edit

data class RestTimerState(
    val endAtEpochMillis: Long,
    val startedAtEpochMillis: Long,
) {
    val isRunning: Boolean
        get() = endAtEpochMillis > System.currentTimeMillis()

    fun remainingSeconds(nowEpochMillis: Long = System.currentTimeMillis()): Int {
        val remainingMillis = (endAtEpochMillis - nowEpochMillis).coerceAtLeast(0L)
        return ((remainingMillis + 999L) / 1_000L).toInt()
    }
}

class TimerStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    fun current(): RestTimerState? {
        val end = preferences.getLong(KEY_END, 0L)
        val start = preferences.getLong(KEY_START, 0L)
        return if (end > 0L && start > 0L) RestTimerState(end, start) else null
    }

    fun start(durationSeconds: Int): RestTimerState {
        val now = System.currentTimeMillis()
        val state = RestTimerState(
            endAtEpochMillis = now + durationSeconds.coerceAtLeast(0) * 1_000L,
            startedAtEpochMillis = now,
        )
        save(state)
        return state
    }

    fun adjust(deltaSeconds: Int): RestTimerState? {
        val current = current() ?: return null
        val adjusted = current.copy(
            endAtEpochMillis = maxOf(
                System.currentTimeMillis(),
                current.endAtEpochMillis + deltaSeconds * 1_000L,
            ),
        )
        save(adjusted)
        return adjusted
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private fun save(state: RestTimerState) {
        preferences.edit {
            putLong(KEY_END, state.endAtEpochMillis)
            putLong(KEY_START, state.startedAtEpochMillis)
        }
    }

    private companion object {
        const val KEY_END = "end_at"
        const val KEY_START = "started_at"
    }
}
