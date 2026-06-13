package org.freelift5.app.ui.progress

import org.freelift5.app.data.ExerciseSessionEntity
import org.freelift5.app.data.ExerciseSessionWithSets
import org.freelift5.app.data.SetRecordEntity
import org.freelift5.app.data.WorkoutSessionEntity
import org.freelift5.app.data.WorkoutSessionWithExercises
import org.freelift5.app.domain.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressMetricsTest {
    @Test
    fun usesHeaviestLoadCompletedAcrossEveryPrescribedSet() {
        val history = listOf(
            workout(weights = listOf(50_000L, 50_000L, 50_000L, 50_000L, 50_000L)),
            workout(weights = listOf(60_000L, 60_000L, 60_000L, 60_000L, 57_500L)),
            workout(weights = listOf(55_000L, 55_000L, 55_000L, 55_000L, 55_000L)),
        )

        assertEquals(
            WeightPersonalRecord(weightGrams = 57_500L, sets = 5, reps = 5),
            weightPersonalRecord(history, "Back Squat"),
        )
    }

    @Test
    fun excludesIncompleteOrMissedPrescriptions() {
        val incomplete = workout(weights = listOf(70_000L, 70_000L, 70_000L, 70_000L))
        val missed = workout(
            weights = List(5) { 70_000L },
            reps = listOf(5, 5, 4, 5, 5),
        )

        assertNull(weightPersonalRecord(listOf(incomplete, missed), "Back Squat"))
    }

    private fun workout(
        weights: List<Long>,
        reps: List<Int> = List(weights.size) { 5 },
    ): WorkoutSessionWithExercises {
        val exercise = ExerciseSessionEntity(
            id = 1,
            workoutSessionId = 1,
            exerciseId = "squat",
            exerciseName = "Back Squat",
            trackingMode = TrackingMode.WEIGHT.name,
            coreSlotKey = "SQUAT",
            accessoryAssignmentId = null,
            orderIndex = 0,
            targetSets = 5,
            targetReps = 5,
            targetWeightGrams = 50_000L,
            incrementGrams = 2_500L,
            successfulRestSeconds = 180,
            failedRestSeconds = 300,
        )
        return WorkoutSessionWithExercises(
            session = WorkoutSessionEntity(
                id = 1,
                workoutType = "A",
                startedAtEpochMillis = 1,
                completedAtEpochMillis = 2,
                status = "COMPLETED",
            ),
            exercises = listOf(
                ExerciseSessionWithSets(
                    exercise = exercise,
                    sets = weights.mapIndexed { index, weight ->
                        SetRecordEntity(
                            id = index.toLong() + 1,
                            exerciseSessionId = exercise.id,
                            setNumber = index + 1,
                            isWarmup = false,
                            targetReps = 5,
                            actualReps = reps[index],
                            targetWeightGrams = exercise.targetWeightGrams,
                            actualWeightGrams = weight,
                            completedAtEpochMillis = index.toLong(),
                        )
                    },
                ),
            ),
        )
    }
}
