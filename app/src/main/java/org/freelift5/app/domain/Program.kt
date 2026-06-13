package org.freelift5.app.domain

/**
 * How the work sets of one exercise are laid out. Phase 1 ships only [Straight] —
 * every work set at the same weight, which keeps the existing uniform session
 * snapshot valid. Ramped and top/back-off schemes (Madcow, the optional Lite/Plus/
 * Ultra variant) are the Phase 2 additions this seam exists for.
 */
sealed interface SetScheme {
    /** Number of work sets the user records. */
    val workSets: Int

    /** Rep target shared by every work set. */
    val targetReps: Int

    data class Straight(val sets: Int, val reps: Int) : SetScheme {
        override val workSets: Int get() = sets
        override val targetReps: Int get() = reps
    }
}

/**
 * How a slot's working weight moves between workouts. Phase 1 ships only
 * [LinearPerWorkout], which is the behaviour [ProgressionEngine] already implements.
 * Weekly and Madcow-ramp policies are Phase 2.
 */
sealed interface ProgressionPolicy {
    data class LinearPerWorkout(
        val deloadAfter: Int = 3,
        val deloadFactor: Double = 0.90,
    ) : ProgressionPolicy
}

/**
 * A progressing main lift inside a program day. Increments and default starts are
 * expressed in both units; the loadable gram value is resolved at materialization.
 *
 * [canonicalSlot] is the movement-family key used both for the shared-slot dedupe
 * (e.g. Squat reused across days A and B) and for carrying a working weight across a
 * program switch. For the five barbell lifts it is the [CoreSlot] name; new movements
 * use their own stable string.
 */
data class SlotDef(
    val canonicalSlot: String,
    val exerciseId: String,
    val setScheme: SetScheme,
    val incrementPounds: Double,
    val incrementKilograms: Double,
    val sharedAcrossDays: Boolean = true,
    /** null => start from the configured empty-bar weight (barbell lifts). */
    val defaultStartPounds: Double? = null,
    val defaultStartKilograms: Double? = null,
    val successfulRestSeconds: Int = 180,
    val failedRestSeconds: Int = 300,
) {
    fun incrementGrams(unitSystem: UnitSystem): Long = WeightMath.toGrams(
        if (unitSystem == UnitSystem.POUNDS) incrementPounds else incrementKilograms,
        unitSystem,
    )

    fun defaultStartGrams(unitSystem: UnitSystem, barWeightGrams: Long): Long {
        val value = if (unitSystem == UnitSystem.POUNDS) defaultStartPounds else defaultStartKilograms
        return value?.let { WeightMath.toGrams(it, unitSystem) } ?: barWeightGrams
    }
}

/**
 * Assistance work inside a program day. Maps onto the existing accessory system
 * (weight / bodyweight / repetition / timed tracking with configurable progression).
 */
data class AccessoryDef(
    val exerciseId: String,
    val trackingMode: TrackingMode,
    val sets: Int,
    val target: Int,
    val incrementPounds: Double = 5.0,
    val incrementKilograms: Double = 2.5,
    val targetIncrement: Int = 1,
    val progressionEverySuccesses: Int = 1,
    val restSeconds: Int = 180,
    val startPounds: Double = 0.0,
    val startKilograms: Double = 0.0,
) {
    fun incrementGrams(unitSystem: UnitSystem): Long = WeightMath.toGrams(
        if (unitSystem == UnitSystem.POUNDS) incrementPounds else incrementKilograms,
        unitSystem,
    )

    fun startGrams(unitSystem: UnitSystem): Long = WeightMath.toGrams(
        if (unitSystem == UnitSystem.POUNDS) startPounds else startKilograms,
        unitSystem,
    )
}

/** One day of a program (Workout A, B, C, …). The day [key] is the value persisted. */
data class WorkoutDay(
    val key: String,
    val label: String,
    val coreSlots: List<SlotDef>,
    val accessories: List<AccessoryDef> = emptyList(),
)

/**
 * A complete routine. Adding a new linear program is a matter of adding one of these
 * to [BuiltInPrograms.all] — no engine, schema, or UI change.
 */
data class ProgramDefinition(
    val id: String,
    val name: String,
    val summary: String,
    val days: List<WorkoutDay>,
    val progression: ProgressionPolicy = ProgressionPolicy.LinearPerWorkout(),
) {
    val firstDayKey: String get() = days.first().key

    fun day(key: String): WorkoutDay? = days.firstOrNull { it.key == key }

    /** Cyclic rotation through [days]; falls back to the first day for an unknown key. */
    fun nextDayKey(currentKey: String): String {
        val index = days.indexOfFirst { it.key == currentKey }
        if (index < 0) return firstDayKey
        return days[(index + 1) % days.size].key
    }
}
