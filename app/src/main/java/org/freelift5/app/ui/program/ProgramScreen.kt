package org.freelift5.app.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.freelift5.app.data.CoreSlotSummary
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.ui.AppUiState
import org.freelift5.app.ui.AppViewModel
import org.freelift5.app.ui.components.SectionTitle

@Composable
fun ProgramScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    var selectedCore by remember { mutableStateOf<CoreSlotSummary?>(null) }
    var adaptationWarning by remember { mutableStateOf<CoreSlotSummary?>(null) }
    var adaptationEditor by remember { mutableStateOf<CoreSlotSummary?>(null) }
    var showAccessoryEditor by remember { mutableStateOf(false) }
    var accessoriesToDelete by remember { mutableStateOf<List<String>?>(null) }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            SectionTitle(
                "Program",
                subtitle = "Core work and accessory work stay separate.",
            )
        }
        Text("Core Program", style = MaterialTheme.typography.titleLarge)
        state.activeProgram.days.forEach { day ->
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(day.label, fontWeight = FontWeight.Bold)
                    state.coreProgram
                        .filter { it.workoutType == day.key }
                        .sortedBy { it.orderIndex }
                        .forEach { slot ->
                            OutlinedCard(onClick = { selectedCore = slot }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(slot.exerciseName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${slot.sets}x${slot.reps}  " +
                                                WeightMath.format(
                                                    slot.currentWeightGrams,
                                                    state.settings.unitSystem,
                                                ),
                                        )
                                    }
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                                }
                            }
                        }
                    val required = state.accessories
                        .filter { it.required && it.workoutType == day.key }
                        .sortedBy { it.orderIndex }
                    if (required.isNotEmpty()) {
                        Text(
                            "Assistance (part of the plan)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        required.forEach { accessory ->
                            Text("${accessory.exerciseName}  ${accessorySummary(accessory)}")
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        val optionalAccessories = state.accessories.filterNot { it.required }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Accessory Work", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { showAccessoryEditor = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(" Add")
            }
        }
        if (optionalAccessories.isEmpty()) {
            Text(
                "Optional extras you add yourself: curls, calf raises, crunches, " +
                    "adaptations, or whatever fits your training.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        optionalAccessories.groupBy { it.exerciseId }.forEach { (_, assignments) ->
            val first = assignments.first()
            OutlinedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(first.exerciseName, fontWeight = FontWeight.SemiBold)
                        Text(
                            accessorySummary(first) + " in " +
                                assignments.joinToString("/") { it.workoutType },
                        )
                    }
                    IconButton(
                        onClick = {
                            accessoriesToDelete = assignments.map { it.assignmentId }
                        },
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete accessory")
                    }
                }
            }
        }
    }

    selectedCore?.let { slot ->
        CoreSlotDialog(
            slot = slot,
            state = state,
            onDismiss = { selectedCore = null },
            onAdapt = {
                selectedCore = null
                adaptationWarning = slot
            },
            onSaveSettings = { sets, reps, currentWeight, increment, rest, failedRest ->
                viewModel.updateCoreSettings(
                    slot.slotKey,
                    sets,
                    reps,
                    currentWeight,
                    increment,
                    rest,
                    failedRest,
                )
                selectedCore = null
            },
            onReset = {
                state.exercises.firstOrNull { exercise ->
                    exercise.builtInSlot == slot.canonicalSlot
                }?.let { viewModel.useExistingAdaptation(slot.slotKey, it.id) }
                selectedCore = null
            },
            onSplit = {
                viewModel.splitCoreSlot(slot.slotKey, slot.workoutType)
                selectedCore = null
            },
        )
    }
    adaptationWarning?.let { slot ->
        AlertDialog(
            onDismissRequest = { adaptationWarning = null },
            title = { Text("Replace a core exercise?") },
            text = {
                Text(
                    "This remains core work, appears in the same workout slot, and affects progression. " +
                        if (state.coreProgram.count { it.slotKey == slot.slotKey } > 1) {
                            "This shared slot changes every workout it appears in."
                        } else {
                            "This change applies to Workout ${slot.workoutType}."
                        },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adaptationWarning = null
                        adaptationEditor = slot
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { adaptationWarning = null }) { Text("Cancel") }
            },
        )
    }
    adaptationEditor?.let { slot ->
        AdaptationDialog(
            slot = slot,
            state = state,
            onDismiss = { adaptationEditor = null },
            onUseExisting = { id ->
                viewModel.useExistingAdaptation(slot.slotKey, id)
                adaptationEditor = null
            },
            onCreate = { name, notes ->
                viewModel.createAndUseAdaptation(slot.slotKey, name, notes)
                adaptationEditor = null
            },
        )
    }
    if (showAccessoryEditor) {
        AccessoryDialog(
            unitSystem = state.settings.unitSystem,
            days = state.activeProgram.days.map { it.key to it.label },
            onDismiss = { showAccessoryEditor = false },
            onSave = {
                    name,
                    mode,
                    workouts,
                    sets,
                    target,
                    weight,
                    increment,
                    targetIncrement,
                    frequency,
                    rest,
                    notes,
                ->
                viewModel.addAccessory(
                    name,
                    mode,
                    workouts,
                    sets,
                    target,
                    weight,
                    increment,
                    targetIncrement,
                    frequency,
                    rest,
                    notes,
                )
                showAccessoryEditor = false
            },
        )
    }
    accessoriesToDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { accessoriesToDelete = null },
            title = { Text("Remove accessory?") },
            text = { Text("Past workout history is retained.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccessories(ids)
                        accessoriesToDelete = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { accessoriesToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CoreSlotDialog(
    slot: CoreSlotSummary,
    state: AppUiState,
    onDismiss: () -> Unit,
    onAdapt: () -> Unit,
    onSaveSettings: (Int, Int, Long, Long, Int, Int) -> Unit,
    onReset: () -> Unit,
    onSplit: () -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    var sets by remember { mutableStateOf(slot.sets.toString()) }
    var reps by remember { mutableStateOf(slot.reps.toString()) }
    var currentWeight by remember {
        mutableStateOf(inputNumber(slot.currentWeightGrams, state.settings.unitSystem))
    }
    var increment by remember {
        mutableStateOf(inputNumber(slot.incrementGrams, state.settings.unitSystem))
    }
    var rest by remember { mutableStateOf(slot.successfulRestSeconds.toString()) }
    var failedRest by remember { mutableStateOf(slot.failedRestSeconds.toString()) }
    val shared = state.coreProgram.count { it.slotKey == slot.slotKey } > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slot.exerciseName} (Core exercise)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (shared) "Appears in multiple workouts"
                    else "Appears in Workout ${slot.workoutType}",
                )
                Text("${slot.sets} sets x ${slot.reps} reps")
                Text("Increment: ${WeightMath.format(slot.incrementGrams, state.settings.unitSystem)}")
                Button(onClick = onAdapt, modifier = Modifier.fillMaxWidth()) {
                    Text("Use an adaptation")
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset to original exercise")
                }
                if (shared) {
                    OutlinedButton(onClick = onSplit, modifier = Modifier.fillMaxWidth()) {
                        Text("Configure Workout ${slot.workoutType} separately")
                    }
                }
                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "Hide advanced core settings" else "Advanced core settings")
                }
                if (advanced) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
                        NumberField("Reps", reps, { reps = it }, Modifier.weight(1f))
                    }
                    NumberField(
                        "Current work weight (${unitSuffix(state.settings.unitSystem)})",
                        currentWeight,
                        { currentWeight = it },
                    )
                    NumberField(
                        "Increment (${unitSuffix(state.settings.unitSystem)})",
                        increment,
                        { increment = it },
                    )
                    NumberField("Rest after success (seconds)", rest, { rest = it })
                    NumberField("Rest after missed set (seconds)", failedRest, { failedRest = it })
                }
            }
        },
        confirmButton = {
            if (advanced) {
                Button(
                    onClick = {
                        onSaveSettings(
                            sets.toIntOrNull()?.coerceAtLeast(1) ?: slot.sets,
                            reps.toIntOrNull()?.coerceAtLeast(1) ?: slot.reps,
                            WeightMath.toGrams(
                                currentWeight.toDoubleOrNull() ?: 0.0,
                                state.settings.unitSystem,
                            ),
                            WeightMath.toGrams(
                                increment.toDoubleOrNull() ?: 0.0,
                                state.settings.unitSystem,
                            ),
                            rest.toIntOrNull()?.coerceAtLeast(0) ?: 180,
                            failedRest.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                        )
                    },
                ) {
                    Text("Save settings")
                }
            } else {
                Button(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (advanced) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AdaptationDialog(
    slot: CoreSlotSummary,
    state: AppUiState,
    onDismiss: () -> Unit,
    onUseExisting: (String) -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var createNew by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adaptation for ${slot.exerciseName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("The replacement inherits ${slot.sets}x${slot.reps}, progression, and timers.")
                FilterChip(
                    selected = !createNew,
                    onClick = { createNew = false },
                    label = { Text("Choose existing") },
                )
                FilterChip(
                    selected = createNew,
                    onClick = { createNew = true },
                    label = { Text("Create exercise") },
                )
                if (createNew) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Exercise name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Your notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                } else {
                    state.exercises.filterNot { it.id == slot.exerciseId }.forEach { exercise ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedId == exercise.id,
                                onClick = { selectedId = exercise.id },
                            )
                            Text(exercise.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = if (createNew) name.isNotBlank() else selectedId != null,
                onClick = {
                    if (createNew) onCreate(name, notes) else selectedId?.let(onUseExisting)
                },
            ) {
                Text("Save adaptation")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccessoryDialog(
    unitSystem: UnitSystem,
    days: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (
        String,
        TrackingMode,
        Set<String>,
        Int,
        Int,
        Long,
        Long,
        Int,
        Int,
        Int,
        String,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TrackingMode.WEIGHT) }
    var workouts by remember { mutableStateOf(days.map { it.first }.toSet()) }
    var sets by remember { mutableStateOf("3") }
    var target by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("0") }
    var increment by remember { mutableStateOf(if (unitSystem == UnitSystem.POUNDS) "5" else "2.5") }
    var targetIncrement by remember { mutableStateOf("1") }
    var frequency by remember { mutableStateOf("1") }
    var rest by remember { mutableStateOf("90") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add accessory exercise") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { (key, label) ->
                        FilterChip(
                            selected = key in workouts,
                            onClick = {
                                workouts = if (key in workouts) workouts - key else workouts + key
                            },
                            label = { Text(label) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrackingMode.entries.forEach { tracking ->
                        FilterChip(
                            selected = mode == tracking,
                            onClick = { mode = tracking },
                            label = {
                                Text(
                                    when (tracking) {
                                        TrackingMode.WEIGHT -> "Weight"
                                        TrackingMode.BODYWEIGHT -> "Body"
                                        TrackingMode.REPETITIONS -> "Reps"
                                        TrackingMode.TIME -> "Time"
                                    },
                                )
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
                    NumberField(
                        if (mode == TrackingMode.TIME) "Seconds" else "Target reps",
                        target,
                        { target = it },
                        Modifier.weight(1f),
                    )
                }
                if (mode == TrackingMode.WEIGHT) {
                    NumberField("Starting load (${unitSuffix(unitSystem)})", weight, { weight = it })
                    NumberField("Increment (${unitSuffix(unitSystem)})", increment, { increment = it })
                } else {
                    NumberField(
                        if (mode == TrackingMode.TIME) {
                            "Increase target by (seconds)"
                        } else {
                            "Increase target by (reps)"
                        },
                        targetIncrement,
                        { targetIncrement = it },
                    )
                }
                NumberField("Progress after successful workouts", frequency, { frequency = it })
                NumberField("Rest (seconds)", rest, { rest = it })
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && workouts.isNotEmpty(),
                onClick = {
                    onSave(
                        name,
                        mode,
                        workouts,
                        sets.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        target.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        WeightMath.toGrams(weight.toDoubleOrNull() ?: 0.0, unitSystem),
                        WeightMath.toGrams(increment.toDoubleOrNull() ?: 0.0, unitSystem),
                        targetIncrement.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        frequency.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        rest.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        notes,
                    )
                },
            ) {
                Text("Add exercise")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}

private fun unitSuffix(unitSystem: UnitSystem): String =
    if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"

private fun inputNumber(grams: Long, unitSystem: UnitSystem): String {
    val value = WeightMath.fromGrams(grams, unitSystem)
    val rounded = kotlin.math.round(value * 4.0) / 4.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

private fun accessorySummary(accessory: org.freelift5.app.data.AccessorySummary): String =
    when (TrackingMode.valueOf(accessory.trackingMode)) {
        TrackingMode.WEIGHT ->
            "${accessory.sets}x${accessory.target}"
        TrackingMode.BODYWEIGHT,
        TrackingMode.REPETITIONS,
        -> "${accessory.sets}x${accessory.target} reps"
        TrackingMode.TIME ->
            "${accessory.sets}x${accessory.target} sec"
    }
