package org.freelift5.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupCalculatorTest {
    @Test
    fun warmupsStayBelowWorkWeightAndStartWithBar() {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        val work = WeightMath.toGrams(135.0, UnitSystem.POUNDS)
        val increment = WeightMath.toGrams(5.0, UnitSystem.POUNDS)

        val sets = WarmupCalculator.calculate(work, bar, increment)

        assertTrue(sets.isNotEmpty())
        assertTrue(sets.first().weightGrams == bar)
        assertTrue(sets.all { it.weightGrams < work })
    }
}

