package org.freelift5.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.freelift5.app.BuildConfig
import org.freelift5.app.FreeLiftApplication
import org.freelift5.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetApp() {
        val app = compose.activity.application as FreeLiftApplication
        runBlocking {
            app.container.repository.clearAllData()
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun onboardingNavigationWorkoutAndRecovery() {
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Your units and optional metrics").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()

        compose.onNodeWithText("Training background").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()

        compose.onNodeWithText("Choose your program").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()

        compose.onNodeWithText("Set your starting weights").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()

        compose.onNodeWithText("Review your plan").assertIsDisplayed()
        compose.onNodeWithText("Start FreeLift5").performClick()
        waitForText("Next: Workout A")

        compose.onNodeWithText("Program", substring = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Core Program").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText("Guides", substring = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Exercise guides").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        clickNavigationItem("Progress")
        compose.onNodeWithText("Workouts").assertIsDisplayed()

        clickNavigationItem("Settings")
        compose.onNodeWithText("No account. No telemetry. No network access. Your data stays on your device.")
            .assertIsDisplayed()
        compose.onNodeWithText("FreeLift5 ${BuildConfig.VERSION_NAME}")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("GitHub repository")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("https://github.com/b3p3k0/FreeLift5")
            .assertIsDisplayed()
        compose.onNodeWithText("GPL-3.0 full text")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("https://www.gnu.org/licenses/gpl-3.0.en.html")
            .assertIsDisplayed()

        clickNavigationItem("Workout")
        compose.onNodeWithText("Start workout").performClick()
        waitForText("Workout A")

        compose.waitForIdle()
        val completeSetNodes = compose
            .onAllNodesWithText("Complete Back Squat set 1", substring = true)
            .fetchSemanticsNodes()
        check(completeSetNodes.isNotEmpty()) {
            compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 8)
        }
        compose.onNodeWithText("Complete Back Squat set 1", substring = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Save set").performClick()
        waitForText("Not now")
        compose.onNodeWithText("Not now").performClick()
        waitForText("Rest")

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForText("Workout A")
        compose.onNodeWithText("Rest").performScrollTo().assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        waitForText("Workout A")
        compose.onNodeWithText("Rest").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Finish workout early")
            .performScrollTo()
            .performClick()
        waitForText("Review workout")
        compose.onNodeWithText("Finish workout").performClick()
        waitForText("Next: Workout B")
    }

    private fun clickNavigationItem(label: String) {
        compose.onAllNodesWithText(label)
            .filter(hasClickAction())
            .fetchSemanticsNodes()
            .also { check(it.isNotEmpty()) { "No clickable navigation item named $label" } }
        compose.onAllNodesWithText(label)
            .filter(hasClickAction())
            .onFirst()
            .performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
