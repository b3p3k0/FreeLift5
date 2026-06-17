package org.freelift5.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.freelift5.app.FreeLiftApplication
import org.freelift5.app.MainActivity
import org.freelift5.app.domain.BuiltInPrograms
import org.freelift5.app.domain.CoreSlot
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.theme.AppThemeId
import org.freelift5.app.theme.ThemeBehavior
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: FreeLiftApplication
        get() = compose.activity.application as FreeLiftApplication

    @Before
    fun createCompletedOnboarding() {
        runBlocking {
            app.container.repository.clearAllData()
            app.container.repository.completeOnboarding(
                unitSystem = UnitSystem.POUNDS,
                birthMonth = null,
                birthYear = null,
                heightMillimeters = null,
                trainingBackground = "NEW",
                bodyWeightGrams = null,
                barWeightGrams = 20_411L,
                startingWeights = CoreSlot.entries.associateWith { 20_411L },
                programId = BuiltInPrograms.DEFAULT_ID,
            )
        }
        compose.activityRule.scenario.recreate()
        waitForTag("active-theme-solarized_light")
    }

    @Test
    fun themeSelectionAppliesImmediatelyPersistsAndClears() {
        clickNavigationItem("Settings")
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Day theme").assertIsDisplayed()
        compose.onNodeWithText("Night theme").assertIsDisplayed()

        compose.onNodeWithTag("theme-behavior-fixed").performClick()
        compose.onNodeWithText("Day theme").assertDoesNotExist()
        compose.onNodeWithText("Night theme").assertDoesNotExist()

        compose.onNodeWithTag("theme-foundry")
            .performScrollTo()
            .performClick()
        waitForTag("active-theme-foundry")
        compose.onNodeWithTag("theme-foundry").assertIsSelected()

        compose.activityRule.scenario.recreate()
        waitForTag("active-theme-foundry")
        clickNavigationItem("Settings")
        compose.onNodeWithTag("theme-foundry")
            .performScrollTo()
            .assertIsSelected()

        runBlocking {
            app.container.repository.clearAllData()
            val reset = app.container.repository.settingsSnapshot().themePreferences
            assertEquals(ThemeBehavior.FOLLOW_SYSTEM, reset.behavior)
            assertEquals(AppThemeId.SOLARIZED_LIGHT, reset.lightTheme)
            assertEquals(AppThemeId.SOLARIZED_DARK, reset.darkTheme)
        }
    }

    private fun clickNavigationItem(label: String) {
        compose.onAllNodesWithText(label)
            .filter(hasClickAction())
            .onFirst()
            .performClick()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
