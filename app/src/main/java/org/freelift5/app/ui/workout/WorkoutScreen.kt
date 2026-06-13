package org.freelift5.app.ui.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.delay
import org.freelift5.app.data.CoreSlotSummary
import org.freelift5.app.data.ExerciseSessionWithSets
import org.freelift5.app.data.SetRecordEntity
import org.freelift5.app.domain.ExercisePrescription
import org.freelift5.app.domain.PlateCalculator
import org.freelift5.app.domain.ProgressionEngine
import org.freelift5.app.domain.ProgressionState
import org.freelift5.app.domain.SetPerformance
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WarmupCalculator
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.timer.TimerStateStore
import org.freelift5.app.ui.AppUiState
import org.freelift5.app.ui.AppViewModel
import org.freelift5.app.ui.components.SectionTitle

enum class WorkoutPage {
    HOME,
    PROGRAM,
    GUIDES,
}

@Composable
fun WorkoutScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onOpenProgram: () -> Unit,
    onOpenGuides: () -> Unit,
) {
    val active = state.activeWorkout
    val view = LocalView.current
    DisposableEffect(active?.session?.id, state.settings.keepScreenAwake) {
        view.keepScreenOn = active != null && state.settings.keepScreenAwake
        onDispose { view.keepScreenOn = false }
    }

    if (active == null) {
        WorkoutHome(
            state = state,
            onStart = viewModel::startWorkout,
            onOpenProgram = onOpenProgram,
            onOpenGuides = onOpenGuides,
            onResolveDeload = viewModel::resolveDeload,
        )
    } else {
        ActiveWorkout(
            state = state,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun WorkoutHome(
    state: AppUiState,
    onStart: () -> Unit,
    onOpenProgram: () -> Unit,
    onOpenGuides: () -> Unit,
    onResolveDeload: (String, Long) -> Unit,
) {
    val dayKey = state.settings.nextWorkoutDayKey
    val dayLabel = state.activeProgram.day(dayKey)?.label ?: "Workout $dayKey"
    val next = state.coreProgram
        .filter { it.workoutType == dayKey }
        .sortedBy { it.orderIndex }
    var customDeload by remember { mutableStateOf<CoreSlotSummary?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            "Next: $dayLabel",
            subtitle = "Complete what you can. Partial workouts are saved and the " +
                "sequence advances.",
        )
        state.coreProgram
            .distinctBy { it.slotKey }
            .filter { it.pendingDeloadGrams != null }
            .forEach { slot ->
                DeloadCard(
                    slot = slot,
                    unitSystem = state.settings.unitSystem,
                    onAccept = {
                        onResolveDeload(slot.slotKey, slot.pendingDeloadGrams!!)
                    },
                    onKeep = {
                        onResolveDeload(slot.slotKey, slot.currentWeightGrams)
                    },
                    onChoose = { customDeload = slot },
                )
            }
        next.forEach { exercise ->
            OutlinedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(exercise.exerciseName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${exercise.sets}x${exercise.reps}  " +
                                WeightMath.format(
                                    exercise.currentWeightGrams,
                                    state.settings.unitSystem,
                                ),
                        )
                    }
                    Text(
                        "+${WeightMath.format(exercise.incrementGrams, state.settings.unitSystem)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val accessories = state.accessories.filter { it.workoutType == dayKey }
        if (accessories.isNotEmpty()) {
            Text("Accessory work", style = MaterialTheme.typography.titleMedium)
            accessories.forEach { accessory ->
                Text(
                    "${accessory.exerciseName}: " +
                        when (TrackingMode.valueOf(accessory.trackingMode)) {
                            TrackingMode.WEIGHT ->
                                "${accessory.sets}x${accessory.target} at " +
                                    WeightMath.format(
                                        accessory.currentWeightGrams,
                                        state.settings.unitSystem,
                                    )
                            TrackingMode.BODYWEIGHT,
                            TrackingMode.REPETITIONS,
                            -> "${accessory.sets}x${accessory.target} reps"
                            TrackingMode.TIME ->
                                "${accessory.sets}x${accessory.target} sec"
                        },
                )
            }
        }
        Button(
            onClick = onStart,
            enabled = next.isNotEmpty() || accessories.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start workout")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenProgram, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Text(" Program")
            }
            OutlinedButton(onClick = onOpenGuides, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                Text(" Guides")
            }
        }
    }

    customDeload?.let { slot ->
        WeightEntryDialog(
            title = "Choose ${slot.exerciseName} weight",
            initialGrams = slot.pendingDeloadGrams ?: slot.currentWeightGrams,
            unitSystem = state.settings.unitSystem,
            onDismiss = { customDeload = null },
            onSave = {
                onResolveDeload(slot.slotKey, it)
                customDeload = null
            },
        )
    }
}

@Composable
private fun DeloadCard(
    slot: CoreSlotSummary,
    unitSystem: UnitSystem,
    onAccept: () -> Unit,
    onKeep: () -> Unit,
    onChoose: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Suggested deload: ${slot.exerciseName}", fontWeight = FontWeight.Bold)
            Text(
                "Three failed workouts at ${WeightMath.format(slot.currentWeightGrams, unitSystem)}. " +
                    "Suggested: ${WeightMath.format(slot.pendingDeloadGrams!!, unitSystem)}.",
            )
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("Accept suggested weight")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onKeep, modifier = Modifier.weight(1f)) {
                    Text("Keep current")
                }
                OutlinedButton(onClick = onChoose, modifier = Modifier.weight(1f)) {
                    Text("Choose")
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkout(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    val workout = state.activeWorkout ?: return
    val exercises = workout.exercises.sortedBy { it.exercise.orderIndex }
    val current = exercises.firstOrNull { relation ->
        relation.sets.count { !it.isWarmup } < relation.exercise.targetSets
    }
    var confirmExercise by remember { mutableStateOf<ExerciseSessionWithSets?>(null) }
    var showFinish by remember { mutableStateOf(false) }
    var showWarmups by remember { mutableStateOf(false) }
    var showPlates by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            "Workout ${workout.session.workoutType}",
            subtitle = "${exercises.count { it.sets.count { set -> !set.isWarmup } >= it.exercise.targetSets }} " +
                "of ${exercises.size} exercises complete",
        )
        exercises.forEach { relation ->
            ExerciseProgressCard(
                relation = relation,
                unitSystem = state.settings.unitSystem,
                isCurrent = relation.exercise.id == current?.exercise?.id,
            )
        }
        if (current != null) {
            val completedSets = current.sets.count { !it.isWarmup }
            Button(
                onClick = { confirmExercise = current },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(
                    "Complete ${current.exercise.exerciseName} set ${completedSets + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val mode = TrackingMode.valueOf(current.exercise.trackingMode)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.exercise.coreSlotKey != null && mode == TrackingMode.WEIGHT) {
                    OutlinedButton(
                        onClick = { showWarmups = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Warmup")
                    }
                }
                if (mode == TrackingMode.WEIGHT) {
                OutlinedButton(
                    onClick = { showPlates = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Calculate, contentDescription = null)
                    Text(" Plates")
                }
                }
            }
        }
        RestTimerCard(state, viewModel)
        HorizontalDivider()
        OutlinedButton(
            onClick = { showFinish = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (current == null) "Review and finish" else "Finish workout early")
        }
    }

    confirmExercise?.let { relation ->
        val setNumber = relation.sets.count { !it.isWarmup } + 1
        SetConfirmationDialog(
            relation = relation,
            setNumber = setNumber,
            unitSystem = state.settings.unitSystem,
            onDismiss = { confirmExercise = null },
            onSave = { reps, weight ->
                viewModel.saveSet(
                    exerciseSessionId = relation.exercise.id,
                    setNumber = setNumber,
                    actualReps = reps,
                    actualWeightGrams = weight,
                    targetReps = relation.exercise.targetReps,
                    successfulRestSeconds = relation.exercise.successfulRestSeconds,
                    failedRestSeconds = relation.exercise.failedRestSeconds,
                )
                confirmExercise = null
            },
        )
    }
    if (showFinish) {
        FinishWorkoutDialog(
            exercises = exercises,
            coreProgram = state.coreProgram,
            unitSystem = state.settings.unitSystem,
            onDismiss = { showFinish = false },
            onFinish = { notes ->
                viewModel.finishWorkout(workout.session.id, notes)
                showFinish = false
            },
        )
    }
    if (showWarmups && current != null) {
        WarmupDialog(
            exercise = current,
            barWeightGrams = state.settings.barWeightGrams,
            unitSystem = state.settings.unitSystem,
            onDismiss = { showWarmups = false },
        )
    }
    if (showPlates && current != null) {
        PlateDialog(
            targetWeightGrams = current.exercise.targetWeightGrams,
            barWeightGrams = state.settings.barWeightGrams,
            unitSystem = state.settings.unitSystem,
            onDismiss = { showPlates = false },
        )
    }
}

@Composable
private fun ExerciseProgressCard(
    relation: ExerciseSessionWithSets,
    unitSystem: UnitSystem,
    isCurrent: Boolean,
) {
    val workSets = relation.sets.filterNot(SetRecordEntity::isWarmup).sortedBy { it.setNumber }
    OutlinedCard(
        border = BorderStroke(
            if (isCurrent) 2.dp else 1.dp,
            if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(relation.exercise.exerciseName, fontWeight = FontWeight.SemiBold)
                Text(
                    exerciseTargetLabel(relation, unitSystem),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(relation.exercise.targetSets) { index ->
                    val set = workSets.getOrNull(index)
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                set?.actualReps?.toString()
                                    ?: "${index + 1}",
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetConfirmationDialog(
    relation: ExerciseSessionWithSets,
    setNumber: Int,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onSave: (Int, Long) -> Unit,
) {
    val trackingMode = TrackingMode.valueOf(relation.exercise.trackingMode)
    val isBodyweight = trackingMode == TrackingMode.BODYWEIGHT
    var reps by remember { mutableStateOf(relation.exercise.targetReps.toString()) }
    var weight by remember {
        mutableStateOf(
            if (isBodyweight && relation.exercise.targetWeightGrams == 0L) {
                ""
            } else {
                inputNumber(relation.exercise.targetWeightGrams, unitSystem)
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${relation.exercise.exerciseName} - Set $setNumber") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Target: ${exerciseTargetLabel(relation, unitSystem)}",
                )
                if (trackingMode == TrackingMode.WEIGHT) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = {
                            Text("Weight (${if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"})")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (isBodyweight) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = {
                            Text(
                                "Additional weight (optional, " +
                                    "${if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"})",
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Last weight: " + if (relation.exercise.targetWeightGrams > 0L) {
                            WeightMath.format(relation.exercise.targetWeightGrams, unitSystem)
                        } else {
                            "BW"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = {
                        Text(if (trackingMode == TrackingMode.TIME) "Seconds" else "Repetitions")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        reps.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        if (trackingMode == TrackingMode.WEIGHT || isBodyweight) {
                            WeightMath.toGrams(
                                weight.toDoubleOrNull() ?: 0.0,
                                unitSystem,
                            )
                        } else {
                            relation.exercise.targetWeightGrams
                        },
                    )
                },
            ) {
                Text("Save set")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FinishWorkoutDialog(
    exercises: List<ExerciseSessionWithSets>,
    coreProgram: List<CoreSlotSummary>,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onFinish: (String) -> Unit,
) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review workout") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                exercises.forEach { relation ->
                    val sets = relation.sets.filterNot(SetRecordEntity::isWarmup)
                    val slot = coreProgram.firstOrNull {
                        it.slotKey == relation.exercise.coreSlotKey
                    }
                    val decision = slot?.let {
                        ProgressionEngine.evaluate(
                            ProgressionState(
                                it.currentWeightGrams,
                                it.consecutiveFailures,
                            ),
                            ExercisePrescription(
                                it.sets,
                                it.reps,
                                it.incrementGrams,
                                it.successfulRestSeconds,
                                it.failedRestSeconds,
                            ),
                            sets.map { set ->
                                SetPerformance(
                                    set.setNumber,
                                    set.targetReps,
                                    set.actualReps,
                                    set.targetWeightGrams,
                                    set.actualWeightGrams,
                                )
                            },
                            WeightMath.toGrams(
                                if (unitSystem == UnitSystem.POUNDS) 5.0 else 2.5,
                                unitSystem,
                            ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(relation.exercise.exerciseName, fontWeight = FontWeight.SemiBold)
                            Text(
                                sets.sortedBy { it.setNumber }.joinToString(" / ") {
                                    it.actualReps.toString()
                                }.ifBlank { "Not started" },
                            )
                        }
                        Text(decision?.action?.name?.replace('_', ' ') ?: "Accessory")
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Workout notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Text(
                    "Completed lifts count toward progression. This session is saved even if partial, and the next workout advances.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onFinish(notes) }) { Text("Finish workout") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep training") } },
    )
}

@Composable
private fun RestTimerCard(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    val context = LocalContext.current
    val store = remember { TimerStateStore(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val timerState = store.current()
    val remaining = timerState?.remainingSeconds(now) ?: 0
    val complete = timerState != null && remaining == 0
    var signaledEnd by remember { mutableLongStateOf(0L) }
    val backgroundPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setBackgroundAlerts(granted)
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(timerState?.endAtEpochMillis, complete) {
        if (
            complete &&
            timerState.endAtEpochMillis != signaledEnd &&
            !state.settings.backgroundAlertsEnabled
        ) {
            signaledEnd = timerState.endAtEpochMillis
            signalInAppTimer(context, state)
        }
    }

    val borderColor by animateColorAsState(
        targetValue = if (complete && state.settings.visualCueEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "rest-complete-color",
    )
    if (timerState != null) {
        OutlinedCard(border = BorderStroke(if (complete) 3.dp else 1.dp, borderColor)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (complete) "Rest complete" else "Rest")
                Text(
                    formatSeconds(remaining),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.adjustTimer(-30) }) {
                        Text("-30 sec")
                    }
                    OutlinedButton(onClick = viewModel::stopTimer) {
                        Text("Skip")
                    }
                    OutlinedButton(onClick = { viewModel.adjustTimer(30) }) {
                        Text("+30 sec")
                    }
                }
            }
        }
    }

    if (
        timerState?.isRunning == true &&
        !state.settings.backgroundAlertsAsked
    ) {
        AlertDialog(
            onDismissRequest = viewModel::markBackgroundAlertsAsked,
            title = { Text("Timer alerts outside FreeLift5?") },
            text = {
                Text(
                    "Enable a countdown notification, sound, and vibration when the screen is locked or you switch apps. No timer data leaves your device.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            backgroundPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setBackgroundAlerts(true)
                        }
                    },
                ) {
                    Text("Enable alerts")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::markBackgroundAlertsAsked) {
                    Text("Not now")
                }
            },
        )
    }
}

@Composable
private fun WarmupDialog(
    exercise: ExerciseSessionWithSets,
    barWeightGrams: Long,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
) {
    val increment = WeightMath.toGrams(
        if (unitSystem == UnitSystem.POUNDS) 5.0 else 2.5,
        unitSystem,
    )
    val warmups = runCatching {
        WarmupCalculator.calculate(
            exercise.exercise.targetWeightGrams,
            barWeightGrams,
            increment,
        )
    }.getOrDefault(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${exercise.exercise.exerciseName} warmup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (warmups.isEmpty()) Text("The work weight is already at the empty bar.")
                warmups.forEach {
                    Text("${WeightMath.format(it.weightGrams, unitSystem)} x ${it.reps}")
                }
                Text(
                    "Warmup sets do not affect progression.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun PlateDialog(
    targetWeightGrams: Long,
    barWeightGrams: Long,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
) {
    val calculation = PlateCalculator.calculate(targetWeightGrams, barWeightGrams, unitSystem)
    val load = calculation.exact ?: calculation.nearestLower ?: calculation.nearestHigher
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load ${WeightMath.format(targetWeightGrams, unitSystem)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bar: ${WeightMath.format(barWeightGrams, unitSystem)}")
                if (calculation.exact == null) {
                    Text("That exact load cannot be made with standard plates.")
                }
                Text("Per side:", fontWeight = FontWeight.SemiBold)
                Text(
                    load?.plateGramsPerSide?.joinToString(" + ") {
                        WeightMath.format(it, unitSystem)
                    }?.ifBlank { "No plates" } ?: "No valid load",
                )
                calculation.nearestLower?.takeIf { calculation.exact == null }?.let {
                    Text("Nearest lower: ${WeightMath.format(it.totalWeightGrams, unitSystem)}")
                }
                calculation.nearestHigher?.takeIf { calculation.exact == null }?.let {
                    Text("Nearest higher: ${WeightMath.format(it.totalWeightGrams, unitSystem)}")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun WeightEntryDialog(
    title: String,
    initialGrams: Long,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var weight by remember { mutableStateOf(inputNumber(initialGrams, unitSystem)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text(if (unitSystem == UnitSystem.POUNDS) "Pounds" else "Kilograms") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(WeightMath.toGrams(weight.toDoubleOrNull() ?: 0.0, unitSystem))
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun inputNumber(grams: Long, unitSystem: UnitSystem): String {
    val value = WeightMath.fromGrams(grams, unitSystem)
    val rounded = kotlin.math.round(value * 4.0) / 4.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

private fun formatSeconds(seconds: Int): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

private fun signalInAppTimer(context: Context, state: AppUiState) {
    if (state.settings.soundEnabled) {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        Handler(Looper.getMainLooper()).postDelayed(tone::release, 500L)
    }
    if (state.settings.vibrationEnabled) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1),
        )
    }
}

private fun exerciseTargetLabel(
    relation: ExerciseSessionWithSets,
    unitSystem: UnitSystem,
): String = when (TrackingMode.valueOf(relation.exercise.trackingMode)) {
    TrackingMode.WEIGHT ->
        "${relation.exercise.targetReps} reps at " +
            WeightMath.format(relation.exercise.targetWeightGrams, unitSystem)
    TrackingMode.BODYWEIGHT ->
        "${relation.exercise.targetReps} bodyweight reps"
    TrackingMode.REPETITIONS ->
        "${relation.exercise.targetReps} reps"
    TrackingMode.TIME ->
        "${relation.exercise.targetReps} seconds"
}
