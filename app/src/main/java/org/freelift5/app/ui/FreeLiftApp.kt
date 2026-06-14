package org.freelift5.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import org.freelift5.app.ui.guides.ExerciseGuidesScreen
import org.freelift5.app.ui.onboarding.OnboardingScreen
import org.freelift5.app.ui.program.ProgramScreen
import org.freelift5.app.ui.progress.ProgressScreen
import org.freelift5.app.ui.settings.SettingsScreen
import org.freelift5.app.ui.theme.AppThemeDefinition
import org.freelift5.app.ui.theme.FreeLiftTheme
import org.freelift5.app.ui.theme.ThemeCatalog
import org.freelift5.app.ui.workout.WorkoutPage
import org.freelift5.app.ui.workout.WorkoutScreen

enum class RootTab(
    val label: String,
    val icon: ImageVector,
) {
    Workout("Workout", Icons.Outlined.FitnessCenter),
    Progress("Progress", Icons.Outlined.BarChart),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
fun FreeLiftApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val systemDark = isSystemInDarkTheme()
    val activeTheme = ThemeCatalog.resolve(state.settings.themePreferences, systemDark)

    FreeLiftTheme(theme = activeTheme) {
        Box(Modifier.fillMaxSize().testTag("active-theme-${activeTheme.id.persistedId}")) {
            AppContent(
                state = state,
                message = message,
                snackbar = snackbar,
                activeTheme = activeTheme,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun AppContent(
    state: AppUiState,
    message: String?,
    snackbar: SnackbarHostState,
    activeTheme: AppThemeDefinition,
    viewModel: AppViewModel,
) {
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    if (!state.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!state.settings.onboardingComplete) {
        OnboardingScreen(onComplete = viewModel::completeOnboarding)
        return
    }

    var selectedTab by remember { mutableStateOf(RootTab.Workout) }
    var workoutPage by remember { mutableStateOf(WorkoutPage.HOME) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                RootTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = {
                            if (tab == RootTab.Workout && selectedTab == RootTab.Workout) {
                                workoutPage = WorkoutPage.HOME
                            }
                            selectedTab = tab
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selectedTab) {
                RootTab.Workout -> when (workoutPage) {
                    WorkoutPage.HOME -> WorkoutScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenProgram = { workoutPage = WorkoutPage.PROGRAM },
                        onOpenGuides = { workoutPage = WorkoutPage.GUIDES },
                    )
                    WorkoutPage.PROGRAM -> ProgramScreen(
                        state = state,
                        viewModel = viewModel,
                        onBack = { workoutPage = WorkoutPage.HOME },
                    )
                    WorkoutPage.GUIDES -> ExerciseGuidesScreen(
                        exercises = state.exercises,
                        onBack = { workoutPage = WorkoutPage.HOME },
                    )
                }
                RootTab.Progress -> ProgressScreen(state, viewModel)
                RootTab.Settings -> SettingsScreen(
                    state = state,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                )
            }
        }
    }
}
