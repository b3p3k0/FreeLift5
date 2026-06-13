package org.freelift5.app.domain

import kotlin.math.floor
import kotlin.math.roundToLong

object WeightMath {
    const val GRAMS_PER_KILOGRAM = 1_000.0
    const val GRAMS_PER_POUND = 453.59237
    private const val GRAM_EPSILON = 1.0

    fun toGrams(value: Double, unitSystem: UnitSystem): Long {
        val multiplier = when (unitSystem) {
            UnitSystem.POUNDS -> GRAMS_PER_POUND
            UnitSystem.KILOGRAMS -> GRAMS_PER_KILOGRAM
        }
        return (value * multiplier).roundToLong()
    }

    fun fromGrams(grams: Long, unitSystem: UnitSystem): Double {
        val divisor = when (unitSystem) {
            UnitSystem.POUNDS -> GRAMS_PER_POUND
            UnitSystem.KILOGRAMS -> GRAMS_PER_KILOGRAM
        }
        return grams / divisor
    }

    fun epleyEstimate(weightGrams: Long, repetitions: Int): Long {
        require(repetitions in 1..10) { "Repetitions must be between 1 and 10." }
        return (weightGrams * (1.0 + repetitions / 30.0)).roundToLong()
    }

    fun suggestedStartingWeight(
        estimatedOneRepMaxGrams: Long,
        loadableIncrementGrams: Long,
    ): Long {
        require(loadableIncrementGrams > 0) { "Loadable increment must be positive." }
        return roundDown(estimatedOneRepMaxGrams * 0.60, loadableIncrementGrams)
    }

    fun roundDown(valueGrams: Double, incrementGrams: Long): Long {
        require(incrementGrams > 0) { "Increment must be positive." }
        // Increments are quantized to whole grams, so an exact multiple can land a
        // fraction of a gram below the boundary; tolerate that so floor does not drop
        // a full increment (e.g. a 150 lb 1RM suggests 90 lb, not 85).
        return floor((valueGrams + GRAM_EPSILON) / incrementGrams).toLong() * incrementGrams
    }

    fun format(grams: Long, unitSystem: UnitSystem): String {
        val value = fromGrams(grams, unitSystem)
        val rounded = (value * 4.0).roundToLong() / 4.0
        val text = if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
        val suffix = if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"
        return "$text $suffix"
    }
}

