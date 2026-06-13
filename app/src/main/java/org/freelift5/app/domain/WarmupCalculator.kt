package org.freelift5.app.domain

object WarmupCalculator {
    private data class Step(
        val percentage: Double,
        val reps: Int,
        val repeat: Int = 1,
    )

    private val steps = listOf(
        Step(percentage = 0.0, reps = 5, repeat = 2),
        Step(percentage = 0.55, reps = 5),
        Step(percentage = 0.70, reps = 3),
        Step(percentage = 0.85, reps = 2),
    )

    fun calculate(
        workWeightGrams: Long,
        barWeightGrams: Long,
        loadableIncrementGrams: Long,
    ): List<WarmupSet> {
        require(workWeightGrams >= barWeightGrams) {
            "Work weight cannot be lighter than the bar."
        }
        require(loadableIncrementGrams > 0) { "Loadable increment must be positive." }

        val result = mutableListOf<WarmupSet>()
        steps.forEach { step ->
            val rawWeight = if (step.percentage == 0.0) {
                barWeightGrams
            } else {
                maxOf(barWeightGrams, (workWeightGrams * step.percentage).toLong())
            }
            val roundedWeight = maxOf(
                barWeightGrams,
                WeightMath.roundDown(rawWeight.toDouble(), loadableIncrementGrams),
            )
            if (roundedWeight >= workWeightGrams) return@forEach

            repeat(step.repeat) {
                if (result.lastOrNull()?.weightGrams != roundedWeight || step.repeat > 1) {
                    result += WarmupSet(roundedWeight, step.reps)
                }
            }
        }
        return result
    }
}
