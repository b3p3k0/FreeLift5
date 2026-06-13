package org.freelift5.app.domain

enum class UnitSystem {
    POUNDS,
    KILOGRAMS,
}

enum class WorkoutType {
    A,
    B;

    fun next(): WorkoutType = if (this == A) B else A
}

enum class CoreSlot {
    SQUAT,
    BENCH_PRESS,
    BARBELL_ROW,
    OVERHEAD_PRESS,
    DEADLIFT,
}

enum class TrackingMode {
    WEIGHT,
    BODYWEIGHT,
    REPETITIONS,
    TIME,
}

data class ExerciseDefinition(
    val id: String,
    val name: String,
    val trackingMode: TrackingMode,
    val builtInSlot: CoreSlot? = null,
    val notes: String = "",
)

data class ExercisePrescription(
    val sets: Int,
    val reps: Int,
    val incrementGrams: Long,
    val successfulRestSeconds: Int = 180,
    val failedRestSeconds: Int = 300,
)

data class CoreAssignment(
    val slot: CoreSlot,
    val workoutType: WorkoutType,
    val exercise: ExerciseDefinition,
    val prescription: ExercisePrescription,
    val targetWeightGrams: Long,
)

data class SetPerformance(
    val setNumber: Int,
    val targetReps: Int,
    val actualReps: Int,
    val targetWeightGrams: Long,
    val actualWeightGrams: Long,
)

data class ProgressionState(
    val currentWeightGrams: Long,
    val consecutiveFailures: Int = 0,
)

enum class ProgressionAction {
    INCREASE,
    REPEAT,
    DELOAD_SUGGESTED,
    RETAIN_INCOMPLETE,
}

data class ProgressionDecision(
    val action: ProgressionAction,
    val nextWeightGrams: Long,
    val consecutiveFailures: Int,
    val suggestedDeloadGrams: Long? = null,
    val reason: String,
)

data class PlateLoad(
    val plateGramsPerSide: List<Long>,
    val totalWeightGrams: Long,
)

data class PlateCalculation(
    val exact: PlateLoad?,
    val nearestLower: PlateLoad?,
    val nearestHigher: PlateLoad?,
)

data class WarmupSet(
    val weightGrams: Long,
    val reps: Int,
)

