package org.freelift5.app.timer

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.freelift5.app.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestTimerServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = TimerStateStore(context)

    @Before
    fun setUp() {
        store.clear()
    }

    @After
    fun tearDown() {
        wakeDevice()
        RestTimerService.stop(context)
        store.clear()
    }

    @Test
    fun foregroundTimerSurvivesScreenOffWithoutNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertEquals(
                PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            RestTimerService.start(
                context = context,
                durationSeconds = 30,
                soundEnabled = false,
                vibrationEnabled = false,
            )
            waitUntil { store.current()?.isRunning == true }
            val before = store.current()
            assertNotNull(before)

            shell("input keyevent 223")
            Thread.sleep(1_500L)

            val after = store.current()
            assertNotNull(after)
            assertTrue(after!!.remainingSeconds() in 25..29)
            assertTrue(isTimerServiceRunning())
        }
    }

    private fun isTimerServiceRunning(): Boolean {
        @Suppress("DEPRECATION")
        return context.getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == RestTimerService::class.java.name }
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(50) {
            if (condition()) return
            Thread.sleep(100L)
        }
        throw AssertionError("Timed out waiting for the foreground timer")
    }

    private fun wakeDevice() {
        shell("input keyevent 224")
        shell("input keyevent 82")
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .close()
    }
}
