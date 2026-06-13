package org.freelift5.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlateCalculatorTest {
    @Test
    fun oneThirtyFiveUsesOneFortyFivePerSide() {
        val calculation = PlateCalculator.calculate(
            targetWeightGrams = WeightMath.toGrams(135.0, UnitSystem.POUNDS),
            barWeightGrams = WeightMath.toGrams(45.0, UnitSystem.POUNDS),
            unitSystem = UnitSystem.POUNDS,
        )

        assertNotNull(calculation.exact)
        val exact = calculation.exact!!
        assertEquals(1, exact.plateGramsPerSide.size)
        assertEquals(
            45.0,
            WeightMath.fromGrams(exact.plateGramsPerSide.single(), UnitSystem.POUNDS),
            0.01,
        )
    }

    @Test
    fun oneHundredUsesMinimumPlateCount() {
        val calculation = PlateCalculator.calculate(
            targetWeightGrams = WeightMath.toGrams(100.0, UnitSystem.POUNDS),
            barWeightGrams = WeightMath.toGrams(45.0, UnitSystem.POUNDS),
            unitSystem = UnitSystem.POUNDS,
        )

        assertNotNull(calculation.exact)
        val exact = calculation.exact!!
        val plates = exact.plateGramsPerSide.map {
            WeightMath.fromGrams(it, UnitSystem.POUNDS)
        }
        assertEquals(2, plates.size)
        assertEquals(25.0, plates[0], 0.01)
        assertEquals(2.5, plates[1], 0.01)
    }
}
