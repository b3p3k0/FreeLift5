package org.freelift5.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.freelift5.app.domain.CoreSlot
import org.freelift5.app.domain.RoutineEngine
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryWorkoutTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: FreeLiftDatabase
    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: FreeLiftRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            context,
            FreeLiftDatabase::class.java,
        ).build()
        settingsStore = SettingsStore(context)
        settingsStore.clear()
        repository = FreeLiftRepository(database, settingsStore)
    }

    @After
    fun tearDown() = runBlocking {
        settingsStore.clear()
        database.close()
    }

    @Test
    fun partialWorkoutProgressesCompletedLiftAndAdvancesSequence() = runBlocking {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        repository.completeOnboarding(
            unitSystem = UnitSystem.POUNDS,
            birthMonth = null,
            birthYear = null,
            heightMillimeters = null,
            trainingBackground = "NEW",
            bodyWeightGrams = null,
            barWeightGrams = bar,
            startingWeights = CoreSlot.entries.associateWith { bar },
        )

        val sessionId = repository.startWorkout()
        val workout = database.dao().getWorkout(sessionId)
        assertNotNull(workout)
        val squat = workout!!.exercises.single {
            it.exercise.coreSlotKey == CoreSlot.SQUAT.name
        }
        repeat(5) { index ->
            repository.saveSet(
                exerciseSessionId = squat.exercise.id,
                setNumber = index + 1,
                actualReps = 5,
                actualWeightGrams = bar,
            )
        }

        repository.finishWorkout(sessionId, "instrumented partial workout")

        val saved = database.dao().getWorkout(sessionId)!!
        assertEquals("PARTIAL", saved.session.status)
        assertEquals(
            bar + RoutineEngine.defaultPrescription(
                CoreSlot.SQUAT,
                UnitSystem.POUNDS,
            ).incrementGrams,
            database.dao().getCoreSlot(CoreSlot.SQUAT.name)!!.currentWeightGrams,
        )
        assertEquals(
            bar,
            database.dao().getCoreSlot(CoreSlot.BENCH_PRESS.name)!!.currentWeightGrams,
        )
        assertEquals("B", settingsStore.settings.first().nextWorkoutDayKey)
    }

    @Test
    fun switchProgramCarriesSharedWeightAndKeepsHistory() = runBlocking {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        repository.completeOnboarding(
            unitSystem = UnitSystem.POUNDS,
            birthMonth = null,
            birthYear = null,
            heightMillimeters = null,
            trainingBackground = "NEW",
            bodyWeightGrams = null,
            barWeightGrams = bar,
            startingWeights = CoreSlot.entries.associateWith { bar },
        )

        val sessionId = repository.startWorkout()
        database.dao().getWorkout(sessionId)!!.exercises
            .filter { it.exercise.coreSlotKey != null }
            .forEach { relation ->
                repeat(relation.exercise.targetSets) { index ->
                    repository.saveSet(
                        relation.exercise.id,
                        index + 1,
                        relation.exercise.targetReps,
                        relation.exercise.targetWeightGrams,
                    )
                }
            }
        repository.finishWorkout(sessionId, "")
        val squatWeight = database.dao().getCoreSlot(CoreSlot.SQUAT.name)!!.currentWeightGrams
        val historyBefore = repository.history.first().size

        repository.switchProgram("lite")

        val squat = database.dao().getCoreSlot(CoreSlot.SQUAT.name)!!
        assertEquals(squatWeight, squat.currentWeightGrams) // shared weight carried
        assertEquals(2, squat.sets)                         // lite is 2x5
        assertEquals(historyBefore, repository.history.first().size) // history intact
        assertEquals("lite", settingsStore.settings.first().activeProgramId)
        assertEquals("A", settingsStore.settings.first().nextWorkoutDayKey)
    }

    @Test
    fun skippingRequiredAccessoryMakesWorkoutPartial() = runBlocking {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        repository.completeOnboarding(
            unitSystem = UnitSystem.POUNDS,
            birthMonth = null,
            birthYear = null,
            heightMillimeters = null,
            trainingBackground = "NEW",
            bodyWeightGrams = null,
            barWeightGrams = bar,
            startingWeights = CoreSlot.entries.associateWith { bar },
            programId = "plus",
        )

        val sessionId = repository.startWorkout() // day A: cores + required accessories
        database.dao().getWorkout(sessionId)!!.exercises
            .filter { it.exercise.coreSlotKey != null }
            .forEach { relation ->
                repeat(relation.exercise.targetSets) { index ->
                    repository.saveSet(
                        relation.exercise.id,
                        index + 1,
                        relation.exercise.targetReps,
                        relation.exercise.targetWeightGrams,
                    )
                }
            }
        // Every core lift is complete, but the required accessories were skipped.
        repository.finishWorkout(sessionId, "")

        assertEquals("PARTIAL", database.dao().getWorkout(sessionId)!!.session.status)
    }

    @Test
    fun threeDayProgramRotatesThroughEveryDay() = runBlocking {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        repository.completeOnboarding(
            unitSystem = UnitSystem.POUNDS,
            birthMonth = null,
            birthYear = null,
            heightMillimeters = null,
            trainingBackground = "NEW",
            bodyWeightGrams = null,
            barWeightGrams = bar,
            startingWeights = CoreSlot.entries.associateWith { bar },
            programId = "plus",
        )
        assertEquals("A", settingsStore.settings.first().nextWorkoutDayKey)

        repository.finishWorkout(repository.startWorkout(), "")
        assertEquals("B", settingsStore.settings.first().nextWorkoutDayKey)
        repository.finishWorkout(repository.startWorkout(), "")
        assertEquals("C", settingsStore.settings.first().nextWorkoutDayKey)
        repository.finishWorkout(repository.startWorkout(), "")
        assertEquals("A", settingsStore.settings.first().nextWorkoutDayKey)
    }

    @Test
    fun accessoryProgressionRaisesTimedTarget() = runBlocking {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        repository.completeOnboarding(
            UnitSystem.POUNDS,
            null,
            null,
            null,
            "NEW",
            null,
            bar,
            CoreSlot.entries.associateWith { bar },
        )
        repository.addAccessory(
            name = "Plank ${UUID.randomUUID()}",
            trackingMode = org.freelift5.app.domain.TrackingMode.TIME,
            workoutTypes = setOf("A"),
            sets = 1,
            target = 30,
            startingWeightGrams = 0,
            incrementGrams = 0,
            targetIncrement = 5,
            progressionEverySuccesses = 1,
            restSeconds = 60,
            notes = "",
        )

        val sessionId = repository.startWorkout()
        val workout = database.dao().getWorkout(sessionId)!!
        val plank = workout.exercises.single { it.exercise.trackingMode == "TIME" }
        repository.saveSet(plank.exercise.id, 1, 30, 0)
        repository.finishWorkout(sessionId, "")

        val assignment = database.dao().getAccessory(
            plank.exercise.accessoryAssignmentId!!,
        )!!
        assertEquals(35, assignment.target)
    }

    @Test
    fun weightedAccessoryDoesNotProgressBelowTargetLoad() = runBlocking {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        val target = WeightMath.toGrams(50.0, UnitSystem.POUNDS)
        repository.completeOnboarding(
            UnitSystem.POUNDS,
            null,
            null,
            null,
            "NEW",
            null,
            bar,
            CoreSlot.entries.associateWith { bar },
        )
        repository.addAccessory(
            name = "Weighted carry ${UUID.randomUUID()}",
            trackingMode = org.freelift5.app.domain.TrackingMode.WEIGHT,
            workoutTypes = setOf("A"),
            sets = 2,
            target = 8,
            startingWeightGrams = target,
            incrementGrams = WeightMath.toGrams(5.0, UnitSystem.POUNDS),
            targetIncrement = 0,
            progressionEverySuccesses = 1,
            restSeconds = 60,
            notes = "",
        )

        val sessionId = repository.startWorkout()
        val workout = database.dao().getWorkout(sessionId)!!
        val accessory = workout.exercises.single {
            it.exercise.accessoryAssignmentId != null
        }
        repeat(2) { index ->
            repository.saveSet(
                exerciseSessionId = accessory.exercise.id,
                setNumber = index + 1,
                actualReps = 8,
                actualWeightGrams = target - 1,
            )
        }
        repository.finishWorkout(sessionId, "")

        val assignment = database.dao().getAccessory(
            accessory.exercise.accessoryAssignmentId!!,
        )!!
        val savedExercise = database.dao().getExerciseSession(accessory.exercise.id)!!
        assertEquals(target, assignment.currentWeightGrams)
        assertEquals(0, assignment.successfulSessions)
        assertEquals("REPEAT", savedExercise.progressionAction)
    }

    @Test
    fun reseedingBuiltInsDoesNotBreakExistingReferences() = runBlocking {
        val bar = WeightMath.toGrams(45.0, UnitSystem.POUNDS)
        repository.completeOnboarding(
            unitSystem = UnitSystem.POUNDS,
            birthMonth = null,
            birthYear = null,
            heightMillimeters = null,
            trainingBackground = "NEW",
            bodyWeightGrams = null,
            barWeightGrams = bar,
            startingWeights = CoreSlot.entries.associateWith { bar },
        )
        // Active session snapshots reference built-in exercises via RESTRICT foreign keys.
        repository.startWorkout()

        // Regression for the startup crash loop: app launch re-seeds built-ins. REPLACE
        // used to delete+reinsert exercise rows and trip the RESTRICT constraints from
        // core_slots and exercise_sessions; @Upsert must update in place instead.
        repository.seedBuiltInExercises()

        val coreSlots = database.dao().getAllCoreSlots()
        assertEquals(CoreSlot.entries.size, coreSlots.size)
        coreSlots.forEach { slot ->
            assertNotNull(database.dao().getExercise(slot.exerciseId))
        }
    }
}
