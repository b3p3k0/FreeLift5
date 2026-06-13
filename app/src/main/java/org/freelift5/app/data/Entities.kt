package org.freelift5.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trackingMode: String,
    val builtInSlot: String?,
    val notes: String = "",
    val archived: Boolean = false,
)

@Entity(
    tableName = "core_slots",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class CoreSlotEntity(
    @PrimaryKey val slotKey: String,
    val canonicalSlot: String,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val incrementGrams: Long,
    val currentWeightGrams: Long,
    val consecutiveFailures: Int = 0,
    val pendingDeloadGrams: Long? = null,
    val successfulRestSeconds: Int = 180,
    val failedRestSeconds: Int = 300,
)

@Entity(
    tableName = "workout_core_slots",
    foreignKeys = [
        ForeignKey(
            entity = CoreSlotEntity::class,
            parentColumns = ["slotKey"],
            childColumns = ["slotKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("slotKey"),
        Index(value = ["workoutType", "orderIndex"], unique = true),
    ],
)
data class WorkoutCoreSlotEntity(
    @PrimaryKey val id: String,
    val workoutType: String,
    val slotKey: String,
    val orderIndex: Int,
)

@Entity(
    tableName = "accessory_assignments",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("exerciseId"),
        Index(value = ["workoutType", "orderIndex"]),
    ],
)
data class AccessoryAssignmentEntity(
    @PrimaryKey val id: String,
    val workoutType: String,
    val exerciseId: String,
    val orderIndex: Int,
    val sets: Int,
    val target: Int,
    val currentWeightGrams: Long,
    val incrementGrams: Long,
    @ColumnInfo(defaultValue = "1") val targetIncrement: Int = 1,
    val progressionEverySuccesses: Int = 1,
    val successfulSessions: Int = 0,
    val restSeconds: Int = 180,
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutType: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val status: String,
    val notes: String = "",
)

@Entity(
    tableName = "exercise_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workoutSessionId"), Index("exerciseId"), Index("coreSlotKey")],
)
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutSessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    @ColumnInfo(defaultValue = "'WEIGHT'") val trackingMode: String = "WEIGHT",
    val coreSlotKey: String?,
    val accessoryAssignmentId: String?,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightGrams: Long,
    val incrementGrams: Long,
    @ColumnInfo(defaultValue = "1") val targetIncrement: Int = 1,
    val successfulRestSeconds: Int,
    val failedRestSeconds: Int,
    val progressionAction: String? = null,
    val nextWeightGrams: Long? = null,
    val nextTarget: Int? = null,
)

@Entity(
    tableName = "set_records",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("exerciseSessionId"),
        Index(value = ["exerciseSessionId", "setNumber", "isWarmup"], unique = true),
    ],
)
data class SetRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseSessionId: Long,
    val setNumber: Int,
    val isWarmup: Boolean,
    val targetReps: Int,
    val actualReps: Int,
    val targetWeightGrams: Long,
    val actualWeightGrams: Long,
    val completedAtEpochMillis: Long,
)

@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAtEpochMillis: Long,
    val bodyWeightGrams: Long,
)

data class CoreSlotSummary(
    val slotKey: String,
    val canonicalSlot: String,
    val exerciseId: String,
    val exerciseName: String,
    val workoutType: String,
    val orderIndex: Int,
    val sets: Int,
    val reps: Int,
    val incrementGrams: Long,
    val currentWeightGrams: Long,
    val consecutiveFailures: Int,
    val pendingDeloadGrams: Long?,
    val successfulRestSeconds: Int,
    val failedRestSeconds: Int,
)

data class AccessorySummary(
    val assignmentId: String,
    val workoutType: String,
    val exerciseId: String,
    val exerciseName: String,
    val trackingMode: String,
    val notes: String,
    val orderIndex: Int,
    val sets: Int,
    val target: Int,
    val currentWeightGrams: Long,
    val incrementGrams: Long,
    val targetIncrement: Int,
    val progressionEverySuccesses: Int,
    val successfulSessions: Int,
    val restSeconds: Int,
)

data class ExerciseProgressPoint(
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val trackingMode: String,
    val completedAtEpochMillis: Long,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightGrams: Long,
    val progressionAction: String?,
)
