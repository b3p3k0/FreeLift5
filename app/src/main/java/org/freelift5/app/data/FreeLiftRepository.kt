package org.freelift5.app.data

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.freelift5.app.domain.BuiltInPrograms
import org.freelift5.app.domain.CoreSlot
import org.freelift5.app.domain.ExercisePrescription
import org.freelift5.app.domain.ProgressionAction
import org.freelift5.app.domain.ProgressionEngine
import org.freelift5.app.domain.ProgramDefinition
import org.freelift5.app.domain.ProgressionState
import org.freelift5.app.domain.SetPerformance
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath

class FreeLiftRepository(
    private val database: FreeLiftDatabase,
    private val settingsStore: SettingsStore,
) {
    private val dao = database.dao()

    val settings: Flow<AppSettings> = settingsStore.settings
    val exercises: Flow<List<ExerciseEntity>> = dao.observeExercises()
    val coreProgram: Flow<List<CoreSlotSummary>> = dao.observeCoreProgram()
    val accessories: Flow<List<AccessorySummary>> = dao.observeAccessories()
    val activeWorkout: Flow<WorkoutSessionWithExercises?> = dao.observeActiveWorkout()
    val history: Flow<List<WorkoutSessionWithExercises>> = dao.observeWorkoutHistory()
    val measurements: Flow<List<BodyMeasurementEntity>> = dao.observeMeasurements()
    val exerciseProgress: Flow<List<ExerciseProgressPoint>> = dao.observeExerciseProgress()

    suspend fun seedBuiltInExercises() {
        dao.upsertExercises(BuiltInPrograms.Catalog.all.map { exercise ->
            ExerciseEntity(
                id = exercise.id,
                name = exercise.name,
                trackingMode = exercise.trackingMode.name,
                builtInSlot = exercise.builtInSlot?.name,
                notes = exercise.notes,
            )
        })
    }

    suspend fun completeOnboarding(
        unitSystem: UnitSystem,
        birthMonth: Int?,
        birthYear: Int?,
        heightMillimeters: Int?,
        trainingBackground: String,
        bodyWeightGrams: Long?,
        barWeightGrams: Long,
        startingWeights: Map<CoreSlot, Long>,
        programId: String = BuiltInPrograms.DEFAULT_ID,
    ) {
        seedBuiltInExercises()
        val program = BuiltInPrograms.byId(programId)
        database.withTransaction {
            materializeProgram(
                program = program,
                unitSystem = unitSystem,
                startingWeights = startingWeights.mapKeys { it.key.name },
                barWeightGrams = barWeightGrams,
            )
            bodyWeightGrams?.let {
                dao.insertMeasurement(
                    BodyMeasurementEntity(
                        recordedAtEpochMillis = System.currentTimeMillis(),
                        bodyWeightGrams = it,
                    ),
                )
            }
        }
        settingsStore.update {
            it.copy(
                onboardingComplete = true,
                unitSystem = unitSystem,
                birthMonth = birthMonth,
                birthYear = birthYear,
                heightMillimeters = heightMillimeters,
                trainingBackground = trainingBackground,
                barWeightGrams = barWeightGrams,
                activeProgramId = program.id,
                nextWorkoutDayKey = program.firstDayKey,
            )
        }
    }

    /**
     * Switch the active program. History tables are left untouched (their immutable
     * snapshots preserve all past workouts); only the live core slots, day mappings, and
     * accessory assignments are replaced. Working weights carry over for any lift whose
     * canonical slot the new program also uses.
     */
    suspend fun switchProgram(programId: String) {
        if (activeWorkout.first() != null) {
            error("Finish or discard the active workout before switching programs.")
        }
        val program = BuiltInPrograms.byId(programId)
        val appSettings = settings.first()
        seedBuiltInExercises()
        database.withTransaction {
            val carriedWeights = dao.getAllCoreSlots()
                .associate { it.canonicalSlot to it.currentWeightGrams }
            dao.deleteAllWorkoutCoreSlots()
            dao.deleteAllCoreSlots()
            dao.deleteAllAccessories()
            materializeProgram(
                program = program,
                unitSystem = appSettings.unitSystem,
                startingWeights = carriedWeights,
                barWeightGrams = appSettings.barWeightGrams,
            )
        }
        settingsStore.update {
            it.copy(
                activeProgramId = program.id,
                nextWorkoutDayKey = program.firstDayKey,
            )
        }
    }

    /**
     * Build a program's live state from its definition. Shared core slots (the same
     * [canonicalSlot] used on more than one day) collapse to a single `core_slots` row
     * referenced by one `workout_core_slots` row per day. Program-prescribed accessories
     * are seeded `required = true`. Caller must run this inside a transaction.
     */
    private suspend fun materializeProgram(
        program: ProgramDefinition,
        unitSystem: UnitSystem,
        startingWeights: Map<String, Long>,
        barWeightGrams: Long,
    ) {
        val coreSlotsByKey = LinkedHashMap<String, CoreSlotEntity>()
        val workoutCoreSlots = mutableListOf<WorkoutCoreSlotEntity>()
        val accessories = mutableListOf<AccessoryAssignmentEntity>()
        program.days.forEach { day ->
            day.coreSlots.forEachIndexed { index, slot ->
                val slotKey = slot.canonicalSlot
                if (slotKey !in coreSlotsByKey) {
                    coreSlotsByKey[slotKey] = CoreSlotEntity(
                        slotKey = slotKey,
                        canonicalSlot = slot.canonicalSlot,
                        exerciseId = slot.exerciseId,
                        sets = slot.setScheme.workSets,
                        reps = slot.setScheme.targetReps,
                        incrementGrams = slot.incrementGrams(unitSystem),
                        currentWeightGrams = startingWeights[slot.canonicalSlot]
                            ?: slot.defaultStartGrams(unitSystem, barWeightGrams),
                        successfulRestSeconds = slot.successfulRestSeconds,
                        failedRestSeconds = slot.failedRestSeconds,
                    )
                }
                workoutCoreSlots += WorkoutCoreSlotEntity(
                    id = "${day.key}_$slotKey",
                    workoutType = day.key,
                    slotKey = slotKey,
                    orderIndex = index,
                )
            }
            day.accessories.forEachIndexed { index, accessory ->
                accessories += AccessoryAssignmentEntity(
                    id = UUID.randomUUID().toString(),
                    workoutType = day.key,
                    exerciseId = accessory.exerciseId,
                    orderIndex = index,
                    sets = accessory.sets,
                    target = accessory.target,
                    currentWeightGrams = accessory.startGrams(unitSystem),
                    incrementGrams = accessory.incrementGrams(unitSystem),
                    targetIncrement = accessory.targetIncrement,
                    progressionEverySuccesses = accessory.progressionEverySuccesses,
                    restSeconds = accessory.restSeconds,
                    required = true,
                )
            }
        }
        dao.upsertCoreSlots(coreSlotsByKey.values.toList())
        dao.upsertWorkoutCoreSlots(workoutCoreSlots)
        accessories.forEach { dao.upsertAccessory(it) }
    }

    suspend fun startWorkout(): Long = database.withTransaction {
        val appSettings = settings.first()
        val dayKey = appSettings.nextWorkoutDayKey
        val existing = activeWorkout.first()
        if (existing != null) return@withTransaction existing.session.id

        val sessionId = dao.insertWorkoutSession(
            WorkoutSessionEntity(
                workoutType = dayKey,
                startedAtEpochMillis = System.currentTimeMillis(),
                status = "ACTIVE",
            ),
        )
        val core = dao.getCoreProgram(dayKey)
        val accessory = dao.getAccessories(dayKey)
        val sessions = buildList {
            core.forEach { item ->
                add(
                    ExerciseSessionEntity(
                        workoutSessionId = sessionId,
                        exerciseId = item.exerciseId,
                        exerciseName = item.exerciseName,
                        trackingMode = TrackingMode.WEIGHT.name,
                        coreSlotKey = item.slotKey,
                        accessoryAssignmentId = null,
                        orderIndex = item.orderIndex,
                        targetSets = item.sets,
                        targetReps = item.reps,
                        targetWeightGrams = item.currentWeightGrams,
                        incrementGrams = item.incrementGrams,
                        targetIncrement = 0,
                        successfulRestSeconds = item.successfulRestSeconds,
                        failedRestSeconds = item.failedRestSeconds,
                    ),
                )
            }
            accessory.forEachIndexed { index, item ->
                add(
                    ExerciseSessionEntity(
                        workoutSessionId = sessionId,
                        exerciseId = item.exerciseId,
                        exerciseName = item.exerciseName,
                        trackingMode = item.trackingMode,
                        coreSlotKey = null,
                        accessoryAssignmentId = item.assignmentId,
                        orderIndex = core.size + index,
                        targetSets = item.sets,
                        targetReps = item.target,
                        targetWeightGrams = item.currentWeightGrams,
                        incrementGrams = item.incrementGrams,
                        targetIncrement = item.targetIncrement,
                        successfulRestSeconds = item.restSeconds,
                        failedRestSeconds = item.restSeconds,
                    ),
                )
            }
        }
        dao.insertExerciseSessions(sessions)
        sessionId
    }

    suspend fun saveSet(
        exerciseSessionId: Long,
        setNumber: Int,
        actualReps: Int,
        actualWeightGrams: Long,
        isWarmup: Boolean = false,
    ) {
        val exercise = dao.getExerciseSession(exerciseSessionId)
            ?: error("Exercise session $exerciseSessionId does not exist.")
        dao.upsertSet(
            SetRecordEntity(
                exerciseSessionId = exerciseSessionId,
                setNumber = setNumber,
                isWarmup = isWarmup,
                targetReps = exercise.targetReps,
                actualReps = actualReps,
                targetWeightGrams = exercise.targetWeightGrams,
                actualWeightGrams = actualWeightGrams,
                completedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun finishWorkout(
        sessionId: Long,
        notes: String,
    ) {
        database.withTransaction {
            val workout = dao.getWorkout(sessionId)
                ?: error("Workout session $sessionId does not exist.")
            val appSettings = settings.first()
            val deloadIncrement = defaultLoadableIncrement(appSettings.unitSystem)
            var allComplete = true

            workout.exercises.forEach { relation ->
                val exercise = relation.exercise
                val slotKey = exercise.coreSlotKey
                if (slotKey == null) {
                    if (updateAccessoryProgression(exercise, relation.sets)) {
                        allComplete = false
                    }
                    return@forEach
                }
                val slot = dao.getCoreSlot(slotKey) ?: return@forEach
                val workSets = relation.sets.filterNot(SetRecordEntity::isWarmup)
                val decision = ProgressionEngine.evaluate(
                    state = ProgressionState(
                        currentWeightGrams = exercise.targetWeightGrams,
                        consecutiveFailures = slot.consecutiveFailures,
                    ),
                    prescription = ExercisePrescription(
                        sets = exercise.targetSets,
                        reps = exercise.targetReps,
                        incrementGrams = exercise.incrementGrams,
                        successfulRestSeconds = exercise.successfulRestSeconds,
                        failedRestSeconds = exercise.failedRestSeconds,
                    ),
                    sets = workSets.map { set ->
                        SetPerformance(
                            setNumber = set.setNumber,
                            targetReps = set.targetReps,
                            actualReps = set.actualReps,
                            targetWeightGrams = set.targetWeightGrams,
                            actualWeightGrams = set.actualWeightGrams,
                        )
                    },
                    deloadRoundingIncrementGrams = deloadIncrement,
                )
                if (decision.action == ProgressionAction.RETAIN_INCOMPLETE) {
                    allComplete = false
                }
                dao.updateCoreSlot(
                    slot.copy(
                        currentWeightGrams = decision.nextWeightGrams,
                        consecutiveFailures = decision.consecutiveFailures,
                        pendingDeloadGrams = decision.suggestedDeloadGrams,
                    ),
                )
                dao.updateExerciseSession(
                    exercise.copy(
                        progressionAction = decision.action.name,
                        nextWeightGrams = decision.nextWeightGrams,
                    ),
                )
            }

            dao.updateWorkoutSession(
                workout.session.copy(
                    completedAtEpochMillis = System.currentTimeMillis(),
                    status = if (allComplete) "COMPLETED" else "PARTIAL",
                    notes = notes,
                ),
            )
            settingsStore.update {
                it.copy(
                    nextWorkoutDayKey = BuiltInPrograms.byId(appSettings.activeProgramId)
                        .nextDayKey(workout.session.workoutType),
                )
            }
        }
    }

    suspend fun resolveDeload(
        slotKey: String,
        selectedWeightGrams: Long,
    ) {
        val slot = dao.getCoreSlot(slotKey) ?: return
        dao.updateCoreSlot(
            slot.copy(
                currentWeightGrams = selectedWeightGrams,
                consecutiveFailures = 0,
                pendingDeloadGrams = null,
            ),
        )
    }

    suspend fun addAccessory(
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
    ) {
        val exerciseId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.upsertExercise(
                ExerciseEntity(
                    id = exerciseId,
                    name = name.trim(),
                    trackingMode = trackingMode.name,
                    builtInSlot = null,
                    notes = notes.trim(),
                ),
            )
            workoutTypes.forEach { dayKey ->
                val nextOrder = dao.getAccessories(dayKey).size
                dao.upsertAccessory(
                    AccessoryAssignmentEntity(
                        id = UUID.randomUUID().toString(),
                        workoutType = dayKey,
                        exerciseId = exerciseId,
                        orderIndex = nextOrder,
                        sets = sets,
                        target = target,
                        currentWeightGrams = startingWeightGrams,
                        incrementGrams = incrementGrams,
                        targetIncrement = targetIncrement,
                        progressionEverySuccesses = progressionEverySuccesses,
                        restSeconds = restSeconds,
                        required = false,
                    ),
                )
            }
        }
    }

    suspend fun deleteAccessory(id: String) {
        dao.deleteAccessory(id)
    }

    suspend fun deleteAccessories(ids: List<String>) {
        if (ids.isNotEmpty()) dao.deleteAccessories(ids)
    }

    suspend fun replaceCoreExercise(
        slotKey: String,
        exerciseId: String,
    ) {
        val slot = dao.getCoreSlot(slotKey) ?: return
        requireNotNull(dao.getExercise(exerciseId)) { "Replacement exercise does not exist." }
        dao.updateCoreSlot(slot.copy(exerciseId = exerciseId))
    }

    suspend fun createCustomExercise(
        name: String,
        trackingMode: TrackingMode,
        notes: String,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsertExercise(
            ExerciseEntity(
                id = id,
                name = name.trim(),
                trackingMode = trackingMode.name,
                builtInSlot = null,
                notes = notes.trim(),
            ),
        )
        return id
    }

    suspend fun updateCoreSettings(
        slotKey: String,
        sets: Int,
        reps: Int,
        currentWeightGrams: Long,
        incrementGrams: Long,
        successfulRestSeconds: Int,
        failedRestSeconds: Int,
    ) {
        val slot = dao.getCoreSlot(slotKey) ?: return
        dao.updateCoreSlot(
            slot.copy(
                sets = sets,
                reps = reps,
                currentWeightGrams = currentWeightGrams,
                incrementGrams = incrementGrams,
                successfulRestSeconds = successfulRestSeconds,
                failedRestSeconds = failedRestSeconds,
            ),
        )
    }

    suspend fun splitCoreSlotForWorkout(
        slotKey: String,
        dayKey: String,
    ): String = database.withTransaction {
        val original = dao.getCoreSlot(slotKey)
            ?: error("Core slot $slotKey does not exist.")
        val mapping = dao.getWorkoutCoreSlot(slotKey, dayKey)
            ?: error("Core slot $slotKey is not used by workout $dayKey.")
        val newKey = "${original.canonicalSlot}_${dayKey}_${UUID.randomUUID()}"
        dao.upsertCoreSlots(listOf(original.copy(slotKey = newKey)))
        dao.updateWorkoutCoreSlot(mapping.copy(slotKey = newKey))
        newKey
    }

    suspend fun addMeasurement(
        bodyWeightGrams: Long,
        recordedAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        dao.insertMeasurement(
            BodyMeasurementEntity(
                recordedAtEpochMillis = recordedAtEpochMillis,
                bodyWeightGrams = bodyWeightGrams,
            ),
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }

    suspend fun settingsSnapshot(): AppSettings = settings.first()

    suspend fun clearAllData() {
        database.withTransaction {
            dao.deleteAllSets()
            dao.deleteAllExerciseSessions()
            dao.deleteAllWorkoutSessions()
            dao.deleteAllMeasurements()
            dao.deleteAllAccessories()
            dao.deleteAllWorkoutCoreSlots()
            dao.deleteAllCoreSlots()
            dao.deleteAllExercises()
        }
        settingsStore.clear()
        seedBuiltInExercises()
    }

    private fun defaultLoadableIncrement(unitSystem: UnitSystem): Long =
        WeightMath.toGrams(
            if (unitSystem == UnitSystem.POUNDS) 5.0 else 2.5,
            unitSystem,
        )

    /** Returns true when this is a required accessory that was left incomplete. */
    private suspend fun updateAccessoryProgression(
        exercise: ExerciseSessionEntity,
        sets: List<SetRecordEntity>,
    ): Boolean {
        val assignmentId = exercise.accessoryAssignmentId ?: return false
        val assignment = dao.getAccessory(assignmentId) ?: return false
        val workSets = sets.filterNot(SetRecordEntity::isWarmup)
        val workSetsByNumber = workSets.associateBy(SetRecordEntity::setNumber)
        val prescribedSets = (1..exercise.targetSets).mapNotNull(workSetsByNumber::get)
        if (prescribedSets.size < exercise.targetSets) return assignment.required
        val weighted = exercise.trackingMode == TrackingMode.WEIGHT.name
        val success = prescribedSets.all {
            it.actualReps >= exercise.targetReps &&
                (!weighted || it.actualWeightGrams >= exercise.targetWeightGrams)
        }
        if (!success) {
            dao.updateExerciseSession(
                exercise.copy(
                    progressionAction = ProgressionAction.REPEAT.name,
                    nextWeightGrams = exercise.targetWeightGrams,
                    nextTarget = exercise.targetReps,
                ),
            )
            return false
        }
        val successes = assignment.successfulSessions + 1
        val shouldIncrease = successes >= assignment.progressionEverySuccesses
        val nextWeight = if (shouldIncrease && weighted) {
            assignment.currentWeightGrams + assignment.incrementGrams
        } else {
            assignment.currentWeightGrams
        }
        val nextTarget = if (shouldIncrease && !weighted) {
            assignment.target + assignment.targetIncrement
        } else {
            assignment.target
        }
        dao.updateAccessory(
            assignment.copy(
                currentWeightGrams = nextWeight,
                target = nextTarget,
                successfulSessions = if (shouldIncrease) 0 else successes,
            ),
        )
        dao.updateExerciseSession(
            exercise.copy(
                progressionAction = if (shouldIncrease) {
                    ProgressionAction.INCREASE.name
                } else {
                    ProgressionAction.REPEAT.name
                },
                nextWeightGrams = nextWeight,
                nextTarget = nextTarget,
            ),
        )
        return false
    }
}
