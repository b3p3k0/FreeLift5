package org.freelift5.app.data

import androidx.room.Embedded
import androidx.room.Relation

data class ExerciseSessionWithSets(
    @Embedded val exercise: ExerciseSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseSessionId",
    )
    val sets: List<SetRecordEntity>,
)

data class WorkoutSessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = ExerciseSessionEntity::class,
        parentColumn = "id",
        entityColumn = "workoutSessionId",
    )
    val exercises: List<ExerciseSessionWithSets>,
)

