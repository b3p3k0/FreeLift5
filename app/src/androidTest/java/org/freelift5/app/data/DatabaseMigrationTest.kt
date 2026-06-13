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

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
