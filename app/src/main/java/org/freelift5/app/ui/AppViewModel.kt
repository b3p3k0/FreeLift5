package org.freelift5.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.freelift5.app.FreeLiftApplication
import org.freelift5.app.data.AccessorySummary
import org.freelift5.app.data.AppSettings
import org.freelift5.app.data.BodyMeasurementEntity
import org.freelift5.app.data.CoreSlotSummary
import org.freelift5.app.data.ExerciseEntity
import org.freelift5.app.data.ExerciseProgressPoint
import org.freelift5.app.data.WorkoutSessionWithExercises
import org.freelift5.app.domain.BuiltInPrograms
import org.freelift5.app.domain.CoreSlot
import org.freelift5.app.domain.ProgramDefinition
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.reminders.ReminderScheduler
import org.freelift5.app.theme.AppThemeId
import org.freelift5.app.theme.ThemeBehavior
import org.freelift5.app.timer.RestTimerService
import org.freelift5.app.timer.TimerStateStore

data class AppUiState(
    val ready: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val coreProgram: List<CoreSlotSummary> = emptyList(),
    val accessories: List<AccessorySummary> = emptyList(),
    val activeProgram: ProgramDefinition = BuiltInPrograms.byId(BuiltInPrograms.DEFAULT_ID),
    val activeWorkout: WorkoutSessionWithExercises? = null,
    val history: List<WorkoutSessionWithExercises> = emptyList(),
    val measurements: List<BodyMeasurementEntity> = emptyList(),
    val exerciseProgress: List<ExerciseProgressPoint> = emptyList(),
)

private data class ProgramState(
    val settings: AppSettings,
    val exercises: List<ExerciseEntity>,
    val coreProgram: List<CoreSlotSummary>,
    val accessories: List<AccessorySummary>,
)

private data class TrainingState(
    val activeWorkout: WorkoutSessionWithExercises?,
    val history: List<WorkoutSessionWithExercises>,
    val measurements: List<BodyMeasurementEntity>,
    val exerciseProgress: List<ExerciseProgressPoint>,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FreeLiftApplication
    private val repository = app.container.repository
    private val timerStore = TimerStateStore(application)
    private val reminderScheduler = ReminderScheduler(application)

    val message = MutableStateFlow<String?>(null)

    private val programState = combine(
        repository.settings,
        repository.exercises,
        repository.coreProgram,
        repository.accessories,
        ::ProgramState,
    )
    private val trainingState = combine(
        repository.activeWorkout,
        repository.history,
        repository.measurements,
        repository.exerciseProgress,
        ::TrainingState,
    )

    val uiState: StateFlow<AppUiState> = combine(
        programState,
        trainingState,
    ) { program, training ->
        AppUiState(
            ready = true,
            settings = program.settings,
            exercises = program.exercises,
            coreProgram = program.coreProgram,
            accessories = program.accessories,
            activeProgram = BuiltInPrograms.byId(program.settings.activeProgramId),
            activeWorkout = training.activeWorkout,
            history = training.history,
            measurements = training.measurements,
            exerciseProgress = training.exerciseProgress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    fun dismissMessage() {
        message.value = null
    }

    fun completeOnboarding(
        unitSystem: UnitSystem,
        birthMonth: Int?,
        birthYear: Int?,
        heightMillimeters: Int?,
        trainingBackground: String,
        bodyWeightGrams: Long?,
        barWeightGrams: Long,
        startingWeights: Map<CoreSlot, Long>,
        programId: String,
    ) = launchAction {
        repository.completeOnboarding(
            unitSystem = unitSystem,
            birthMonth = birthMonth,
            birthYear = birthYear,
            heightMillimeters = heightMillimeters,
            trainingBackground = trainingBackground,
            bodyWeightGrams = bodyWeightGrams,
            barWeightGrams = barWeightGrams,
            startingWeights = startingWeights,
            programId = programId,
        )
    }

    fun startWorkout() = launchAction {
        repository.startWorkout()
    }

    fun saveSet(
        exerciseSessionId: Long,
        setNumber: Int,
        actualReps: Int,
        actualWeightGrams: Long,
        targetReps: Int,
        successfulRestSeconds: Int,
        failedRestSeconds: Int,
    ) = launchAction {
        repository.saveSet(
            exerciseSessionId = exerciseSessionId,
            setNumber = setNumber,
            actualReps = actualReps,
            actualWeightGrams = actualWeightGrams,
        )
        val duration = if (actualReps >= targetReps) {
            successfulRestSeconds
        } else {
            failedRestSeconds
        }
        val settings = uiState.value.settings
        if (settings.backgroundAlertsEnabled) {
            RestTimerService.start(
                context = app,
                durationSeconds = duration,
                soundEnabled = settings.soundEnabled,
                vibrationEnabled = settings.vibrationEnabled,
            )
        } else {
            timerStore.start(duration)
        }
    }

    fun finishWorkout(sessionId: Long, notes: String) = launchAction {
        repository.finishWorkout(sessionId, notes)
        stopTimer()
    }

    fun resolveDeload(slotKey: String, selectedWeightGrams: Long) = launchAction {
        repository.resolveDeload(slotKey, selectedWeightGrams)
    }

    fun adjustTimer(deltaSeconds: Int) {
        if (uiState.value.settings.backgroundAlertsEnabled) {
            RestTimerService.adjust(app, deltaSeconds)
        } else {
            timerStore.adjust(deltaSeconds)
        }
    }

    fun stopTimer() {
        if (uiState.value.settings.backgroundAlertsEnabled) {
            RestTimerService.stop(app)
        } else {
            timerStore.clear()
        }
    }

    fun setBackgroundAlerts(enabled: Boolean) = launchAction {
        val currentSettings = uiState.value.settings
        repository.updateSettings { settings ->
            settings.copy(
                backgroundAlertsAsked = true,
                backgroundAlertsEnabled = enabled,
            )
        }
        if (enabled) {
            timerStore.current()?.takeIf { it.isRunning }?.let { timer ->
                RestTimerService.start(
                    app,
                    timer.remainingSeconds(),
                    currentSettings.soundEnabled,
                    currentSettings.vibrationEnabled,
                )
            }
        } else {
            RestTimerService.detach(app)
        }
    }

    fun markBackgroundAlertsAsked() = updateSettings {
        it.copy(backgroundAlertsAsked = true)
    }

    fun setTimerCue(
        sound: Boolean? = null,
        vibration: Boolean? = null,
        visual: Boolean? = null,
    ) = updateSettings {
        it.copy(
            soundEnabled = sound ?: it.soundEnabled,
            vibrationEnabled = vibration ?: it.vibrationEnabled,
            visualCueEnabled = visual ?: it.visualCueEnabled,
        )
    }

    fun setKeepScreenAwake(enabled: Boolean) = updateSettings {
        it.copy(keepScreenAwake = enabled)
    }

    fun setUnitSystem(unitSystem: UnitSystem) = updateSettings {
        it.copy(unitSystem = unitSystem)
    }

    fun setBarWeight(weightGrams: Long) = updateSettings {
        it.copy(barWeightGrams = weightGrams)
    }

    fun setThemeBehavior(
        behavior: ThemeBehavior,
        activeTheme: AppThemeId,
    ) = updateSettings {
        it.copy(
            themePreferences = it.themePreferences.copy(
                behavior = behavior,
                fixedTheme = if (behavior == ThemeBehavior.FIXED) {
                    activeTheme
                } else {
                    it.themePreferences.fixedTheme
                },
            ),
        )
    }

    fun setTheme(theme: AppThemeId) = updateSettings {
        val preferences = it.themePreferences
        it.copy(
            themePreferences = when (preferences.behavior) {
                ThemeBehavior.FIXED -> preferences.copy(fixedTheme = theme)
                ThemeBehavior.FOLLOW_SYSTEM -> if (theme.isDark) {
                    preferences.copy(darkTheme = theme)
                } else {
                    preferences.copy(lightTheme = theme)
                }
            },
        )
    }

    fun setWorkoutReminders(
        enabled: Boolean,
        days: Set<Int>? = null,
        minutes: Int? = null,
    ) = updateSettings {
        it.copy(
            workoutRemindersEnabled = enabled,
            workoutReminderDays = days ?: it.workoutReminderDays,
            workoutReminderMinutes = minutes ?: it.workoutReminderMinutes,
        )
    }

    fun setBodyReminder(enabled: Boolean, intervalDays: Int? = null) = updateSettings {
        it.copy(
            bodyReminderEnabled = enabled,
            bodyReminderIntervalDays = intervalDays ?: it.bodyReminderIntervalDays,
        )
    }

    fun addMeasurement(weightGrams: Long) = launchAction {
        repository.addMeasurement(weightGrams)
    }

    fun addAccessory(
        name: String,
        trackingMode: TrackingMode,
        workoutTypes: Set<String>,
        sets: Int,
        target: Int,
        startingWeightGrams: Long,
        incrementGrams: Long,
        targetIncrement: Int,
        progressionEverySuccesses: Int,
        restSeconds: Int,
        notes: String,
    ) = launchAction {
        repository.addAccessory(
            name,
            trackingMode,
            workoutTypes,
            sets,
            target,
            startingWeightGrams,
            incrementGrams,
            targetIncrement,
            progressionEverySuccesses,
            restSeconds,
            notes,
        )
    }

    fun deleteAccessories(ids: List<String>) = launchAction {
        repository.deleteAccessories(ids)
    }

    fun createAndUseAdaptation(
        slotKey: String,
        name: String,
        notes: String,
    ) = launchAction {
        val id = repository.createCustomExercise(name, TrackingMode.WEIGHT, notes)
        repository.replaceCoreExercise(slotKey, id)
    }

    fun useExistingAdaptation(slotKey: String, exerciseId: String) = launchAction {
        repository.replaceCoreExercise(slotKey, exerciseId)
    }

    fun updateCoreSettings(
        slotKey: String,
        sets: Int,
        reps: Int,
        currentWeightGrams: Long,
        incrementGrams: Long,
        successfulRestSeconds: Int,
        failedRestSeconds: Int,
    ) = launchAction {
        repository.updateCoreSettings(
            slotKey,
            sets,
            reps,
            currentWeightGrams,
            incrementGrams,
            successfulRestSeconds,
            failedRestSeconds,
        )
    }

    fun splitCoreSlot(slotKey: String, dayKey: String) = launchAction {
        repository.splitCoreSlotForWorkout(slotKey, dayKey)
    }

    fun switchProgram(programId: String) = launchAction {
        repository.switchProgram(programId)
    }

    fun clearAllData() = launchAction {
        repository.clearAllData()
        timerStore.clear()
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = launchAction {
        repository.updateSettings(transform)
        reminderScheduler.apply(repository.settingsSnapshot())
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { message.value = it.message ?: "Something went wrong." }
        }
    }
}
