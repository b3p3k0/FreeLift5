package org.freelift5.app.domain

object ProgressionEngine {
    private const val FAILURES_BEFORE_DELOAD = 3
    private const val DELOAD_FACTOR = 0.90

    fun evaluate(
        state: ProgressionState,
        prescription: ExercisePrescription,
        sets: List<SetPerformance>,
        deloadRoundingIncrementGrams: Long,
    ): ProgressionDecision {
        require(deloadRoundingIncrementGrams > 0) {
            "Deload rounding increment must be positive."
        }

        if (sets.size < prescription.sets) {
            return ProgressionDecision(
                action = ProgressionAction.RETAIN_INCOMPLETE,
                nextWeightGrams = state.currentWeightGrams,
                consecutiveFailures = state.consecutiveFailures,
                reason = "The exercise was not fully completed.",
            )
        }

        val requiredSets = sets
            .sortedBy(SetPerformance::setNumber)
            .take(prescription.sets)
        val successful = requiredSets.all { set ->
            set.actualReps >= prescription.reps &&
                set.actualWeightGrams >= state.currentWeightGrams
        }

        if (successful) {
            return ProgressionDecision(
                action = ProgressionAction.INCREASE,
                nextWeightGrams = state.currentWeightGrams + prescription.incrementGrams,
                consecutiveFailures = 0,
                reason = "Every prescribed set reached its target.",
            )
        }

        val failures = state.consecutiveFailures + 1
        if (failures >= FAILURES_BEFORE_DELOAD) {
            val suggested = WeightMath.roundDown(
                state.currentWeightGrams * DELOAD_FACTOR,
                deloadRoundingIncrementGrams,
            )
            return ProgressionDecision(
                action = ProgressionAction.DELOAD_SUGGESTED,
                nextWeightGrams = state.currentWeightGrams,
                consecutiveFailures = failures,
                suggestedDeloadGrams = suggested,
                reason = "Three consecutive workouts missed this prescription.",
            )
        }

        return ProgressionDecision(
            action = ProgressionAction.REPEAT,
            nextWeightGrams = state.currentWeightGrams,
            consecutiveFailures = failures,
            reason = "One or more prescribed sets missed the target.",
        )
    }
}

