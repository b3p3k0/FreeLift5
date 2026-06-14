package org.freelift5.app.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferencesTest {
    @Test
    fun persistedIdsFallBackSafelyByBrightness() {
        assertEquals(
            AppThemeId.SOLARIZED_LIGHT,
            AppThemeId.lightFromPersisted(null),
        )
        assertEquals(
            AppThemeId.SOLARIZED_LIGHT,
            AppThemeId.lightFromPersisted("foundry"),
        )
        assertEquals(
            AppThemeId.SOLARIZED_DARK,
            AppThemeId.darkFromPersisted("pool_tile"),
        )
        assertEquals(
            AppThemeId.SOLARIZED_DARK,
            AppThemeId.darkFromPersisted("unknown"),
        )
        assertEquals(
            ThemeBehavior.FOLLOW_SYSTEM,
            ThemeBehavior.fromPersisted("unknown"),
        )
    }

    @Test
    fun themeIdsExposeStableUniqueStorageValues() {
        assertEquals(
            AppThemeId.entries.size,
            AppThemeId.entries.map(AppThemeId::persistedId).toSet().size,
        )
        assertTrue(AppThemeId.SOLARIZED_DARK.isDark)
        assertFalse(AppThemeId.SOLARIZED_LIGHT.isDark)
    }
}
