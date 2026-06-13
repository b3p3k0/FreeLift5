package org.freelift5.app.ui.progress

import org.freelift5.app.data.SetRecordEntity
import org.freelift5.app.data.WorkoutSessionWithExercises
import org.freelift5.app.domain.TrackingMode

internal data class WeightPersonalRecord(
    val weightGrams: Long,
    val sets: Int,
    val reps: Int,
)

internal fun weightPersonalRecord(
    history: List<WorkoutSessionWithExercises>,
    exerciseName: String,
): WeightPersonalRecord? = history
    .asSequence()
    .flatMap { it.exercises.asSequence() }
    .filter {
        it.exercise.exerciseName == exerciseName &&
            it.exercise.trackingMode == TrackingMode.WEIGHT.name
    }
    .mapNotNull { relation ->
        val exercise = relation.exercise
        val prescribedSets = relation.sets
            .filterNot(SetRecordEntity::isWarmup)
            .associateBy(SetRecordEntity::setNumber)
        val completedSets = (1..exercise.targetSets).mapNotNull(prescribedSets::get)
        if (
            completedSets.size != exercise.targetSets ||
            completedSets.any {
                it.actualReps < it.targetReps || it.actualWeightGrams <= 0L
            }
        ) {
            null
        } else {
            WeightPersonalRecord(
                weightGrams = completedSets.minOf(SetRecordEntity::actualWeightGrams),
                sets = exercise.targetSets,
                reps = exercise.targetReps,
            )
        }
    }
    .maxByOrNull(WeightPersonalRecord::weightGrams)
