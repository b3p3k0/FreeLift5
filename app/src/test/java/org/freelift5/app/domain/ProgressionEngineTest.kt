package org.freelift5.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionEngineTest {
    private val current = WeightMath.toGrams(135.0, UnitSystem.POUNDS)
    private val increment = WeightMath.toGrams(5.0, UnitSystem.POUNDS)
    private val prescription = ExercisePrescription(sets = 5, reps = 5, incrementGrams = increment)

    @Test
    fun completeSuccessIncreasesWeightAndClearsFailures() {
        val decision = ProgressionEngine.evaluate(
            state = ProgressionState(current, consecutiveFailures = 2),
            prescription = prescription,
            sets = performances(5, 5, 5, 5, 5),
            deloadRoundingIncrementGrams = increment,
        )

        assertEquals(ProgressionAction.INCREASE, decision.action)
        assertEquals(current + increment, decision.nextWeightGrams)
        assertEquals(0, decision.consecutiveFailures)
        assertNull(decision.suggestedDeloadGrams)
    }

    @Test
    fun missedSetRepeatsAndCountsFailure() {
        val decision = ProgressionEngine.evaluate(
            state = ProgressionState(current),
            prescription = prescription,
            sets = performances(5, 5, 3, 5, 5),
            deloadRoundingIncrementGrams = increment,
        )

        assertEquals(ProgressionAction.REPEAT, decision.action)
        assertEquals(current, decision.nextWeightGrams)
        assertEquals(1, decision.consecutiveFailures)
    }

    @Test
    fun thirdFailureSuggestsTenPercentDeloadRoundedDown() {
        val decision = ProgressionEngine.evaluate(
            state = ProgressionState(current, consecutiveFailures = 2),
            prescription = prescription,
            sets = performances(5, 4, 5, 5, 5),
            deloadRoundingIncrementGrams = increment,
        )

        assertEquals(ProgressionAction.DELOAD_SUGGESTED, decision.action)
        assertEquals(
            120.0,
            WeightMath.fromGrams(decision.suggestedDeloadGrams!!, UnitSystem.POUNDS),
            0.01,
        )
    }

    @Test
    fun incompleteExerciseRetainsProgressWithoutAddingFailure() {
        val decision = ProgressionEngine.evaluate(
            state = ProgressionState(current, consecutiveFailures = 1),
            prescription = prescription,
            sets = performances(5, 5),
            deloadRoundingIncrementGrams = increment,
        )

        assertEquals(ProgressionAction.RETAIN_INCOMPLETE, decision.action)
        assertEquals(1, decision.consecutiveFailures)
    }

    private fun performances(vararg repetitions: Int): List<SetPerformance> =
        repetitions.mapIndexed { index, reps ->
            SetPerformance(
                setNumber = index + 1,
                targetReps = 5,
                actualReps = reps,
                targetWeightGrams = current,
                actualWeightGrams = current,
            )
        }
}

