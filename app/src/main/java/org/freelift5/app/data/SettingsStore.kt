package org.freelift5.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WorkoutType

private val Context.freeLiftDataStore by preferencesDataStore(name = "freelift_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.POUNDS,
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val heightMillimeters: Int? = null,
    val trainingBackground: String = "NEW",
    val barWeightGrams: Long = 20_411L,
    val nextWorkout: WorkoutType = WorkoutType.A,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val visualCueEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val backgroundAlertsAsked: Boolean = false,
    val backgroundAlertsEnabled: Boolean = false,
    val workoutRemindersEnabled: Boolean = false,
    val workoutReminderDays: Set<Int> = setOf(1, 3, 5),
    val workoutReminderMinutes: Int = 18 * 60,
    val bodyReminderEnabled: Boolean = false,
    val bodyReminderIntervalDays: Int = 14,
)

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.freeLiftDataStore.data.map(::toSettings)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.freeLiftDataStore.edit { preferences ->
            writeSettings(preferences, transform(toSettings(preferences)))
        }
    }

    suspend fun clear() {
        context.freeLiftDataStore.edit { it.clear() }
    }

    private fun toSettings(preferences: Preferences): AppSettings = AppSettings(
        onboardingComplete = preferences[Keys.ONBOARDING_COMPLETE] ?: false,
        unitSystem = runCatching {
            UnitSystem.valueOf(preferences[Keys.UNIT_SYSTEM] ?: UnitSystem.POUNDS.name)
        }.getOrDefault(UnitSystem.POUNDS),
        birthMonth = preferences[Keys.BIRTH_MONTH]?.takeIf { it > 0 },
        birthYear = preferences[Keys.BIRTH_YEAR]?.takeIf { it > 0 },
        heightMillimeters = preferences[Keys.HEIGHT_MM]?.takeIf { it > 0 },
        trainingBackground = preferences[Keys.TRAINING_BACKGROUND] ?: "NEW",
        barWeightGrams = preferences[Keys.BAR_WEIGHT_GRAMS] ?: 20_411L,
        nextWorkout = runCatching {
            WorkoutType.valueOf(preferences[Keys.NEXT_WORKOUT] ?: WorkoutType.A.name)
        }.getOrDefault(WorkoutType.A),
        soundEnabled = preferences[Keys.SOUND_ENABLED] ?: true,
        vibrationEnabled = preferences[Keys.VIBRATION_ENABLED] ?: true,
        visualCueEnabled = preferences[Keys.VISUAL_CUE_ENABLED] ?: true,
        keepScreenAwake = preferences[Keys.KEEP_SCREEN_AWAKE] ?: true,
        backgroundAlertsAsked = preferences[Keys.BACKGROUND_ALERTS_ASKED] ?: false,
        backgroundAlertsEnabled = preferences[Keys.BACKGROUND_ALERTS_ENABLED] ?: false,
        workoutRemindersEnabled = preferences[Keys.WORKOUT_REMINDERS_ENABLED] ?: false,
        workoutReminderDays = (preferences[Keys.WORKOUT_REMINDER_DAYS] ?: "1,3,5")
            .split(',')
            .mapNotNull(String::toIntOrNull)
            .toSet(),
        workoutReminderMinutes = preferences[Keys.WORKOUT_REMINDER_MINUTES] ?: 18 * 60,
        bodyReminderEnabled = preferences[Keys.BODY_REMINDER_ENABLED] ?: false,
        bodyReminderIntervalDays = preferences[Keys.BODY_REMINDER_INTERVAL] ?: 14,
    )

    private fun writeSettings(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        settings: AppSettings,
    ) {
        preferences[Keys.ONBOARDING_COMPLETE] = settings.onboardingComplete
        preferences[Keys.UNIT_SYSTEM] = settings.unitSystem.name
        settings.birthMonth?.let { preferences[Keys.BIRTH_MONTH] = it }
            ?: preferences.remove(Keys.BIRTH_MONTH)
        settings.birthYear?.let { preferences[Keys.BIRTH_YEAR] = it }
            ?: preferences.remove(Keys.BIRTH_YEAR)
        settings.heightMillimeters?.let { preferences[Keys.HEIGHT_MM] = it }
            ?: preferences.remove(Keys.HEIGHT_MM)
        preferences[Keys.TRAINING_BACKGROUND] = settings.trainingBackground
        preferences[Keys.BAR_WEIGHT_GRAMS] = settings.barWeightGrams
        preferences[Keys.NEXT_WORKOUT] = settings.nextWorkout.name
        preferences[Keys.SOUND_ENABLED] = settings.soundEnabled
        preferences[Keys.VIBRATION_ENABLED] = settings.vibrationEnabled
        preferences[Keys.VISUAL_CUE_ENABLED] = settings.visualCueEnabled
        preferences[Keys.KEEP_SCREEN_AWAKE] = settings.keepScreenAwake
        preferences[Keys.BACKGROUND_ALERTS_ASKED] = settings.backgroundAlertsAsked
        preferences[Keys.BACKGROUND_ALERTS_ENABLED] = settings.backgroundAlertsEnabled
        preferences[Keys.WORKOUT_REMINDERS_ENABLED] = settings.workoutRemindersEnabled
        preferences[Keys.WORKOUT_REMINDER_DAYS] =
            settings.workoutReminderDays.sorted().joinToString(",")
        preferences[Keys.WORKOUT_REMINDER_MINUTES] = settings.workoutReminderMinutes
        preferences[Keys.BODY_REMINDER_ENABLED] = settings.bodyReminderEnabled
        preferences[Keys.BODY_REMINDER_INTERVAL] = settings.bodyReminderIntervalDays
    }

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val BIRTH_MONTH = intPreferencesKey("birth_month")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val HEIGHT_MM = intPreferencesKey("height_mm")
        val TRAINING_BACKGROUND = stringPreferencesKey("training_background")
        val BAR_WEIGHT_GRAMS = longPreferencesKey("bar_weight_grams")
        val NEXT_WORKOUT = stringPreferencesKey("next_workout")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val VISUAL_CUE_ENABLED = booleanPreferencesKey("visual_cue_enabled")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val BACKGROUND_ALERTS_ASKED = booleanPreferencesKey("background_alerts_asked")
        val BACKGROUND_ALERTS_ENABLED = booleanPreferencesKey("background_alerts_enabled")
        val WORKOUT_REMINDERS_ENABLED = booleanPreferencesKey("workout_reminders_enabled")
        val WORKOUT_REMINDER_DAYS = stringPreferencesKey("workout_reminder_days")
        val WORKOUT_REMINDER_MINUTES = intPreferencesKey("workout_reminder_minutes")
        val BODY_REMINDER_ENABLED = booleanPreferencesKey("body_reminder_enabled")
        val BODY_REMINDER_INTERVAL = intPreferencesKey("body_reminder_interval")
    }
}
