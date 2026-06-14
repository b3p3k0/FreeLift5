package org.freelift5.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import org.freelift5.app.theme.AppThemeId
import org.freelift5.app.theme.ThemeBehavior
import org.freelift5.app.theme.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCatalogTest {
    @Test
    fun catalogHasFiveUniqueThemesWithExpectedBrightnessSplit() {
        assertEquals(5, ThemeCatalog.all.size)
        assertEquals(5, ThemeCatalog.all.map { it.id }.toSet().size)
        assertEquals(3, ThemeCatalog.lightThemes.size)
        assertEquals(2, ThemeCatalog.darkThemes.size)
        assertTrue(ThemeCatalog.lightThemes.none(AppThemeDefinition::isDark))
        assertTrue(ThemeCatalog.darkThemes.all(AppThemeDefinition::isDark))
    }

    @Test
    fun resolverUsesSolarizedDefaultsAndHonorsFixedMode() {
        val defaults = ThemePreferences()
        assertEquals(
            AppThemeId.SOLARIZED_LIGHT,
            ThemeCatalog.resolve(defaults, systemDark = false).id,
        )
        assertEquals(
            AppThemeId.SOLARIZED_DARK,
            ThemeCatalog.resolve(defaults, systemDark = true).id,
        )

        val fixed = defaults.copy(
            behavior = ThemeBehavior.FIXED,
            fixedTheme = AppThemeId.FOUNDRY,
        )
        assertEquals(AppThemeId.FOUNDRY, ThemeCatalog.resolve(fixed, false).id)
        assertEquals(AppThemeId.FOUNDRY, ThemeCatalog.resolve(fixed, true).id)
    }

    @Test
    fun everyThemeMeetsContrastRequirements() {
        ThemeCatalog.all.forEach { theme ->
            val scheme = theme.colorScheme
            textPairs(scheme).forEach { (name, foreground, background) ->
                assertContrast(theme.name, name, foreground, background, minimum = 4.5)
            }
            nonTextPairs(scheme).forEach { (name, foreground, background) ->
                assertContrast(theme.name, name, foreground, background, minimum = 3.0)
            }
        }
    }

    private fun textPairs(scheme: ColorScheme): List<ColorPair> = buildList {
        add(ColorPair("primary", scheme.onPrimary, scheme.primary))
        add(ColorPair("primary container", scheme.onPrimaryContainer, scheme.primaryContainer))
        add(ColorPair("secondary", scheme.onSecondary, scheme.secondary))
        add(ColorPair("secondary container", scheme.onSecondaryContainer, scheme.secondaryContainer))
        add(ColorPair("tertiary", scheme.onTertiary, scheme.tertiary))
        add(ColorPair("tertiary container", scheme.onTertiaryContainer, scheme.tertiaryContainer))
        add(ColorPair("background", scheme.onBackground, scheme.background))
        add(ColorPair("surface", scheme.onSurface, scheme.surface))
        add(ColorPair("surface variant", scheme.onSurfaceVariant, scheme.surfaceVariant))
        add(ColorPair("inverse surface", scheme.inverseOnSurface, scheme.inverseSurface))
        add(ColorPair("error", scheme.onError, scheme.error))
        add(ColorPair("error container", scheme.onErrorContainer, scheme.errorContainer))
        surfaceContainers(scheme).forEachIndexed { index, color ->
            add(ColorPair("surface container $index", scheme.onSurface, color))
        }
        fixedPairs("primary fixed", scheme.primaryFixed, scheme.primaryFixedDim, scheme.onPrimaryFixed, scheme.onPrimaryFixedVariant)
        fixedPairs("secondary fixed", scheme.secondaryFixed, scheme.secondaryFixedDim, scheme.onSecondaryFixed, scheme.onSecondaryFixedVariant)
        fixedPairs("tertiary fixed", scheme.tertiaryFixed, scheme.tertiaryFixedDim, scheme.onTertiaryFixed, scheme.onTertiaryFixedVariant)
    }

    private fun MutableList<ColorPair>.fixedPairs(
        name: String,
        fixed: Color,
        dim: Color,
        onFixed: Color,
        onFixedVariant: Color,
    ) {
        add(ColorPair(name, onFixed, fixed))
        add(ColorPair("$name dim", onFixed, dim))
        add(ColorPair("$name variant", onFixedVariant, fixed))
        add(ColorPair("$name dim variant", onFixedVariant, dim))
    }

    private fun surfaceContainers(scheme: ColorScheme): List<Color> = listOf(
        scheme.surfaceBright,
        scheme.surfaceDim,
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
    )

    private fun nonTextPairs(scheme: ColorScheme): List<ColorPair> = listOf(
        ColorPair("chart line", scheme.primary, scheme.surface),
        ColorPair("outline", scheme.outline, scheme.surface),
        ColorPair("outline variant", scheme.outlineVariant, scheme.surface),
    )

    private fun assertContrast(
        themeName: String,
        pairName: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$themeName $pairName contrast was $ratio, expected at least $minimum",
            ratio >= minimum,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun Color.luminance(): Double {
        fun channel(value: Float): Double {
            val component = value.toDouble()
            return if (component <= 0.04045) {
                component / 12.92
            } else {
                Math.pow((component + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private data class ColorPair(
        val name: String,
        val foreground: Color,
        val background: Color,
    )
}
