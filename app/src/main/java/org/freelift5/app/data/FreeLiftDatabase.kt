package org.freelift5.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        CoreSlotEntity::class,
        WorkoutCoreSlotEntity::class,
        AccessoryAssignmentEntity::class,
        WorkoutSessionEntity::class,
        ExerciseSessionEntity::class,
        SetRecordEntity::class,
        BodyMeasurementEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class FreeLiftDatabase : RoomDatabase() {
    abstract fun dao(): FreeLiftDao

    companion object {
        fun create(context: Context): FreeLiftDatabase = Room.databaseBuilder(
            context.applicationContext,
            FreeLiftDatabase::class.java,
            "freelift5.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE accessory_assignments " +
                        "ADD COLUMN targetIncrement INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE exercise_sessions " +
                        "ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'WEIGHT'",
                )
                db.execSQL(
                    "ALTER TABLE exercise_sessions " +
                        "ADD COLUMN targetIncrement INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE exercise_sessions ADD COLUMN nextTarget INTEGER",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE accessory_assignments " +
                        "ADD COLUMN required INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
