package org.freelift5.app.export

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.freelift5.app.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {
    @Test
    fun escapesCommasQuotesAndNewlines() {
        assertEquals("\"one,two\"", CsvExport.escape("one,two"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExport.escape("line1\nline2"))
    }

    @Test
    fun emptyExportsStillContainVersionedHeaders() {
        assertTrue(CsvExport.workouts(emptyList()).startsWith("schema_version"))
        assertTrue(CsvExport.measurements(emptyList()).startsWith("schema_version"))
    }

    @Test
    fun completeBundleContainsEveryDataCategory() {
        val entries = mutableSetOf<String>()
        ZipInputStream(
            ByteArrayInputStream(
                CsvExport.completeBundle(
                    workoutSessions = emptyList(),
                    measurements = emptyList(),
                    appSettings = AppSettings(),
                ),
            ),
        ).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name
            }
        }

        assertTrue("sessions-v2.csv" in entries)
        assertTrue("exercise-sessions-v2.csv" in entries)
        assertTrue("workouts-v2.csv" in entries)
        assertTrue("measurements-v2.csv" in entries)
        assertTrue("exercises-v2.csv" in entries)
        assertTrue("core-program-v2.csv" in entries)
        assertTrue("accessories-v2.csv" in entries)
        assertTrue("settings-v2.csv" in entries)
    }

    @Test
    fun settingsExportRemainsDataOnlyAndVersionTwo() {
        val settings = CsvExport.settings(AppSettings())
        assertTrue(settings.startsWith("schema_version,unit_system"))
        assertTrue(settings.lines()[1].startsWith("2,"))
        assertTrue("theme preferences stay device-local", "theme_" !in settings)
    }
}
