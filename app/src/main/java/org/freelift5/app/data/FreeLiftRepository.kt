package org.freelift5.app.data

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.freelift5.app.domain.CoreSlot
import org.freelift5.app.domain.ExercisePrescription
import org.freelift5.app.domain.ProgressionAction
import org.freelift5.app.domain.ProgressionEngine
import org.freelift5.app.domain.ProgressionState
import org.freelift5.app.domain.RoutineEngine
import org.freelift5.app.domain.SetPerformance
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.domain.WorkoutType

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
        dao.upsertExercises(RoutineEngine.builtInExercises.values.map { exercise ->
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
    ) {
        seedBuiltInExercises()
        database.withTransaction {
            val coreSlots = CoreSlot.entries.map { slot ->
                val prescription = RoutineEngine.defaultPrescription(slot, unitSystem)
                CoreSlotEntity(
                    slotKey = slot.name,
                    canonicalSlot = slot.name,
                    exerciseId = RoutineEngine.builtInExercises.getValue(slot).id,
                    sets = prescription.sets,
                    reps = prescription.reps,
                    incrementGrams = prescription.incrementGrams,
                    currentWeightGrams = startingWeights[slot] ?: barWeightGrams,
                )
            }
            dao.upsertCoreSlots(coreSlots)
            dao.upsertWorkoutCoreSlots(
                WorkoutType.entries.flatMap { workoutType ->
                    RoutineEngine.slotsFor(workoutType).mapIndexed { index, slot ->
                        WorkoutCoreSlotEntity(
                            id = "${workoutType.name}_${slot.name}",
                            workoutType = workoutType.name,
                            slotKey = slot.name,
                            orderIndex = index,
                        )
                    }
                },
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
                nextWorkout = WorkoutType.A,
            )
        }
    }

    suspend fun startWorkout(): Long = database.withTransaction {
        val appSettings = settings.first()
        val workoutType = appSettings.nextWorkout
        val existing = activeWorkout.first()
        if (existing != null) return@withTransaction existing.session.id

        val sessionId = dao.insertWorkoutSession(
            WorkoutSessionEntity(
                workoutType = workoutType.name,
                startedAtEpochMillis = System.currentTimeMillis(),
                status = "ACTIVE",
            ),
        )
        val core = dao.getCoreProgram(workoutType.name)
        val accessory = dao.getAccessories(workoutType.name)
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
            var allCoreComplete = true

            workout.exercises.forEach { relation ->
                val exercise = relation.exercise
                val slotKey = exercise.coreSlotKey
                if (slotKey == null) {
                    updateAccessoryProgression(exercise, relation.sets)
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
                    allCoreComplete = false
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
                    status = if (allCoreComplete) "COMPLETED" else "PARTIAL",
                    notes = notes,
                ),
            )
            settingsStore.update {
                it.copy(nextWorkout = WorkoutType.valueOf(workout.session.workoutType).next())
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
        workoutTypes: Set<WorkoutType>,
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
            workoutTypes.forEach { workoutType ->
                val nextOrder = dao.getAccessories(workoutType.name).size
                dao.upsertAccessory(
                    AccessoryAssignmentEntity(
                        id = UUID.randomUUID().toString(),
                        workoutType = workoutType.name,
                        exerciseId = exerciseId,
                        orderIndex = nextOrder,
                        sets = sets,
                        target = target,
                        currentWeightGrams = startingWeightGrams,
                        incrementGrams = incrementGrams,
                        targetIncrement = targetIncrement,
                        progressionEverySuccesses = progressionEverySuccesses,
                        restSeconds = restSeconds,
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
        workoutType: WorkoutType,
    ): String = database.withTransaction {
        val original = dao.getCoreSlot(slotKey)
            ?: error("Core slot $slotKey does not exist.")
        val mapping = dao.getWorkoutCoreSlot(slotKey, workoutType.name)
            ?: error("Core slot $slotKey is not used by workout ${workoutType.name}.")
        val newKey = "${original.canonicalSlot}_${workoutType.name}_${UUID.randomUUID()}"
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

    private suspend fun updateAccessoryProgression(
        exercise: ExerciseSessionEntity,
        sets: List<SetRecordEntity>,
    ) {
        val assignmentId = exercise.accessoryAssignmentId ?: return
        val assignment = dao.getAccessory(assignmentId) ?: return
        val workSets = sets.filterNot(SetRecordEntity::isWarmup)
        val workSetsByNumber = workSets.associateBy(SetRecordEntity::setNumber)
        val prescribedSets = (1..exercise.targetSets).mapNotNull(workSetsByNumber::get)
        if (prescribedSets.size < exercise.targetSets) return
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
            return
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
    }
}
