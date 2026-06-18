package org.freelift5.app.domain

/**
 * The built-in exercise catalog and the program registry.
 *
 * Adding a linear program is a data-only change: append an [ExerciseDefinition] here
 * for any new movement, then add a [ProgramDefinition] to [all]. No engine, schema, or
 * UI change is required.
 *
 * Deferred to Phase 2 (each needs a capability this phase intentionally lacks):
 *  - Ultra: repeats a lift at two volumes in one week (Squat 5x5 on A, 1x5 on C), which
 *    needs per-day prescriptions stored on the day mapping rather than one per slot.
 *  - Madcow, Intermediate: weekly progression cadence and ramped / percentage set schemes.
 *
 * Data points marked TODO(confirm) are best-reading values from source program notes
 * and are safe one-line edits once confirmed.
 */
object BuiltInPrograms {

    object Catalog {
        // The five barbell lifts. Ids and names match RoutineEngine.builtInExercises so
        // existing rows and history stay consistent.
        val SQUAT = ExerciseDefinition(
            "core_squat",
            "Back Squat",
            TrackingMode.WEIGHT,
            CoreSlot.SQUAT,
            EquipmentKind.BARBELL,
        )
        val BENCH = ExerciseDefinition(
            "core_bench_press",
            "Bench Press",
            TrackingMode.WEIGHT,
            CoreSlot.BENCH_PRESS,
            EquipmentKind.BARBELL,
        )
        val ROW = ExerciseDefinition(
            "core_barbell_row",
            "Barbell Row",
            TrackingMode.WEIGHT,
            CoreSlot.BARBELL_ROW,
            EquipmentKind.BARBELL,
        )
        val OHP = ExerciseDefinition(
            "core_overhead_press",
            "Overhead Press",
            TrackingMode.WEIGHT,
            CoreSlot.OVERHEAD_PRESS,
            EquipmentKind.BARBELL,
        )
        val DEADLIFT = ExerciseDefinition(
            "core_deadlift",
            "Deadlift",
            TrackingMode.WEIGHT,
            CoreSlot.DEADLIFT,
            EquipmentKind.BARBELL,
        )

        // Plus assistance movements.
        val INCLINE_BENCH = ExerciseDefinition(
            "bb_incline_bench",
            "Incline Bench Press",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.BARBELL,
        )
        val DIPS = ExerciseDefinition(
            "dips",
            "Dips",
            TrackingMode.BODYWEIGHT,
            equipment = EquipmentKind.BODYWEIGHT,
        )
        val PULL_UPS = ExerciseDefinition(
            "pull_ups",
            "Pull-ups",
            TrackingMode.BODYWEIGHT,
            equipment = EquipmentKind.BODYWEIGHT,
        )
        val SITUPS = ExerciseDefinition("situps", "Situps", TrackingMode.REPETITIONS)
        val SKULLCRUSHER = ExerciseDefinition(
            "skullcrusher",
            "Skullcrusher",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.BARBELL,
        )
        val BARBELL_CURL = ExerciseDefinition(
            "bb_curl",
            "Barbell Curl",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.BARBELL,
        )
        val CALF_RAISE = ExerciseDefinition(
            "calf_raise",
            "Calf Raise",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.BARBELL,
        )
        val PALLOF_PRESS = ExerciseDefinition("pallof_press", "Pallof Press", TrackingMode.REPETITIONS)
        val PLANK = ExerciseDefinition("plank", "Plank", TrackingMode.TIME)

        // Dumbbell movements (5xOTG, and shared Plus assistance).
        val DB_BENCH = ExerciseDefinition(
            "db_bench",
            "Dumbbell Bench Press",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.DUMBBELL,
        )
        val DB_INCLINE = ExerciseDefinition(
            "db_incline_bench",
            "Dumbbell Incline Press",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.DUMBBELL,
        )
        val DB_ROW = ExerciseDefinition(
            "db_row",
            "Dumbbell Row",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.DUMBBELL,
        )
        val DB_OHP = ExerciseDefinition(
            "db_overhead_press",
            "Dumbbell Overhead Press",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.DUMBBELL,
        )
        val DB_LUNGE = ExerciseDefinition(
            "db_lunge",
            "Dumbbell Lunge",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.DUMBBELL,
        )
        val DB_RDL = ExerciseDefinition(
            "db_romanian_deadlift",
            "Dumbbell Romanian Deadlift",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.DUMBBELL,
        )
        val DB_CURL = ExerciseDefinition(
            "db_curl",
            "Dumbbell Curl",
            TrackingMode.WEIGHT,
            equipment = EquipmentKind.DUMBBELL,
        )

        val all: List<ExerciseDefinition> = listOf(
            SQUAT, BENCH, ROW, OHP, DEADLIFT,
            INCLINE_BENCH, DIPS, PULL_UPS, SITUPS, SKULLCRUSHER, BARBELL_CURL, CALF_RAISE, PALLOF_PRESS, PLANK,
            DB_BENCH, DB_INCLINE, DB_ROW, DB_OHP, DB_LUNGE, DB_RDL, DB_CURL,
        )

        fun byId(id: String): ExerciseDefinition? = all.firstOrNull { it.id == id }

        fun equipmentForExercise(id: String): EquipmentKind? = byId(id)?.equipment
    }

    const val DEFAULT_ID = "original"

    val all: List<ProgramDefinition> = listOf(
        original(),
        lite(),
        mini(),
        plus(),
        onTheGo(),
    )

    fun byId(id: String): ProgramDefinition = all.firstOrNull { it.id == id } ?: original()

    // ---- Program definitions -------------------------------------------------------

    private const val INC_LB = 5.0
    private const val INC_KG = 2.5
    private const val DL_INC_LB = 10.0
    private const val DL_INC_KG = 5.0

    /** A barbell main lift: straight sets, empty-bar default start. */
    private fun barbell(
        exercise: ExerciseDefinition,
        sets: Int,
        reps: Int,
        incLb: Double = INC_LB,
        incKg: Double = INC_KG,
    ) = SlotDef(
        canonicalSlot = exercise.builtInSlot?.name ?: exercise.id.uppercase(),
        exerciseId = exercise.id,
        setScheme = SetScheme.Straight(sets, reps),
        incrementPounds = incLb,
        incrementKilograms = incKg,
    )

    /** A dumbbell main lift: straight sets, light fixed default start (not a barbell). */
    private fun dumbbell(
        canonical: String,
        exercise: ExerciseDefinition,
        sets: Int,
        reps: Int,
    ) = SlotDef(
        canonicalSlot = canonical,
        exerciseId = exercise.id,
        setScheme = SetScheme.Straight(sets, reps),
        incrementPounds = INC_LB,
        incrementKilograms = INC_KG,
        // Dumbbell jumps vary; 5 lb / 2.5 kg increment and 10 lb / 5 kg start are editable defaults.
        defaultStartPounds = 10.0,
        defaultStartKilograms = 5.0,
    )

    private fun original() = ProgramDefinition(
        id = "original",
        name = "Original 5x5",
        summary = "The classic A/B routine: five compound lifts, add weight every workout.",
        days = listOf(
            WorkoutDay(
                key = "A",
                label = "Workout A",
                coreSlots = listOf(
                    barbell(Catalog.SQUAT, 5, 5),
                    barbell(Catalog.BENCH, 5, 5),
                    barbell(Catalog.ROW, 5, 5),
                ),
            ),
            WorkoutDay(
                key = "B",
                label = "Workout B",
                coreSlots = listOf(
                    barbell(Catalog.SQUAT, 5, 5),
                    barbell(Catalog.OHP, 5, 5),
                    barbell(Catalog.DEADLIFT, 1, 5, DL_INC_LB, DL_INC_KG),
                ),
            ),
        ),
    )

    private fun lite() = ProgramDefinition(
        id = "lite",
        name = "Lite (2x5)",
        summary = "Same lifts as Original at two work sets each — lower volume for recovery-limited lifters.",
        days = listOf(
            WorkoutDay(
                key = "A",
                label = "Workout A",
                coreSlots = listOf(
                    barbell(Catalog.SQUAT, 2, 5),
                    barbell(Catalog.BENCH, 2, 5),
                    barbell(Catalog.ROW, 2, 5),
                ),
            ),
            WorkoutDay(
                key = "B",
                label = "Workout B",
                coreSlots = listOf(
                    barbell(Catalog.SQUAT, 2, 5),
                    barbell(Catalog.OHP, 2, 5),
                    barbell(Catalog.DEADLIFT, 2, 5, DL_INC_LB, DL_INC_KG),
                ),
            ),
        ),
    )

    private fun mini() = ProgramDefinition(
        id = "mini",
        name = "Mini (2 lifts)",
        summary = "Two lifts per session at 2x5 — the shortest barbell routine, no rows.",
        days = listOf(
            WorkoutDay(
                key = "A",
                label = "Workout A",
                coreSlots = listOf(
                    barbell(Catalog.SQUAT, 2, 5),
                    barbell(Catalog.BENCH, 2, 5),
                ),
            ),
            WorkoutDay(
                key = "B",
                label = "Workout B",
                coreSlots = listOf(
                    barbell(Catalog.DEADLIFT, 2, 5, DL_INC_LB, DL_INC_KG),
                    barbell(Catalog.OHP, 2, 5),
                ),
            ),
        ),
    )

    // Plus accessory defaults. TODO(confirm): exact accessory selection and targets.
    private fun acc(
        exercise: ExerciseDefinition,
        sets: Int,
        target: Int,
        weighted: Boolean = exercise.trackingMode == TrackingMode.WEIGHT,
    ) = AccessoryDef(
        exerciseId = exercise.id,
        trackingMode = exercise.trackingMode,
        sets = sets,
        target = target,
        incrementPounds = if (weighted) INC_LB else 0.0,
        incrementKilograms = if (weighted) INC_KG else 0.0,
        targetIncrement = if (weighted) 0 else 1,
    )

    private fun plus() = ProgramDefinition(
        id = "plus",
        name = "Plus (5x5 + assistance)",
        summary = "Original plus an assistance day: more upper-body and isolation volume across A/B/C.",
        days = listOf(
            WorkoutDay(
                key = "A",
                label = "Workout A",
                coreSlots = listOf(
                    barbell(Catalog.SQUAT, 5, 5),
                    barbell(Catalog.BENCH, 5, 5),
                    barbell(Catalog.ROW, 5, 5),
                ),
                accessories = listOf(
                    acc(Catalog.SITUPS, 3, 8),
                    acc(Catalog.SKULLCRUSHER, 3, 8),
                    acc(Catalog.BARBELL_CURL, 3, 8),
                    acc(Catalog.CALF_RAISE, 3, 8),
                    acc(Catalog.PALLOF_PRESS, 3, 8),
                ),
            ),
            WorkoutDay(
                key = "B",
                label = "Workout B",
                coreSlots = listOf(
                    barbell(Catalog.DEADLIFT, 5, 5, DL_INC_LB, DL_INC_KG),
                    barbell(Catalog.OHP, 5, 5),
                ),
                accessories = listOf(
                    acc(Catalog.DIPS, 5, 5),
                    AccessoryDef(Catalog.PLANK.id, TrackingMode.TIME, sets = 3, target = 30, targetIncrement = 5),
                ),
            ),
            WorkoutDay(
                key = "C",
                label = "Workout C",
                coreSlots = emptyList(),
                accessories = listOf(
                    acc(Catalog.INCLINE_BENCH, 3, 8),
                    acc(Catalog.PULL_UPS, 3, 8),
                    acc(Catalog.DB_BENCH, 3, 8),
                    acc(Catalog.DB_ROW, 3, 8),
                ),
            ),
        ),
    )

    private fun onTheGo() = ProgramDefinition(
        id = "on_the_go",
        name = "5xOTG",
        summary = "On-the-go dumbbell-and-bench routine at 5x12 for travel or limited equipment.",
        days = listOf(
            WorkoutDay(
                key = "A",
                label = "Workout A",
                coreSlots = listOf(
                    dumbbell("DB_LUNGE", Catalog.DB_LUNGE, 5, 12),
                    dumbbell("DB_BENCH", Catalog.DB_BENCH, 5, 12),
                    dumbbell("DB_ROW", Catalog.DB_ROW, 5, 12),
                ),
                accessories = listOf(acc(Catalog.DB_CURL, 3, 12)),
            ),
            WorkoutDay(
                key = "B",
                label = "Workout B",
                coreSlots = listOf(
                    dumbbell("DB_RDL", Catalog.DB_RDL, 5, 12),
                    dumbbell("DB_INCLINE", Catalog.DB_INCLINE, 5, 12),
                    dumbbell("DB_OHP", Catalog.DB_OHP, 5, 12),
                ),
                accessories = listOf(acc(Catalog.SITUPS, 3, 12)),
            ),
        ),
    )
}
