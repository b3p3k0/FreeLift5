package org.freelift5.app.domain

object RoutineEngine {
    private const val CORE_INCREMENT_POUNDS = 5.0
    private const val CORE_INCREMENT_KILOGRAMS = 2.5
    private const val DEADLIFT_INCREMENT_POUNDS = 10.0
    private const val DEADLIFT_INCREMENT_KILOGRAMS = 5.0

    val builtInExercises: Map<CoreSlot, ExerciseDefinition> = CoreSlot.entries.associateWith { slot ->
        ExerciseDefinition(
            id = "core_${slot.name.lowercase()}",
            name = when (slot) {
                CoreSlot.SQUAT -> "Back Squat"
                CoreSlot.BENCH_PRESS -> "Bench Press"
                CoreSlot.BARBELL_ROW -> "Barbell Row"
                CoreSlot.OVERHEAD_PRESS -> "Overhead Press"
                CoreSlot.DEADLIFT -> "Deadlift"
            },
            trackingMode = TrackingMode.WEIGHT,
            builtInSlot = slot,
        )
    }

    fun defaultPrescription(
        slot: CoreSlot,
        unitSystem: UnitSystem,
    ): ExercisePrescription {
        val increment = when {
            slot == CoreSlot.DEADLIFT && unitSystem == UnitSystem.POUNDS ->
                DEADLIFT_INCREMENT_POUNDS
            slot == CoreSlot.DEADLIFT && unitSystem == UnitSystem.KILOGRAMS ->
                DEADLIFT_INCREMENT_KILOGRAMS
            unitSystem == UnitSystem.POUNDS -> CORE_INCREMENT_POUNDS
            else -> CORE_INCREMENT_KILOGRAMS
        }
        return ExercisePrescription(
            sets = if (slot == CoreSlot.DEADLIFT) 1 else 5,
            reps = 5,
            incrementGrams = WeightMath.toGrams(increment, unitSystem),
        )
    }
}

