package org.freelift5.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.freelift5.app.data.BodyMeasurementEntity
import org.freelift5.app.data.ExerciseProgressPoint
import org.freelift5.app.data.SetRecordEntity
import org.freelift5.app.data.WorkoutSessionWithExercises
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.ui.AppUiState
import org.freelift5.app.ui.AppViewModel
import org.freelift5.app.ui.components.PrivacyCard
import org.freelift5.app.ui.components.SectionTitle

private enum class ProgressSection {
    OVERVIEW,
    HISTORY,
    EXERCISES,
}

private enum class ExerciseMetric(val label: String) {
    WORK_WEIGHT("Work weight"),
    ESTIMATED_ONE_REP_MAX("Estimated 1RM"),
    VOLUME("Volume"),
    RELATIVE_STRENGTH("Relative strength"),
}

@Composable
fun ProgressScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var section by remember { mutableStateOf(ProgressSection.OVERVIEW) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Progress")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressSection.entries.forEach {
                FilterChip(
                    selected = section == it,
                    onClick = { section = it },
                    label = { Text(it.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        when (section) {
            ProgressSection.OVERVIEW -> Overview(state, viewModel)
            ProgressSection.HISTORY -> History(state)
            ProgressSection.EXERCISES -> ExerciseProgress(state)
        }
    }
}

@Composable
private fun Overview(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var showMeasurement by remember { mutableStateOf(false) }
    val latestWeight = state.measurements.lastOrNull()
    val fourWeeksAgo = System.currentTimeMillis() - 28L * 24 * 60 * 60 * 1_000
    val recentCount = state.history.count {
        (it.session.completedAtEpochMillis ?: 0L) >= fourWeeksAgo
    }
    val activeWeeks = state.history.mapNotNull { workout ->
        workout.session.completedAtEpochMillis?.let {
            DateTimeFormatter.ofPattern("YYYY-ww")
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }
    }.distinct().size
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Workouts", state.history.size.toString(), Modifier.weight(1f))
            SummaryCard("Completed", state.history.count { it.session.status == "COMPLETED" }.toString(), Modifier.weight(1f))
            SummaryCard("Partial", state.history.count { it.session.status == "PARTIAL" }.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Last 4 weeks", recentCount.toString(), Modifier.weight(1f))
            SummaryCard("Active weeks", activeWeeks.toString(), Modifier.weight(1f))
        }
        Card {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Body weight", style = MaterialTheme.typography.titleMedium)
                Text(
                    latestWeight?.let {
                        WeightMath.format(it.bodyWeightGrams, state.settings.unitSystem)
                    } ?: "No measurements yet",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.measurements.size > 1) {
                    LineChart(
                        values = state.measurements.map {
                            WeightMath.fromGrams(it.bodyWeightGrams, state.settings.unitSystem)
                        },
                    )
                }
                Button(onClick = { showMeasurement = true }) {
                    Text("Add measurement")
                }
            }
        }
        val recent = state.history.take(5)
        if (recent.isNotEmpty()) {
            Text("Recent workouts", style = MaterialTheme.typography.titleLarge)
            recent.forEach { WorkoutHistoryCard(it, state.settings.unitSystem) }
        }
        PrivacyCard(
            text = "Measurements stay on your phone and are used only for calculating metrics.",
        )
    }
    if (showMeasurement) {
        MeasurementDialog(
            unitSystem = state.settings.unitSystem,
            onDismiss = { showMeasurement = false },
            onSave = {
                viewModel.addMeasurement(it)
                showMeasurement = false
            },
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun History(state: AppUiState) {
    var selected by remember { mutableStateOf<WorkoutSessionWithExercises?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.history.isEmpty()) {
            Text("Completed and partial workouts will appear here.")
        }
        state.history.forEach { workout ->
            OutlinedCard(onClick = { selected = workout }) {
                WorkoutHistoryCard(workout, state.settings.unitSystem)
            }
        }
    }
    selected?.let { workout ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Workout ${workout.session.workoutType}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${formatDate(workout.session.completedAtEpochMillis)} • ${workout.session.status}")
                    workout.exercises.sortedBy { it.exercise.orderIndex }.forEach { relation ->
                        val sets = relation.sets.filterNot(SetRecordEntity::isWarmup)
                        val result = sets.sortedBy { it.setNumber }
                            .joinToString("/") { it.actualReps.toString() }
                        val suffix = when (TrackingMode.valueOf(relation.exercise.trackingMode)) {
                            TrackingMode.WEIGHT ->
                                " at ${WeightMath.format(relation.exercise.targetWeightGrams, state.settings.unitSystem)}"
                            TrackingMode.BODYWEIGHT -> " bodyweight reps"
                            TrackingMode.REPETITIONS -> " reps"
                            TrackingMode.TIME -> " seconds"
                        }
                        Text(
                            "${relation.exercise.exerciseName}: $result$suffix",
                        )
                    }
                    workout.session.notes.takeIf(String::isNotBlank)?.let {
                        Text("Notes: $it")
                    }
                }
            },
            confirmButton = { Button(onClick = { selected = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun WorkoutHistoryCard(
    workout: WorkoutSessionWithExercises,
    unitSystem: UnitSystem,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Workout ${workout.session.workoutType}", fontWeight = FontWeight.SemiBold)
            Text(workout.session.status)
        }
        Text(formatDate(workout.session.completedAtEpochMillis))
        val volume = workout.exercises.sumOf { relation ->
            relation.sets.filterNot(SetRecordEntity::isWarmup).sumOf {
                it.actualWeightGrams * it.actualReps
            }
        }
        Text(
            "Volume: ${WeightMath.fromGrams(volume, unitSystem).roundToInt()} " +
                if (unitSystem == UnitSystem.POUNDS) "lb-reps" else "kg-reps",
        )
    }
}

@Composable
private fun ExerciseProgress(state: AppUiState) {
    val names = state.exerciseProgress
        .filter { it.trackingMode == TrackingMode.WEIGHT.name }
        .map { it.exerciseName }
        .distinct()
    var selectedName by remember(names) { mutableStateOf(names.firstOrNull()) }
    var metric by remember { mutableStateOf(ExerciseMetric.WORK_WEIGHT) }
    val points = state.exerciseProgress.filter { it.exerciseName == selectedName }
    val values = metricValues(metric, points, state)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (names.isEmpty()) {
            Text("Exercise charts appear after your first saved workout.")
            return@Column
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            names.forEach { name ->
                FilterChip(
                    selected = selectedName == name,
                    onClick = { selectedName = name },
                    label = { Text(name) },
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExerciseMetric.entries.forEach {
                FilterChip(
                    selected = metric == it,
                    onClick = { metric = it },
                    label = { Text(it.label) },
                )
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(metric.label, style = MaterialTheme.typography.titleMedium)
                if (values.isEmpty()) {
                    Text("Not enough data for this metric.")
                } else {
                    LineChart(values)
                }
            }
        }
        val record = selectedName?.let { weightPersonalRecord(state.history, it) }
        record?.let {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Weight PR", fontWeight = FontWeight.Bold)
                    Text(
                        "${WeightMath.format(it.weightGrams, state.settings.unitSystem)} " +
                            "for ${it.sets}x${it.reps}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
    }
}

private fun metricValues(
    metric: ExerciseMetric,
    points: List<ExerciseProgressPoint>,
    state: AppUiState,
): List<Double> = points.mapNotNull { point ->
    when (metric) {
        ExerciseMetric.WORK_WEIGHT ->
            WeightMath.fromGrams(point.targetWeightGrams, state.settings.unitSystem)
        ExerciseMetric.ESTIMATED_ONE_REP_MAX ->
            WeightMath.fromGrams(
                WeightMath.epleyEstimate(point.targetWeightGrams, point.targetReps.coerceIn(1, 10)),
                state.settings.unitSystem,
            )
        ExerciseMetric.VOLUME -> {
            val workout = state.history.firstOrNull { it.session.id == point.sessionId }
            val relation = workout?.exercises?.firstOrNull { it.exercise.exerciseId == point.exerciseId }
            relation?.sets
                ?.filterNot(SetRecordEntity::isWarmup)
                ?.sumOf { WeightMath.fromGrams(it.actualWeightGrams, state.settings.unitSystem) * it.actualReps }
        }
        ExerciseMetric.RELATIVE_STRENGTH -> {
            val bodyWeight = state.measurements
                .lastOrNull { it.recordedAtEpochMillis <= point.completedAtEpochMillis }
                ?.bodyWeightGrams
                ?: return@mapNotNull null
            WeightMath.epleyEstimate(
                point.targetWeightGrams,
                point.targetReps.coerceIn(1, 10),
            ).toDouble() / bodyWeight
        }
    }
}

@Composable
private fun LineChart(values: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier.fillMaxWidth().aspectRatio(2f),
    ) {
        if (values.isEmpty()) return@Canvas
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: min
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) size.width / 2 else size.width * index / (values.size - 1)
            val y = size.height - ((value - min) / range * size.height * 0.8 + size.height * 0.1).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color, radius = 6f, center = Offset(x, y))
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
    }
}

@Composable
private fun MeasurementDialog(
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add body weight") },
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
                onClick = {
                    onSave(WeightMath.toGrams(value.toDouble(), unitSystem))
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatDate(epochMillis: Long?): String {
    epochMillis ?: return "In progress"
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
