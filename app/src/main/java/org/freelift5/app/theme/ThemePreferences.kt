package org.freelift5.app.theme

enum class ThemeBehavior(val persistedId: String) {
    FOLLOW_SYSTEM("follow_system"),
    FIXED("fixed"),
    ;

    companion object {
        fun fromPersisted(value: String?): ThemeBehavior =
            entries.firstOrNull { it.persistedId == value } ?: FOLLOW_SYSTEM
    }
}

enum class AppThemeId(
    val persistedId: String,
    val isDark: Boolean,
) {
    SOLARIZED_LIGHT("solarized_light", false),
    FIELD_MANUAL("field_manual", false),
    POOL_TILE("pool_tile", false),
    SOLARIZED_DARK("solarized_dark", true),
    FOUNDRY("foundry", true),
    ;

    companion object {
        fun fromPersisted(value: String?): AppThemeId? =
            entries.firstOrNull { it.persistedId == value }

        fun lightFromPersisted(value: String?): AppThemeId =
            fromPersisted(value)?.takeUnless(AppThemeId::isDark) ?: SOLARIZED_LIGHT

        fun darkFromPersisted(value: String?): AppThemeId =
            fromPersisted(value)?.takeIf(AppThemeId::isDark) ?: SOLARIZED_DARK
    }
}

data class ThemePreferences(
    val behavior: ThemeBehavior = ThemeBehavior.FOLLOW_SYSTEM,
    val lightTheme: AppThemeId = AppThemeId.SOLARIZED_LIGHT,
    val darkTheme: AppThemeId = AppThemeId.SOLARIZED_DARK,
    val fixedTheme: AppThemeId = AppThemeId.SOLARIZED_LIGHT,
) {
    fun resolvedTheme(systemDark: Boolean): AppThemeId = when (behavior) {
        ThemeBehavior.FOLLOW_SYSTEM -> if (systemDark) darkTheme else lightTheme
        ThemeBehavior.FIXED -> fixedTheme
    }
}
