package org.freelift5.app.timer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.freelift5.app.MainActivity
import org.freelift5.app.R
import org.freelift5.app.notifications.NotificationChannels

class RestTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var timerStore: TimerStateStore
    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var soundEnabled = true
    private var vibrationEnabled = true

    override fun onCreate() {
        super.onCreate()
        timerStore = TimerStateStore(this)
        NotificationChannels.create(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTimer()
                return START_NOT_STICKY
            }
            ACTION_DETACH -> {
                detachTimer()
                return START_NOT_STICKY
            }
            ACTION_ADJUST -> {
                timerStore.adjust(intent.getIntExtra(EXTRA_DELTA_SECONDS, 0))
                startCountdown()
                return START_STICKY
            }
            ACTION_START -> {
                soundEnabled = intent.getBooleanExtra(EXTRA_SOUND, true)
                vibrationEnabled = intent.getBooleanExtra(EXTRA_VIBRATION, true)
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 180)
                timerStore.start(duration)
                startCountdown()
                return START_STICKY
            }
            else -> {
                if (timerStore.current()?.isRunning == true) {
                    startCountdown()
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCountdown() {
        val state = timerStore.current() ?: return
        val remaining = state.remainingSeconds()
        if (remaining <= 0) {
            completeTimer()
            return
        }

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            TIMER_NOTIFICATION_ID,
            timerNotification(remaining, complete = false),
            serviceType,
        )
        acquireWakeLock(remaining)
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val seconds = timerStore.current()?.remainingSeconds() ?: 0
                if (seconds <= 0) {
                    completeTimer()
                    break
                }
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(TIMER_NOTIFICATION_ID, timerNotification(seconds, complete = false))
                delay(1_000L)
            }
        }
    }

    private fun completeTimer() {
        if (soundEnabled) {
            ToneGenerator(AudioManager.STREAM_ALARM, 80).also { tone ->
                if (tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)) {
                    Handler(Looper.getMainLooper()).postDelayed(tone::release, 500L)
                } else {
                    tone.release()
                }
            }
        }
        if (vibrationEnabled) {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1)
            }
            vibrator()?.vibrate(
                effect,
            )
        }
        getSystemService(android.app.NotificationManager::class.java)
            .notify(TIMER_NOTIFICATION_ID, timerNotification(0, complete = true))
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun stopTimer() {
        countdownJob?.cancel()
        timerStore.clear()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // completeTimer() detaches a persistent "Rest complete" notification; a later
        // stop (Finish workout / Skip) runs in a fresh service where STOP_FOREGROUND_REMOVE
        // has nothing to remove, so cancel it explicitly.
        getSystemService(android.app.NotificationManager::class.java)
            .cancel(TIMER_NOTIFICATION_ID)
        stopSelf()
    }

    private fun detachTimer() {
        countdownJob?.cancel()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun timerNotification(seconds: Int, complete: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RestTimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (complete) {
            "Rest complete"
        } else {
            val minutes = seconds / 60
            val remainder = seconds % 60
            String.format(Locale.US, "%d:%02d remaining", minutes, remainder)
        }
        return NotificationCompat.Builder(this, NotificationChannels.TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_barbell)
            .setContentTitle(if (complete) "Rest complete" else "FreeLift5 rest timer")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(!complete)
            .setOngoing(!complete)
            .setSilent(!complete)
            .addAction(0, if (complete) "Dismiss" else "Stop", stop)
            .build()
    }

    private fun acquireWakeLock(remainingSeconds: Int) {
        releaseWakeLock()
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FreeLift5:RestTimer",
        ).apply {
            acquire((remainingSeconds + 60L) * 1_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
        wakeLock = null
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

    companion object {
        private const val TIMER_NOTIFICATION_ID = 501
        private const val ACTION_START = "org.freelift5.app.timer.START"
        private const val ACTION_STOP = "org.freelift5.app.timer.STOP"
        private const val ACTION_DETACH = "org.freelift5.app.timer.DETACH"
        private const val ACTION_ADJUST = "org.freelift5.app.timer.ADJUST"
        private const val EXTRA_DURATION_SECONDS = "duration_seconds"
        private const val EXTRA_DELTA_SECONDS = "delta_seconds"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_VIBRATION = "vibration"

        fun start(
            context: android.content.Context,
            durationSeconds: Int,
            soundEnabled: Boolean,
            vibrationEnabled: Boolean,
        ) {
            val intent = Intent(context, RestTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                .putExtra(EXTRA_SOUND, soundEnabled)
                .putExtra(EXTRA_VIBRATION, vibrationEnabled)
            ContextCompat.startForegroundService(context, intent)
        }

        fun adjust(context: android.content.Context, deltaSeconds: Int) {
            context.startService(
                Intent(context, RestTimerService::class.java)
                    .setAction(ACTION_ADJUST)
                    .putExtra(EXTRA_DELTA_SECONDS, deltaSeconds),
            )
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_STOP),
            )
        }

        fun detach(context: android.content.Context) {
            context.startService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_DETACH),
            )
        }
    }
}
