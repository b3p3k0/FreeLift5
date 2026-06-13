package org.freelift5.app.timer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = TimerStateStore(context)

    @Before
    fun setUp() {
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun timerSurvivesStoreRecreationAndCanBeAdjustedAndCleared() {
        val started = store.start(180)
        assertEquals(180, started.remainingSeconds(started.startedAtEpochMillis))

        val restored = TimerStateStore(context).current()
        assertNotNull(restored)
        assertEquals(started.endAtEpochMillis, restored!!.endAtEpochMillis)

        val adjusted = TimerStateStore(context).adjust(30)
        assertNotNull(adjusted)
        assertTrue(adjusted!!.endAtEpochMillis >= started.endAtEpochMillis + 29_000L)

        TimerStateStore(context).clear()
        assertNull(TimerStateStore(context).current())
    }
}
