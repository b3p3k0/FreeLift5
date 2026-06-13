package org.freelift5.app.domain

import kotlin.math.roundToInt

object PlateCalculator {
    private const val TICKS_PER_UNIT = 4

    fun calculate(
        targetWeightGrams: Long,
        barWeightGrams: Long,
        unitSystem: UnitSystem,
    ): PlateCalculation {
        val standardPlates = standardPlateValues(unitSystem)
        val targetTicks = toTicks(targetWeightGrams, unitSystem)
        val barTicks = toTicks(barWeightGrams, unitSystem)
        val plateTicks = standardPlates.map { (it * TICKS_PER_UNIT).roundToInt() }
        val perSideTarget = (targetTicks - barTicks) / 2.0

        if (perSideTarget < 0) {
            val barOnly = PlateLoad(emptyList(), barWeightGrams)
            return PlateCalculation(null, null, barOnly)
        }

        val maxTicks = perSideTarget.toInt() + (plateTicks.maxOrNull() ?: 0) + 1
        val combinations = minimumPlateCombinations(maxTicks, plateTicks)
        val exactTicks = perSideTarget.toInt().takeIf {
            perSideTarget % 1.0 == 0.0 && combinations[it] != null
        }
        val lowerTicks = (perSideTarget.toInt() downTo 0)
            .firstOrNull { combinations[it] != null }
        val higherTicks = (kotlin.math.ceil(perSideTarget).toInt()..maxTicks)
            .firstOrNull { combinations[it] != null }

        fun loadFor(ticks: Int?): PlateLoad? {
            ticks ?: return null
            val plates = combinations[ticks]?.sortedDescending() ?: return null
            val plateGrams = plates.map { plateTick ->
                WeightMath.toGrams(plateTick.toDouble() / TICKS_PER_UNIT, unitSystem)
            }
            val totalTicks = barTicks + ticks * 2
            return PlateLoad(
                plateGramsPerSide = plateGrams,
                totalWeightGrams = WeightMath.toGrams(
                    totalTicks.toDouble() / TICKS_PER_UNIT,
                    unitSystem,
                ),
            )
        }

        return PlateCalculation(
            exact = loadFor(exactTicks),
            nearestLower = loadFor(lowerTicks),
            nearestHigher = loadFor(higherTicks),
        )
    }

    fun standardPlateGrams(unitSystem: UnitSystem): List<Long> =
        standardPlateValues(unitSystem).map { WeightMath.toGrams(it, unitSystem) }

    private fun standardPlateValues(unitSystem: UnitSystem): List<Double> =
        if (unitSystem == UnitSystem.POUNDS) {
            listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)
        } else {
            listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        }

    private fun toTicks(grams: Long, unitSystem: UnitSystem): Int =
        (WeightMath.fromGrams(grams, unitSystem) * TICKS_PER_UNIT).roundToInt()

    private fun minimumPlateCombinations(
        maxTicks: Int,
        plateTicks: List<Int>,
    ): List<List<Int>?> {
        val result = MutableList<List<Int>?>(maxTicks + 1) { null }
        result[0] = emptyList()
        for (ticks in 1..maxTicks) {
            result[ticks] = plateTicks.mapNotNull { plate ->
                if (plate > ticks) return@mapNotNull null
                result[ticks - plate]?.plus(plate)
            }.minByOrNull(List<Int>::size)
        }
        return result
    }
}
