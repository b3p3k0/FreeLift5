package org.freelift5.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineEngineTest {
    @Test
    fun deadliftDefaultsToOneSetAndTenPoundIncrement() {
        val prescription = RoutineEngine.defaultPrescription(
            CoreSlot.DEADLIFT,
            UnitSystem.POUNDS,
        )

        assertEquals(1, prescription.sets)
        assertEquals(5, prescription.reps)
        assertEquals(
            10.0,
            WeightMath.fromGrams(prescription.incrementGrams, UnitSystem.POUNDS),
            0.01,
        )
    }
}

