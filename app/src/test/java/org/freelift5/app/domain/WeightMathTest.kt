package org.freelift5.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightMathTest {
    @Test
    fun epleyEstimateUsesRecentSet() {
        val estimate = WeightMath.epleyEstimate(
            weightGrams = WeightMath.toGrams(135.0, UnitSystem.POUNDS),
            repetitions = 5,
        )

        assertEquals(157.5, WeightMath.fromGrams(estimate, UnitSystem.POUNDS), 0.01)
    }

    @Test
    fun suggestedStartUsesSixtyPercentAndRoundsDown() {
        val estimate = WeightMath.toGrams(157.5, UnitSystem.POUNDS)
        val increment = WeightMath.toGrams(5.0, UnitSystem.POUNDS)

        val suggested = WeightMath.suggestedStartingWeight(estimate, increment)

        assertEquals(90.0, WeightMath.fromGrams(suggested, UnitSystem.POUNDS), 0.01)
    }

    @Test
    fun knownOneRepMaxAtExactMultipleSuggestsSixtyPercent() {
        val estimate = WeightMath.toGrams(150.0, UnitSystem.POUNDS)
        val increment = WeightMath.toGrams(5.0, UnitSystem.POUNDS)

        val suggested = WeightMath.suggestedStartingWeight(estimate, increment)

        assertEquals(90.0, WeightMath.fromGrams(suggested, UnitSystem.POUNDS), 0.01)
    }
}

