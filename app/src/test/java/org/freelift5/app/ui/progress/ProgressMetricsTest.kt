package org.freelift5.app.ui.progress

import java.time.ZoneOffset
import org.freelift5.app.data.AppSettings
import org.freelift5.app.data.BodyMeasurementEntity
import org.freelift5.app.data.ExerciseProgressPoint
import org.freelift5.app.data.ExerciseSessionEntity
import org.freelift5.app.data.ExerciseSessionWithSets
import org.freelift5.app.data.SetRecordEntity
import org.freelift5.app.data.WorkoutSessionEntity
import org.freelift5.app.data.WorkoutSessionWithExercises
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.ui.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun bodyWeightChartPointsCarryMeasurementDatesAndUnits() {
        val firstDay = epochDay(0)
        val secondDay = epochDay(1)
        val measurements = listOf(
            BodyMeasurementEntity(
                id = 2,
                recordedAtEpochMillis = secondDay,
                bodyWeightGrams = WeightMath.toGrams(202.5, UnitSystem.POUNDS),
            ),
            BodyMeasurementEntity(
                id = 1,
                recordedAtEpochMillis = firstDay,
                bodyWeightGrams = WeightMath.toGrams(200.0, UnitSystem.POUNDS),
            ),
        )

        val points = bodyWeightChartPoints(measurements, UnitSystem.POUNDS)

        assertEquals(listOf(firstDay, secondDay), points.map(ChartPoint::timestampMillis))
        assertEquals("200 lb", weightValueFormatter(UnitSystem.POUNDS)(points.first().value))
    }

    @Test
    fun exerciseChartPointsCarryWorkoutDates() {
        val completedAt = epochDay(2)
        val point = progressPoint(
            sessionId = 7,
            completedAt = completedAt,
            targetWeightGrams = WeightMath.toGrams(135.0, UnitSystem.POUNDS),
        )
        val state = AppUiState(
            settings = AppSettings(unitSystem = UnitSystem.POUNDS),
            history = listOf(workout(sessionId = 7, completedAt = completedAt)),
        )

        val points = exerciseMetricChartPoints(
            ExerciseMetric.WORK_WEIGHT,
            listOf(point),
            state,
        )

        assertEquals(listOf(completedAt), points.map(ChartPoint::timestampMillis))
        assertEquals("135 lb", exerciseValueFormatter(ExerciseMetric.WORK_WEIGHT, UnitSystem.POUNDS)(points.first().value))
    }

    @Test
    fun chartDateLabelsUseCompactFirstMiddleLastDates() {
        assertEquals(
            listOf("Jan 1"),
            chartDateLabels(listOf(ChartPoint(epochDay(0), 1.0)), ZoneOffset.UTC),
        )
        assertEquals(
            listOf("Jan 1", "Jan 2"),
            chartDateLabels(
                listOf(
                    ChartPoint(epochDay(0), 1.0),
                    ChartPoint(epochDay(1), 2.0),
                ),
                ZoneOffset.UTC,
            ),
        )
        assertEquals(
            listOf("Jan 1", "Jan 2", "Jan 4"),
            chartDateLabels(
                listOf(
                    ChartPoint(epochDay(0), 1.0),
                    ChartPoint(epochDay(1), 2.0),
                    ChartPoint(epochDay(2), 3.0),
                    ChartPoint(epochDay(3), 4.0),
                ),
                ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun yAxisAndMetricFormattersIncludeUnits() {
        val axis = chartAxisLabels(
            listOf(
                ChartPoint(epochDay(0), 100.0),
                ChartPoint(epochDay(1), 200.0),
            ),
            weightValueFormatter(UnitSystem.POUNDS),
        )

        assertEquals(ChartAxisLabels(min = "100 lb", mid = "150 lb", max = "200 lb"), axis)
        assertEquals(
            "135 lb",
            exerciseValueFormatter(ExerciseMetric.ESTIMATED_ONE_REP_MAX, UnitSystem.POUNDS)(135.0),
        )
        assertEquals(
            "1.3k lb-reps",
            exerciseValueFormatter(ExerciseMetric.VOLUME, UnitSystem.POUNDS)(1_250.0),
        )
        assertEquals(
            "1.24x BW",
            exerciseValueFormatter(ExerciseMetric.RELATIVE_STRENGTH, UnitSystem.POUNDS)(1.237),
        )
    }

    @Test
    fun relativeStrengthOmitsPointsWithoutPriorBodyWeight() {
        val completedAt = epochDay(2)
        val state = AppUiState(
            settings = AppSettings(unitSystem = UnitSystem.POUNDS),
            measurements = listOf(
                BodyMeasurementEntity(
                    id = 1,
                    recordedAtEpochMillis = epochDay(3),
                    bodyWeightGrams = WeightMath.toGrams(200.0, UnitSystem.POUNDS),
                ),
            ),
        )

        val points = exerciseMetricChartPoints(
            ExerciseMetric.RELATIVE_STRENGTH,
            listOf(progressPoint(completedAt = completedAt)),
            state,
        )

        assertTrue(points.isEmpty())
    }

    private fun workout(
        weights: List<Long>,
        reps: List<Int> = List(weights.size) { 5 },
        sessionId: Long = 1,
        completedAt: Long = 2,
    ): WorkoutSessionWithExercises {
        val exercise = ExerciseSessionEntity(
            id = 1,
            workoutSessionId = sessionId,
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
                id = sessionId,
                workoutType = "A",
                startedAtEpochMillis = 1,
                completedAtEpochMillis = completedAt,
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

    private fun workout(
        sessionId: Long,
        completedAt: Long,
    ): WorkoutSessionWithExercises = workout(
        weights = List(5) { WeightMath.toGrams(135.0, UnitSystem.POUNDS) },
        sessionId = sessionId,
        completedAt = completedAt,
    )

    private fun progressPoint(
        sessionId: Long = 1,
        completedAt: Long = epochDay(1),
        targetWeightGrams: Long = WeightMath.toGrams(135.0, UnitSystem.POUNDS),
    ) = ExerciseProgressPoint(
        sessionId = sessionId,
        exerciseId = "squat",
        exerciseName = "Back Squat",
        trackingMode = TrackingMode.WEIGHT.name,
        completedAtEpochMillis = completedAt,
        targetSets = 5,
        targetReps = 5,
        targetWeightGrams = targetWeightGrams,
        progressionAction = null,
    )

    private fun epochDay(day: Long): Long = day * 24L * 60L * 60L * 1_000L
}
