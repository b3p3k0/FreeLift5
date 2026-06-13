package org.freelift5.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInProgramsTest {

    @Test
    fun everyProgramIsWellFormed() {
        assertTrue(BuiltInPrograms.all.isNotEmpty())
        val ids = BuiltInPrograms.all.map { it.id }
        assertEquals("program ids must be unique", ids.size, ids.toSet().size)

        BuiltInPrograms.all.forEach { program ->
            assertTrue("${program.id} has no days", program.days.isNotEmpty())
            val dayKeys = program.days.map { it.key }
            assertEquals("${program.id} day keys must be unique", dayKeys.size, dayKeys.toSet().size)

            program.days.forEach { day ->
                day.coreSlots.forEach { slot ->
                    assertNotNull(
                        "${program.id}/${day.key} slot references unknown exercise ${slot.exerciseId}",
                        BuiltInPrograms.Catalog.byId(slot.exerciseId),
                    )
                    assertTrue("${program.id} pound increment must be positive", slot.incrementPounds > 0.0)
                    assertTrue("${program.id} kg increment must be positive", slot.incrementKilograms > 0.0)
                    assertTrue("${program.id} needs at least one work set", slot.setScheme.workSets >= 1)
                    assertTrue("${program.id} needs a positive rep target", slot.setScheme.targetReps >= 1)
                }
                day.accessories.forEach { accessory ->
                    assertNotNull(
                        "${program.id}/${day.key} accessory references unknown exercise ${accessory.exerciseId}",
                        BuiltInPrograms.Catalog.byId(accessory.exerciseId),
                    )
                }
            }
        }
    }

    @Test
    fun defaultProgramIsResolvable() {
        assertEquals("original", BuiltInPrograms.DEFAULT_ID)
        assertEquals(BuiltInPrograms.DEFAULT_ID, BuiltInPrograms.byId(BuiltInPrograms.DEFAULT_ID).id)
    }

    @Test
    fun unknownProgramFallsBackToOriginal() {
        assertEquals("original", BuiltInPrograms.byId("does-not-exist").id)
    }

    @Test
    fun catalogMatchesRoutineEngineBuiltIns() {
        CoreSlot.entries.forEach { slot ->
            val fromEngine = RoutineEngine.builtInExercises.getValue(slot)
            val fromCatalog = BuiltInPrograms.Catalog.byId(fromEngine.id)
            assertNotNull("catalog missing ${fromEngine.id}", fromCatalog)
            assertEquals(fromEngine.name, fromCatalog!!.name)
            assertEquals(slot, fromCatalog.builtInSlot)
        }
    }

    @Test
    fun originalSharesOneSquatSlotAcrossBothDays() {
        val original = BuiltInPrograms.byId("original")
        val squatDays = original.days.filter { day ->
            day.coreSlots.any { it.canonicalSlot == CoreSlot.SQUAT.name }
        }
        assertEquals(listOf("A", "B"), squatDays.map { it.key })
        squatDays.forEach { day ->
            val squat = day.coreSlots.first { it.canonicalSlot == CoreSlot.SQUAT.name }
            assertTrue("Squat must be flagged shared to dedupe to one slot", squat.sharedAcrossDays)
        }
    }

    @Test
    fun rotationCyclesThroughDays() {
        val original = BuiltInPrograms.byId("original")
        assertEquals("B", original.nextDayKey("A"))
        assertEquals("A", original.nextDayKey("B"))
        assertEquals("A", original.firstDayKey)

        val plus = BuiltInPrograms.byId("plus")
        assertEquals(listOf("A", "B", "C"), plus.days.map { it.key })
        assertEquals("B", plus.nextDayKey("A"))
        assertEquals("C", plus.nextDayKey("B"))
        assertEquals("A", plus.nextDayKey("C"))

        // Synthetic four-day program proves rotation is not hard-coded to two or three days.
        val fourDay = ProgramDefinition(
            id = "synthetic-4",
            name = "Synthetic",
            summary = "test",
            days = listOf("A", "B", "C", "D").map { key ->
                WorkoutDay(key, "Day $key", coreSlots = listOf(barbellSquat()))
            },
        )
        assertEquals("D", fourDay.nextDayKey("C"))
        assertEquals("A", fourDay.nextDayKey("D"))
        assertEquals("A", fourDay.nextDayKey("unknown"))
    }

    @Test
    fun addingALinearProgramIsDataOnly() {
        // Acceptance test for the framework: a new program is just data and is well-formed
        // with no engine change. It reuses the existing catalog.
        val custom = ProgramDefinition(
            id = "custom-3x5",
            name = "Custom 3x5",
            summary = "demo",
            days = listOf(
                WorkoutDay(
                    key = "A",
                    label = "Workout A",
                    coreSlots = listOf(
                        barbellSquat(),
                        SlotDef(
                            canonicalSlot = CoreSlot.BENCH_PRESS.name,
                            exerciseId = BuiltInPrograms.Catalog.BENCH.id,
                            setScheme = SetScheme.Straight(3, 5),
                            incrementPounds = 5.0,
                            incrementKilograms = 2.5,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(3, custom.days.first().coreSlots[1].setScheme.workSets)
        assertNotNull(BuiltInPrograms.Catalog.byId(custom.days.first().coreSlots[0].exerciseId))
    }

    @Test
    fun linearPolicyDefaultsMatchEngineBehaviour() {
        val policy = ProgressionPolicy.LinearPerWorkout()
        assertEquals(3, policy.deloadAfter)
        assertEquals(0.90, policy.deloadFactor, 0.0)

        // Passing the policy values to the engine reproduces the default deload decision.
        val weight = WeightMath.toGrams(100.0, UnitSystem.POUNDS)
        val increment = WeightMath.toGrams(5.0, UnitSystem.POUNDS)
        val missed = (1..5).map { number ->
            SetPerformance(number, targetReps = 5, actualReps = 3, targetWeightGrams = weight, actualWeightGrams = weight)
        }
        val decision = ProgressionEngine.evaluate(
            state = ProgressionState(weight, consecutiveFailures = policy.deloadAfter - 1),
            prescription = ExercisePrescription(sets = 5, reps = 5, incrementGrams = increment),
            sets = missed,
            deloadRoundingIncrementGrams = increment,
            deloadAfter = policy.deloadAfter,
            deloadFactor = policy.deloadFactor,
        )
        assertEquals(ProgressionAction.DELOAD_SUGGESTED, decision.action)
        assertSame(ProgressionAction.DELOAD_SUGGESTED, decision.action)
        assertNotNull(decision.suggestedDeloadGrams)
    }

    private fun barbellSquat() = SlotDef(
        canonicalSlot = CoreSlot.SQUAT.name,
        exerciseId = BuiltInPrograms.Catalog.SQUAT.id,
        setScheme = SetScheme.Straight(5, 5),
        incrementPounds = 5.0,
        incrementKilograms = 2.5,
    )
}
