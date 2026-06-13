package org.freelift5.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FreeLiftDatabase::class.java,
    )

    @Test
    fun migratesVersionOneToVersionTwo() {
        helper.createDatabase(DATABASE_NAME, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO exercises
                    (id, name, trackingMode, builtInSlot, notes, archived)
                VALUES
                    ('custom', 'Plank', 'TIME', NULL, '', 0)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            FreeLiftDatabase.MIGRATION_1_2,
        ).use { database ->
            database.query(
                "SELECT trackingMode, targetIncrement FROM exercise_sessions LIMIT 1",
            ).close()
        }
    }

    @Test
    fun migratesVersionTwoToVersionThree() {
        helper.createDatabase(DATABASE_NAME, 2).use { database ->
            database.execSQL(
                """
                INSERT INTO exercises
                    (id, name, trackingMode, builtInSlot, notes, archived)
                VALUES
                    ('curl', 'Barbell Curl', 'WEIGHT', NULL, '', 0)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO accessory_assignments
                    (id, workoutType, exerciseId, orderIndex, sets, target,
                     currentWeightGrams, incrementGrams, targetIncrement,
                     progressionEverySuccesses, successfulSessions, restSeconds)
                VALUES
                    ('acc1', 'A', 'curl', 0, 3, 8, 0, 2268, 1, 1, 0, 180)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            FreeLiftDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query(
                "SELECT required FROM accessory_assignments WHERE id = 'acc1'",
            ).use { cursor ->
                cursor.moveToFirst()
                check(cursor.getInt(0) == 0) { "required must default to 0 after migration" }
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
