package org.freelift5.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FreeLiftDao {
    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getExercise(id: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoreSlots(slots: List<CoreSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutCoreSlots(slots: List<WorkoutCoreSlotEntity>)

    @Update
    suspend fun updateCoreSlot(slot: CoreSlotEntity)

    @Query("SELECT * FROM core_slots ORDER BY canonicalSlot, slotKey")
    suspend fun getAllCoreSlots(): List<CoreSlotEntity>

    @Query("SELECT * FROM core_slots WHERE slotKey = :slotKey LIMIT 1")
    suspend fun getCoreSlot(slotKey: String): CoreSlotEntity?

    @Query(
        """
        SELECT * FROM workout_core_slots
        WHERE slotKey = :slotKey AND workoutType = :workoutType
        LIMIT 1
        """,
    )
    suspend fun getWorkoutCoreSlot(
        slotKey: String,
        workoutType: String,
    ): WorkoutCoreSlotEntity?

    @Update
    suspend fun updateWorkoutCoreSlot(slot: WorkoutCoreSlotEntity)

    @Query(
        """
        SELECT cs.slotKey, cs.canonicalSlot, cs.exerciseId, e.name AS exerciseName,
               wc.workoutType, wc.orderIndex, cs.sets, cs.reps, cs.incrementGrams,
               cs.currentWeightGrams, cs.consecutiveFailures, cs.pendingDeloadGrams,
               cs.successfulRestSeconds, cs.failedRestSeconds
        FROM workout_core_slots wc
        JOIN core_slots cs ON cs.slotKey = wc.slotKey
        JOIN exercises e ON e.id = cs.exerciseId
        ORDER BY wc.workoutType, wc.orderIndex
        """,
    )
    fun observeCoreProgram(): Flow<List<CoreSlotSummary>>

    @Query(
        """
        SELECT cs.slotKey, cs.canonicalSlot, cs.exerciseId, e.name AS exerciseName,
               wc.workoutType, wc.orderIndex, cs.sets, cs.reps, cs.incrementGrams,
               cs.currentWeightGrams, cs.consecutiveFailures, cs.pendingDeloadGrams,
               cs.successfulRestSeconds, cs.failedRestSeconds
        FROM workout_core_slots wc
        JOIN core_slots cs ON cs.slotKey = wc.slotKey
        JOIN exercises e ON e.id = cs.exerciseId
        WHERE wc.workoutType = :workoutType
        ORDER BY wc.orderIndex
        """,
    )
    suspend fun getCoreProgram(workoutType: String): List<CoreSlotSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccessory(accessory: AccessoryAssignmentEntity)

    @Query("SELECT * FROM accessory_assignments WHERE id = :id LIMIT 1")
    suspend fun getAccessory(id: String): AccessoryAssignmentEntity?

    @Update
    suspend fun updateAccessory(accessory: AccessoryAssignmentEntity)

    @Query("DELETE FROM accessory_assignments WHERE id = :id")
    suspend fun deleteAccessory(id: String)

    @Query("DELETE FROM accessory_assignments WHERE id IN (:ids)")
    suspend fun deleteAccessories(ids: List<String>)

    @Query(
        """
        SELECT a.id AS assignmentId, a.workoutType, a.exerciseId,
               e.name AS exerciseName, e.trackingMode, e.notes, a.orderIndex,
               a.sets, a.target, a.currentWeightGrams, a.incrementGrams,
               a.targetIncrement, a.progressionEverySuccesses,
               a.successfulSessions, a.restSeconds, a.required
        FROM accessory_assignments a
        JOIN exercises e ON e.id = a.exerciseId
        ORDER BY a.workoutType, a.orderIndex
        """,
    )
    fun observeAccessories(): Flow<List<AccessorySummary>>

    @Query(
        """
        SELECT a.id AS assignmentId, a.workoutType, a.exerciseId,
               e.name AS exerciseName, e.trackingMode, e.notes, a.orderIndex,
               a.sets, a.target, a.currentWeightGrams, a.incrementGrams,
               a.targetIncrement, a.progressionEverySuccesses,
               a.successfulSessions, a.restSeconds, a.required
        FROM accessory_assignments a
        JOIN exercises e ON e.id = a.exerciseId
        WHERE a.workoutType = :workoutType
        ORDER BY a.orderIndex
        """,
    )
    suspend fun getAccessories(workoutType: String): List<AccessorySummary>

    @Insert
    suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateWorkoutSession(session: WorkoutSessionEntity)

    @Insert
    suspend fun insertExerciseSessions(exercises: List<ExerciseSessionEntity>): List<Long>

    @Update
    suspend fun updateExerciseSession(exercise: ExerciseSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(set: SetRecordEntity): Long

    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'ACTIVE'
        ORDER BY startedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    fun observeActiveWorkout(): Flow<WorkoutSessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getWorkout(sessionId: Long): WorkoutSessionWithExercises?

    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status != 'ACTIVE'
        ORDER BY completedAtEpochMillis DESC
        """,
    )
    fun observeWorkoutHistory(): Flow<List<WorkoutSessionWithExercises>>

    @Query("SELECT * FROM exercise_sessions WHERE id = :id LIMIT 1")
    suspend fun getExerciseSession(id: Long): ExerciseSessionEntity?

    @Insert
    suspend fun insertMeasurement(measurement: BodyMeasurementEntity): Long

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)

    @Query("SELECT * FROM body_measurements ORDER BY recordedAtEpochMillis")
    fun observeMeasurements(): Flow<List<BodyMeasurementEntity>>

    @Query(
        """
        SELECT es.workoutSessionId AS sessionId, es.exerciseId, es.exerciseName,
               es.trackingMode,
               ws.completedAtEpochMillis, es.targetSets, es.targetReps,
               es.targetWeightGrams, es.progressionAction
        FROM exercise_sessions es
        JOIN workout_sessions ws ON ws.id = es.workoutSessionId
        WHERE ws.status != 'ACTIVE' AND ws.completedAtEpochMillis IS NOT NULL
        ORDER BY ws.completedAtEpochMillis
        """,
    )
    fun observeExerciseProgress(): Flow<List<ExerciseProgressPoint>>

    @Query("DELETE FROM set_records")
    suspend fun deleteAllSets()

    @Query("DELETE FROM exercise_sessions")
    suspend fun deleteAllExerciseSessions()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllWorkoutSessions()

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAllMeasurements()

    @Query("DELETE FROM accessory_assignments")
    suspend fun deleteAllAccessories()

    @Query("DELETE FROM workout_core_slots")
    suspend fun deleteAllWorkoutCoreSlots()

    @Query("DELETE FROM core_slots")
    suspend fun deleteAllCoreSlots()

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()
}
