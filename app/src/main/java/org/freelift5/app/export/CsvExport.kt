package org.freelift5.app.export

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.freelift5.app.data.AccessorySummary
import org.freelift5.app.data.AppSettings
import org.freelift5.app.data.BodyMeasurementEntity
import org.freelift5.app.data.CoreSlotSummary
import org.freelift5.app.data.ExerciseEntity
import org.freelift5.app.data.WorkoutSessionWithExercises

object CsvExport {
    const val SCHEMA_VERSION = 2

    fun workouts(sessions: List<WorkoutSessionWithExercises>): String = buildString {
        appendLine(
            listOf(
                "schema_version",
                "session_id",
                "workout",
                "started_at_epoch_ms",
                "completed_at_epoch_ms",
                "status",
                "exercise_id",
                "exercise",
                "tracking_mode",
                "core_slot",
                "set_number",
                "set_type",
                "target_reps",
                "actual_reps",
                "target_weight_grams",
                "actual_weight_grams",
                "notes",
            ).joinToString(","),
        )
        sessions.forEach { workout ->
            workout.exercises.sortedBy { it.exercise.orderIndex }.forEach { relation ->
                relation.sets.sortedBy { it.setNumber }.forEach { set ->
                    appendLine(
                        row(
                            SCHEMA_VERSION,
                            workout.session.id,
                            workout.session.workoutType,
                            workout.session.startedAtEpochMillis,
                            workout.session.completedAtEpochMillis ?: "",
                            workout.session.status,
                            relation.exercise.exerciseId,
                            relation.exercise.exerciseName,
                            relation.exercise.trackingMode,
                            relation.exercise.coreSlotKey ?: "",
                            set.setNumber,
                            if (set.isWarmup) "WARMUP" else "WORK",
                            set.targetReps,
                            set.actualReps,
                            set.targetWeightGrams,
                            set.actualWeightGrams,
                            workout.session.notes,
                        ),
                    )
                }
            }
        }
    }

    fun measurements(measurements: List<BodyMeasurementEntity>): String = buildString {
        appendLine("schema_version,id,recorded_at_epoch_ms,body_weight_grams")
        measurements.forEach { measurement ->
            appendLine(
                row(
                    SCHEMA_VERSION,
                    measurement.id,
                    measurement.recordedAtEpochMillis,
                    measurement.bodyWeightGrams,
                ),
            )
        }
    }

    fun sessions(sessions: List<WorkoutSessionWithExercises>): String = buildString {
        appendLine(
            "schema_version,session_id,workout,started_at_epoch_ms," +
                "completed_at_epoch_ms,status,notes",
        )
        sessions.forEach { workout ->
            appendLine(
                row(
                    SCHEMA_VERSION,
                    workout.session.id,
                    workout.session.workoutType,
                    workout.session.startedAtEpochMillis,
                    workout.session.completedAtEpochMillis ?: "",
                    workout.session.status,
                    workout.session.notes,
                ),
            )
        }
    }

    fun exerciseSessions(sessions: List<WorkoutSessionWithExercises>): String = buildString {
        appendLine(
            "schema_version,session_id,exercise_session_id,exercise_id,exercise," +
                "tracking_mode,core_slot,accessory_assignment,order_index,target_sets," +
                "target_reps,target_weight_grams,increment_grams,target_increment," +
                "success_rest_seconds,failed_rest_seconds,progression_action," +
                "next_weight_grams,next_target",
        )
        sessions.forEach { workout ->
            workout.exercises.sortedBy { it.exercise.orderIndex }.forEach { relation ->
                val exercise = relation.exercise
                appendLine(
                    row(
                        SCHEMA_VERSION,
                        workout.session.id,
                        exercise.id,
                        exercise.exerciseId,
                        exercise.exerciseName,
                        exercise.trackingMode,
                        exercise.coreSlotKey ?: "",
                        exercise.accessoryAssignmentId ?: "",
                        exercise.orderIndex,
                        exercise.targetSets,
                        exercise.targetReps,
                        exercise.targetWeightGrams,
                        exercise.incrementGrams,
                        exercise.targetIncrement,
                        exercise.successfulRestSeconds,
                        exercise.failedRestSeconds,
                        exercise.progressionAction ?: "",
                        exercise.nextWeightGrams ?: "",
                        exercise.nextTarget ?: "",
                    ),
                )
            }
        }
    }

    fun exercises(exercises: List<ExerciseEntity>): String = buildString {
        appendLine("schema_version,id,name,tracking_mode,built_in_slot,notes,archived")
        exercises.forEach { exercise ->
            appendLine(
                row(
                    SCHEMA_VERSION,
                    exercise.id,
                    exercise.name,
                    exercise.trackingMode,
                    exercise.builtInSlot ?: "",
                    exercise.notes,
                    exercise.archived,
                ),
            )
        }
    }

    fun coreProgram(coreProgram: List<CoreSlotSummary>): String = buildString {
        appendLine(
            "schema_version,slot_key,canonical_slot,exercise_id,exercise,workout," +
                "order_index,sets,reps,current_weight_grams,increment_grams," +
                "consecutive_failures,pending_deload_grams,success_rest_seconds," +
                "failed_rest_seconds",
        )
        coreProgram.forEach { slot ->
            appendLine(
                row(
                    SCHEMA_VERSION,
                    slot.slotKey,
                    slot.canonicalSlot,
                    slot.exerciseId,
                    slot.exerciseName,
                    slot.workoutType,
                    slot.orderIndex,
                    slot.sets,
                    slot.reps,
                    slot.currentWeightGrams,
                    slot.incrementGrams,
                    slot.consecutiveFailures,
                    slot.pendingDeloadGrams ?: "",
                    slot.successfulRestSeconds,
                    slot.failedRestSeconds,
                ),
            )
        }
    }

    fun accessories(accessories: List<AccessorySummary>): String = buildString {
        appendLine(
            "schema_version,assignment_id,workout,exercise_id,exercise,tracking_mode," +
                "notes,order_index,sets,target,current_weight_grams,increment_grams," +
                "target_increment,progression_every_successes,successful_sessions," +
                "rest_seconds",
        )
        accessories.forEach { accessory ->
            appendLine(
                row(
                    SCHEMA_VERSION,
                    accessory.assignmentId,
                    accessory.workoutType,
                    accessory.exerciseId,
                    accessory.exerciseName,
                    accessory.trackingMode,
                    accessory.notes,
                    accessory.orderIndex,
                    accessory.sets,
                    accessory.target,
                    accessory.currentWeightGrams,
                    accessory.incrementGrams,
                    accessory.targetIncrement,
                    accessory.progressionEverySuccesses,
                    accessory.successfulSessions,
                    accessory.restSeconds,
                ),
            )
        }
    }

    fun settings(settings: AppSettings): String = buildString {
        appendLine(
            "schema_version,unit_system,birth_month,birth_year,height_mm," +
                "training_background,bar_weight_grams,next_workout,sound_enabled," +
                "vibration_enabled,visual_cue_enabled,keep_screen_awake," +
                "background_alerts_enabled,workout_reminders_enabled," +
                "workout_reminder_days,workout_reminder_minutes," +
                "body_reminder_enabled,body_reminder_interval_days",
        )
        appendLine(
            row(
                SCHEMA_VERSION,
                settings.unitSystem,
                settings.birthMonth ?: "",
                settings.birthYear ?: "",
                settings.heightMillimeters ?: "",
                settings.trainingBackground,
                settings.barWeightGrams,
                settings.nextWorkoutDayKey,
                settings.soundEnabled,
                settings.vibrationEnabled,
                settings.visualCueEnabled,
                settings.keepScreenAwake,
                settings.backgroundAlertsEnabled,
                settings.workoutRemindersEnabled,
                settings.workoutReminderDays.sorted().joinToString(";"),
                settings.workoutReminderMinutes,
                settings.bodyReminderEnabled,
                settings.bodyReminderIntervalDays,
            ),
        )
    }

    fun completeBundle(
        workoutSessions: List<WorkoutSessionWithExercises>,
        measurements: List<BodyMeasurementEntity>,
        appSettings: AppSettings? = null,
        exerciseDefinitions: List<ExerciseEntity> = emptyList(),
        coreSlots: List<CoreSlotSummary> = emptyList(),
        accessoryAssignments: List<AccessorySummary> = emptyList(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeEntry("workouts-v$SCHEMA_VERSION.csv", workouts(workoutSessions))
            zip.writeEntry("sessions-v$SCHEMA_VERSION.csv", sessions(workoutSessions))
            zip.writeEntry(
                "exercise-sessions-v$SCHEMA_VERSION.csv",
                exerciseSessions(workoutSessions),
            )
            zip.writeEntry(
                "measurements-v$SCHEMA_VERSION.csv",
                measurements(measurements),
            )
            zip.writeEntry(
                "exercises-v$SCHEMA_VERSION.csv",
                exercises(exerciseDefinitions),
            )
            zip.writeEntry("core-program-v$SCHEMA_VERSION.csv", coreProgram(coreSlots))
            zip.writeEntry(
                "accessories-v$SCHEMA_VERSION.csv",
                accessories(accessoryAssignments),
            )
            appSettings?.let {
                zip.writeEntry("settings-v$SCHEMA_VERSION.csv", settings(it))
            }
        }
        return output.toByteArray()
    }

    fun escape(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    private fun row(vararg values: Any?): String = values.joinToString(",") { escape(it) }

    private fun ZipOutputStream.writeEntry(name: String, contents: String) {
        putNextEntry(ZipEntry(name))
        write(contents.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }
}
