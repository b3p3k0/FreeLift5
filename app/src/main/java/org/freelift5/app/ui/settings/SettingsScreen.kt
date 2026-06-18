package org.freelift5.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.nio.charset.StandardCharsets
import org.freelift5.app.BuildConfig
import org.freelift5.app.domain.BuiltInPrograms
import org.freelift5.app.domain.ProgramDefinition
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.export.CsvExport
import org.freelift5.app.export.ExportFiles
import org.freelift5.app.ui.AppUiState
import org.freelift5.app.ui.AppViewModel
import org.freelift5.app.ui.components.PrivacyCard
import org.freelift5.app.ui.components.SectionTitle
import org.freelift5.app.ui.theme.AppThemeDefinition

private enum class PermissionPurpose {
    TIMER,
    WORKOUT_REMINDER,
    BODY_REMINDER,
}

@Composable
fun SettingsScreen(
    state: AppUiState,
    activeTheme: AppThemeDefinition,
    viewModel: AppViewModel,
) {
    val context = LocalContext.current
    val exportSessions = listOfNotNull(state.activeWorkout) + state.history
    var showBarWeight by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showProgramPicker by remember { mutableStateOf(false) }
    var pendingProgramSwitch by remember { mutableStateOf<ProgramDefinition?>(null) }
    var pendingPermission by remember { mutableStateOf<PermissionPurpose?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { destination ->
            pendingExport?.let { ExportFiles.writeToUri(context, destination, it) }
        }
        pendingExport = null
    }
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let { destination ->
            pendingExport?.let { ExportFiles.writeToUri(context, destination, it) }
        }
        pendingExport = null
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            when (pendingPermission) {
                PermissionPurpose.TIMER -> viewModel.setBackgroundAlerts(true)
                PermissionPurpose.WORKOUT_REMINDER ->
                    viewModel.setWorkoutReminders(true)
                PermissionPurpose.BODY_REMINDER ->
                    viewModel.setBodyReminder(true)
                null -> Unit
            }
        } else if (pendingPermission == PermissionPurpose.TIMER) {
            viewModel.setBackgroundAlerts(false)
        }
        pendingPermission = null
    }

    fun requestNotifications(purpose: PermissionPurpose, enable: () -> Unit) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enable()
        } else {
            pendingPermission = purpose
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("Settings")
        PrivacyCard(
            text = "No account. No telemetry. No network access. Your data stays on your device.",
        )

        AppearanceSettingsSection(
            preferences = state.settings.themePreferences,
            activeTheme = activeTheme,
            onBehaviorChange = viewModel::setThemeBehavior,
            onThemeSelected = viewModel::setTheme,
        )

        SettingsSection("Units and equipment") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitSystem.entries.forEach { unit ->
                    FilterChip(
                        selected = state.settings.unitSystem == unit,
                        onClick = { viewModel.setUnitSystem(unit) },
                        label = { Text(if (unit == UnitSystem.POUNDS) "Pounds" else "Kilograms") },
                    )
                }
            }
            SettingsActionRow(
                label = "Bar weight",
                value = WeightMath.format(
                    state.settings.barWeightGrams,
                    state.settings.unitSystem,
                ),
                onClick = { showBarWeight = true },
            )
        }

        SettingsSection("Training program") {
            Text(state.activeProgram.name, fontWeight = FontWeight.SemiBold)
            Text(
                state.activeProgram.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.activeWorkout != null) {
                Text(
                    "Finish your active workout before switching programs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { showProgramPicker = true },
                enabled = state.activeWorkout == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change program")
            }
        }

        SettingsSection("Rest timers") {
            ToggleRow(
                "Sound",
                "Play a short alert when rest ends.",
                state.settings.soundEnabled,
            ) { viewModel.setTimerCue(sound = it) }
            ToggleRow(
                "Vibration",
                "Use a short local vibration pattern.",
                state.settings.vibrationEnabled,
            ) { viewModel.setTimerCue(vibration = it) }
            ToggleRow(
                "Visual cue",
                "Pulse the timer border without flashing the full screen.",
                state.settings.visualCueEnabled,
            ) { viewModel.setTimerCue(visual = it) }
            ToggleRow(
                "Keep screen awake",
                "Only while the active workout screen is visible.",
                state.settings.keepScreenAwake,
                viewModel::setKeepScreenAwake,
            )
            ToggleRow(
                "Background timer alerts",
                "Run a temporary foreground timer and show a countdown notification.",
                state.settings.backgroundAlertsEnabled,
            ) { enabled ->
                if (enabled) {
                    requestNotifications(PermissionPurpose.TIMER) {
                        viewModel.setBackgroundAlerts(true)
                    }
                } else {
                    viewModel.setBackgroundAlerts(false)
                }
            }
        }

        SettingsSection("Reminders") {
            ToggleRow(
                "Workout reminders",
                "Approximate on-device reminders. Off by default.",
                state.settings.workoutRemindersEnabled,
            ) { enabled ->
                if (enabled) {
                    requestNotifications(PermissionPurpose.WORKOUT_REMINDER) {
                        viewModel.setWorkoutReminders(true)
                    }
                } else {
                    viewModel.setWorkoutReminders(false)
                }
            }
            if (state.settings.workoutRemindersEnabled) {
                Text("Training days")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                        val day = index + 1
                        FilterChip(
                            selected = day in state.settings.workoutReminderDays,
                            onClick = {
                                val days = if (day in state.settings.workoutReminderDays) {
                                    state.settings.workoutReminderDays - day
                                } else {
                                    state.settings.workoutReminderDays + day
                                }
                                if (days.isNotEmpty()) {
                                    viewModel.setWorkoutReminders(true, days = days)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                ReminderTimeEditor(
                    minutes = state.settings.workoutReminderMinutes,
                    onSave = {
                        viewModel.setWorkoutReminders(true, minutes = it)
                    },
                )
            }
            ToggleRow(
                "Body-weight reminder",
                "Optional interval prompt. Off by default.",
                state.settings.bodyReminderEnabled,
            ) { enabled ->
                if (enabled) {
                    requestNotifications(PermissionPurpose.BODY_REMINDER) {
                        viewModel.setBodyReminder(true)
                    }
                } else {
                    viewModel.setBodyReminder(false)
                }
            }
            if (state.settings.bodyReminderEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Every ${state.settings.bodyReminderIntervalDays} days")
                    Row {
                        TextButton(
                            onClick = {
                                viewModel.setBodyReminder(
                                    true,
                                    (state.settings.bodyReminderIntervalDays - 1).coerceAtLeast(1),
                                )
                            },
                        ) { Text("-") }
                        TextButton(
                            onClick = {
                                viewModel.setBodyReminder(
                                    true,
                                    state.settings.bodyReminderIntervalDays + 1,
                                )
                            },
                        ) { Text("+") }
                    }
                }
            }
        }

        SettingsSection("Export your data") {
            OutlinedButton(
                onClick = {
                    pendingExport = CsvExport.workouts(exportSessions)
                        .toByteArray(StandardCharsets.UTF_8)
                    csvLauncher.launch("freelift5-workouts.csv")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Text(" Save workout CSV")
            }
            OutlinedButton(
                onClick = {
                    pendingExport = CsvExport.measurements(state.measurements)
                        .toByteArray(StandardCharsets.UTF_8)
                    csvLauncher.launch("freelift5-measurements.csv")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Text(" Save measurement CSV")
            }
            OutlinedButton(
                onClick = {
                    pendingExport = CsvExport.completeBundle(
                        workoutSessions = exportSessions,
                        measurements = state.measurements,
                        appSettings = state.settings,
                        exerciseDefinitions = state.exercises,
                        coreSlots = state.coreProgram,
                        accessoryAssignments = state.accessories,
                    )
                    zipLauncher.launch("freelift5-export.zip")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Text(" Save complete ZIP")
            }
            Button(
                onClick = {
                    ExportFiles.share(
                        context,
                        "freelift5-export.zip",
                        "application/zip",
                        CsvExport.completeBundle(
                            workoutSessions = exportSessions,
                            measurements = state.measurements,
                            appSettings = state.settings,
                            exerciseDefinitions = state.exercises,
                            coreSlots = state.coreProgram,
                            accessoryAssignments = state.accessories,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Text(" Share complete export")
            }
            Text(
                "You choose the destination. FreeLift5 does not operate a cloud service.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SettingsSection("About") {
            Text("FreeLift5 ${BuildConfig.VERSION_NAME}")
            Text("GPL-3.0-or-later")
            Text("Offline 5x5 workout tracker")
        }

        HorizontalDivider()
        OutlinedButton(
            onClick = { showClear = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
            Text(" Clear all data")
        }
    }

    if (showBarWeight) {
        BarWeightDialog(
            unitSystem = state.settings.unitSystem,
            currentGrams = state.settings.barWeightGrams,
            onDismiss = { showBarWeight = false },
            onSave = {
                viewModel.setBarWeight(it)
                showBarWeight = false
            },
        )
    }
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Clear all FreeLift5 data?") },
            text = {
                Text(
                    "This permanently deletes workouts, measurements, custom exercises, and settings from this device. Export first if you need a copy.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClear = false
                    },
                ) {
                    Text("Permanently delete")
                }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancel") } },
        )
    }
    if (showProgramPicker) {
        AlertDialog(
            onDismissRequest = { showProgramPicker = false },
            title = { Text("Choose a program") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BuiltInPrograms.all.forEach { program ->
                        val current = program.id == state.activeProgram.id
                        OutlinedCard(
                            onClick = {
                                showProgramPicker = false
                                if (!current) pendingProgramSwitch = program
                            },
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(
                                    program.name + if (current) "  (current)" else "",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    program.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProgramPicker = false }) { Text("Close") }
            },
        )
    }
    pendingProgramSwitch?.let { program ->
        AlertDialog(
            onDismissRequest = { pendingProgramSwitch = null },
            title = { Text("Switch to ${program.name}?") },
            text = {
                Text(
                    "Your workout history is kept. Lifts you have been progressing keep " +
                        "their current weight wherever the new program uses the same " +
                        "movement; new lifts start at their default and stay editable.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.switchProgram(program.id)
                        pendingProgramSwitch = null
                    },
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingProgramSwitch = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        TextButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun ReminderTimeEditor(
    minutes: Int,
    onSave: (Int) -> Unit,
) {
    var hour by remember(minutes) { mutableStateOf((minutes / 60).toString()) }
    var minute by remember(minutes) { mutableStateOf((minutes % 60).toString().padStart(2, '0')) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = hour,
            onValueChange = { hour = it },
            label = { Text("Hour (0-23)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = minute,
            onValueChange = { minute = it },
            label = { Text("Minute") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        TextButton(
            onClick = {
                val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 18
                val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onSave(h * 60 + m)
            },
        ) {
            Text("Apply")
        }
    }
}

@Composable
private fun BarWeightDialog(
    unitSystem: UnitSystem,
    currentGrams: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var value by remember {
        mutableStateOf(
            WeightMath.fromGrams(currentGrams, unitSystem)
                .let { kotlin.math.round(it * 4.0) / 4.0 }
                .toString()
                .removeSuffix(".0"),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bar weight") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(if (unitSystem == UnitSystem.POUNDS) "Pounds" else "Kilograms") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = (value.toDoubleOrNull() ?: 0.0) > 0,
                onClick = { onSave(WeightMath.toGrams(value.toDouble(), unitSystem)) },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
