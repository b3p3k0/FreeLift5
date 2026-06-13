package org.freelift5.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.freelift5.app.domain.BuiltInPrograms
import org.freelift5.app.domain.CoreSlot
import org.freelift5.app.domain.ProgramDefinition
import org.freelift5.app.domain.RoutineEngine
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.domain.WorkoutDay
import org.freelift5.app.ui.components.PrivacyCard
import org.freelift5.app.ui.components.SectionTitle

private enum class StartMethod {
    EMPTY_BAR,
    RECENT_SET,
    KNOWN_ESTIMATE,
}

private data class LiftInput(
    val method: StartMethod = StartMethod.EMPTY_BAR,
    val weight: String = "",
    val repetitions: String = "5",
)

@Composable
fun OnboardingScreen(
    onComplete: (
        UnitSystem,
        Int?,
        Int?,
        Int?,
        String,
        Long?,
        Long,
        Map<CoreSlot, Long>,
        String,
    ) -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    var selectedProgramId by remember { mutableStateOf(BuiltInPrograms.DEFAULT_ID) }
    var unitSystem by remember { mutableStateOf(UnitSystem.POUNDS) }
    var birthMonth by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var bodyWeight by remember { mutableStateOf("") }
    var trainingBackground by remember { mutableStateOf("NEW") }
    var barWeight by remember { mutableStateOf("45") }
    val liftInputs = remember {
        mutableStateMapOf<CoreSlot, LiftInput>().apply {
            CoreSlot.entries.forEach { put(it, LiftInput()) }
        }
    }
    val reviewedWeights = remember { mutableStateMapOf<CoreSlot, String>() }

    val program = BuiltInPrograms.byId(selectedProgramId)
    val programSlots = CoreSlot.entries.filter { slot ->
        program.days.any { day -> day.coreSlots.any { it.canonicalSlot == slot.name } }
    }

    fun recalculateReview() {
        val barGrams = WeightMath.toGrams(
            barWeight.toDoubleOrNull() ?: if (unitSystem == UnitSystem.POUNDS) 45.0 else 20.0,
            unitSystem,
        )
        val loadableIncrement = WeightMath.toGrams(
            if (unitSystem == UnitSystem.POUNDS) 5.0 else 2.5,
            unitSystem,
        )
        programSlots.forEach { slot ->
            val input = liftInputs.getValue(slot)
            val suggested = when (input.method) {
                StartMethod.EMPTY_BAR -> barGrams
                StartMethod.RECENT_SET -> {
                    val weightGrams = WeightMath.toGrams(
                        input.weight.toDoubleOrNull() ?: 0.0,
                        unitSystem,
                    )
                    val reps = input.repetitions.toIntOrNull()?.coerceIn(2, 10) ?: 5
                    WeightMath.suggestedStartingWeight(
                        WeightMath.epleyEstimate(weightGrams, reps),
                        loadableIncrement,
                    )
                }
                StartMethod.KNOWN_ESTIMATE -> {
                    val estimate = WeightMath.toGrams(
                        input.weight.toDoubleOrNull() ?: 0.0,
                        unitSystem,
                    )
                    WeightMath.suggestedStartingWeight(estimate, loadableIncrement)
                }
            }.coerceAtLeast(barGrams)
            reviewedWeights[slot] = displayNumber(suggested, unitSystem)
        }
    }

    Scaffold(
        bottomBar = {
            if (page > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { page-- },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            if (page == 4) recalculateReview()
                            if (page < 5) {
                                page++
                            } else {
                                val fallbackBar = if (unitSystem == UnitSystem.POUNDS) 45.0 else 20.0
                                val barGrams = WeightMath.toGrams(
                                    barWeight.toDoubleOrNull() ?: fallbackBar,
                                    unitSystem,
                                )
                                onComplete(
                                    unitSystem,
                                    birthMonth.toIntOrNull()?.takeIf { it in 1..12 },
                                    birthYear.toIntOrNull()?.takeIf { it in 1900..2100 },
                                    heightToMillimeters(height, unitSystem),
                                    trainingBackground,
                                    bodyWeight.toDoubleOrNull()?.let {
                                        WeightMath.toGrams(it, unitSystem)
                                    },
                                    barGrams,
                                    programSlots.associateWith { slot ->
                                        WeightMath.toGrams(
                                            reviewedWeights[slot]?.toDoubleOrNull() ?: fallbackBar,
                                            unitSystem,
                                        )
                                    },
                                    selectedProgramId,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (page == 5) "Start FreeLift5" else "Next")
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (page) {
                0 -> WelcomePage(onStart = { page = 1 })
                1 -> ProfilePage(
                    unitSystem = unitSystem,
                    onUnitChange = {
                        unitSystem = it
                        barWeight = if (it == UnitSystem.POUNDS) "45" else "20"
                    },
                    birthMonth = birthMonth,
                    onBirthMonthChange = { birthMonth = it },
                    birthYear = birthYear,
                    onBirthYearChange = { birthYear = it },
                    height = height,
                    onHeightChange = { height = it },
                    bodyWeight = bodyWeight,
                    onBodyWeightChange = { bodyWeight = it },
                )
                2 -> BackgroundPage(
                    selected = trainingBackground,
                    onSelect = { trainingBackground = it },
                )
                3 -> ProgramPickPage(
                    selectedId = selectedProgramId,
                    onSelect = { selectedProgramId = it },
                )
                4 -> StartingWeightsPage(
                    unitSystem = unitSystem,
                    slots = programSlots,
                    barWeight = barWeight,
                    onBarWeightChange = { barWeight = it },
                    inputs = liftInputs,
                    onInputChange = { slot, input -> liftInputs[slot] = input },
                )
                else -> ReviewPage(
                    unitSystem = unitSystem,
                    program = program,
                    slots = programSlots,
                    weights = reviewedWeights,
                    onWeightChange = { slot, value -> reviewedWeights[slot] = value },
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "FreeLift5",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Offline 5x5 workout tracker",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Get started")
        }
        Spacer(Modifier.height(16.dp))
        PrivacyCard(text = "No account. No telemetry. No network access.")
    }
}

@Composable
private fun ProfilePage(
    unitSystem: UnitSystem,
    onUnitChange: (UnitSystem) -> Unit,
    birthMonth: String,
    onBirthMonthChange: (String) -> Unit,
    birthYear: String,
    onBirthYearChange: (String) -> Unit,
    height: String,
    onHeightChange: (String) -> Unit,
    bodyWeight: String,
    onBodyWeightChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            "Your units and optional metrics",
            subtitle = "Everything on this page is optional.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnitSystem.entries.forEach { unit ->
                FilterChip(
                    selected = unitSystem == unit,
                    onClick = { onUnitChange(unit) },
                    label = { Text(if (unit == UnitSystem.POUNDS) "lb (pounds)" else "kg (kilograms)") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = birthMonth,
                onValueChange = onBirthMonthChange,
                label = { Text("Birth month") },
                placeholder = { Text("1-12") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = birthYear,
                onValueChange = onBirthYearChange,
                label = { Text("Birth year") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = height,
            onValueChange = onHeightChange,
            label = {
                Text(if (unitSystem == UnitSystem.POUNDS) "Height (inches)" else "Height (cm)")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = bodyWeight,
            onValueChange = onBodyWeightChange,
            label = {
                Text("Body weight (${if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"})")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        PrivacyCard()
    }
}

@Composable
private fun BackgroundPage(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val choices = listOf(
        "NEW" to ("New to lifting" to "I am just getting started."),
        "RETURNING" to ("Returning" to "I have lifted before and taken time off."),
        "CURRENT" to ("Currently training" to "I lift regularly now."),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            "Training background",
            subtitle = "This helps us explain suggestions clearly.",
        )
        choices.forEach { (id, copy) ->
            Card(onClick = { onSelect(id) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == id, onClick = { onSelect(id) })
                    Column {
                        Text(copy.first, fontWeight = FontWeight.SemiBold)
                        Text(copy.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            "Start lighter than you think you need. Technique and consistency matter more than an impressive first entry.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StartingWeightsPage(
    unitSystem: UnitSystem,
    slots: List<CoreSlot>,
    barWeight: String,
    onBarWeightChange: (String) -> Unit,
    inputs: Map<CoreSlot, LiftInput>,
    onInputChange: (CoreSlot, LiftInput) -> Unit,
) {
    val suffix = if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            "Set your starting weights",
            subtitle = "Use a recent clean set or an estimate. " +
                "True-max testing is not required or recommended.",
        )
        OutlinedTextField(
            value = barWeight,
            onValueChange = onBarWeightChange,
            label = { Text("Empty bar weight ($suffix)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (slots.isEmpty()) {
            Text(
                "This program uses dumbbells. Each lift starts light and is fully " +
                    "editable later from the Program tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        slots.forEach { slot ->
            val input = inputs.getValue(slot)
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        RoutineEngine.builtInExercises.getValue(slot).name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    StartMethod.entries.forEach { method ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = input.method == method,
                                onClick = { onInputChange(slot, input.copy(method = method)) },
                            )
                            Text(
                                when (method) {
                                    StartMethod.EMPTY_BAR -> "Start with the empty bar"
                                    StartMethod.RECENT_SET -> "Use a recent set of 2-10 clean reps"
                                    StartMethod.KNOWN_ESTIMATE -> "Enter a known estimated 1RM"
                                },
                            )
                        }
                    }
                    if (input.method != StartMethod.EMPTY_BAR) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = input.weight,
                                onValueChange = {
                                    onInputChange(slot, input.copy(weight = it))
                                },
                                label = {
                                    Text(
                                        if (input.method == StartMethod.RECENT_SET) {
                                            "Set weight ($suffix)"
                                        } else {
                                            "Estimated 1RM ($suffix)"
                                        },
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            if (input.method == StartMethod.RECENT_SET) {
                                OutlinedTextField(
                                    value = input.repetitions,
                                    onValueChange = {
                                        onInputChange(slot, input.copy(repetitions = it))
                                    },
                                    label = { Text("Reps") },
                                    modifier = Modifier.weight(0.6f),
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewPage(
    unitSystem: UnitSystem,
    program: ProgramDefinition,
    slots: List<CoreSlot>,
    weights: Map<CoreSlot, String>,
    onWeightChange: (CoreSlot, String) -> Unit,
) {
    val suffix = if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            "Review your plan",
            subtitle = "Suggested starts use 60% of estimated 1RM, rounded down. " +
                "Change anything here.",
        )
        program.days.forEach { day -> DayPreviewCard(day) }
        HorizontalDivider()
        slots.forEach { slot ->
            OutlinedTextField(
                value = weights[slot].orEmpty(),
                onValueChange = { onWeightChange(slot, it) },
                label = {
                    Text("${RoutineEngine.builtInExercises.getValue(slot).name} ($suffix)")
                },
                supportingText = {
                    if (slot == CoreSlot.DEADLIFT) {
                        Text("Default increment: ${if (unitSystem == UnitSystem.POUNDS) "10 lb" else "5 kg"}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        Text(
            "Rows and deadlifts with an empty bar should begin at normal plate height using stable blocks or rack safeties. Use a rock or something.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ProgramPickPage(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            "Choose your program",
            subtitle = "You can switch later in Settings. History is kept when you do.",
        )
        BuiltInPrograms.all.forEach { program ->
            Card(onClick = { onSelect(program.id) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedId == program.id,
                        onClick = { onSelect(program.id) },
                    )
                    Column {
                        Text(program.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            program.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPreviewCard(day: WorkoutDay) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(day.label, fontWeight = FontWeight.Bold)
            day.coreSlots.forEach { slot ->
                val name = BuiltInPrograms.Catalog.byId(slot.exerciseId)?.name ?: slot.exerciseId
                Text("$name  ${slot.setScheme.workSets}x${slot.setScheme.targetReps}")
            }
            day.accessories.forEach { accessory ->
                val name = BuiltInPrograms.Catalog.byId(accessory.exerciseId)?.name
                    ?: accessory.exerciseId
                Text(
                    "$name  ${accessory.sets}x${accessory.target}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun displayNumber(grams: Long, unitSystem: UnitSystem): String {
    val value = WeightMath.fromGrams(grams, unitSystem)
    val rounded = kotlin.math.round(value * 4.0) / 4.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

private fun heightToMillimeters(value: String, unitSystem: UnitSystem): Int? {
    val number = value.toDoubleOrNull() ?: return null
    return if (unitSystem == UnitSystem.POUNDS) {
        (number * 25.4).toInt()
    } else {
        (number * 10.0).toInt()
    }
}
