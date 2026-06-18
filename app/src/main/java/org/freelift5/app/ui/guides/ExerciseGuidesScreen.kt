package org.freelift5.app.ui.guides

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.freelift5.app.data.ExerciseEntity
import org.freelift5.app.domain.CoreSlot
import org.freelift5.app.domain.RoutineEngine
import org.freelift5.app.ui.components.SectionTitle

private data class ExerciseGuide(
    val slot: CoreSlot,
    val setup: String,
    val movement: String,
    val cues: List<String>,
    val mistakes: List<String>,
    val safety: String,
)

private val guides = listOf(
    ExerciseGuide(
        CoreSlot.SQUAT,
        "Set the bar across the muscular shelf of your upper back. Brace your trunk, place your feet around shoulder width, and keep the whole foot planted.",
        "Unlock hips and knees together, descend under control until your hip crease reaches at least knee level, then drive the floor away and stand tall.",
        listOf("Brace before every rep", "Keep knees tracking with toes", "Maintain pressure through the whole foot"),
        listOf("Collapsing the chest", "Knees falling inward", "Losing balance onto toes or heels"),
        "Use rack safeties set just below the bottom position. Stop if pain changes your movement or you cannot control the bar.",
    ),
    ExerciseGuide(
        CoreSlot.BENCH_PRESS,
        "Lie with eyes near the bar, feet planted, shoulder blades gently pulled back, and hands placed evenly. Use safeties or a competent spotter.",
        "Unrack with straight arms, lower the bar toward the lower chest with forearms near vertical, touch under control, and press back over the shoulders.",
        listOf("Keep shoulders set", "Use steady leg pressure", "Keep wrists stacked over forearms"),
        listOf("Bouncing the bar", "Lifting hips from the bench", "Flaring elbows abruptly"),
        "Do not bench alone without safeties. Use collars only when the rack and spotting setup make them appropriate.",
    ),
    ExerciseGuide(
        CoreSlot.BARBELL_ROW,
        "Raise the bar to normal plate height when using small plates. Hinge at the hips, brace, and begin each rep from a stable floor or support position.",
        "Pull the bar toward the lower chest or upper abdomen without turning the movement into a standing shrug. Lower it under control and reset your brace.",
        listOf("Keep the torso position consistent", "Pull elbows behind you", "Reset before each rep"),
        listOf("Jerking the torso upright", "Rounding under load", "Letting the bar drift far forward"),
        "Use proper rack pins, safeties, or stable blocks to establish height.",
    ),
    ExerciseGuide(
        CoreSlot.OVERHEAD_PRESS,
        "Stand with the bar at upper-chest height, hands just outside shoulders, wrists stacked, glutes tight, and trunk braced.",
        "Move your head slightly back, press the bar vertically, then bring your body under it as the bar passes your forehead. Finish with arms straight overhead.",
        listOf("Squeeze glutes", "Keep ribs controlled", "Move the bar in a near-vertical line"),
        listOf("Leaning back excessively", "Pressing around the face", "Relaxing at the bottom"),
        "Use a load you can control without turning the press into a standing incline bench. Stop for sharp shoulder or back pain.",
    ),
    ExerciseGuide(
        CoreSlot.DEADLIFT,
        "Place the bar over mid-foot at normal plate height. Grip just outside the legs, bring shins to the bar, brace, and set the back in a strong neutral position.",
        "Push the floor away while keeping the bar close. Stand by extending knees and hips together. Return the bar by hinging first, then bending the knees.",
        listOf("Brace before pulling", "Keep the bar close", "Finish tall without leaning back"),
        listOf("Yanking slack from the bar", "Letting hips shoot up first", "Hyperextending at lockout"),
        "Do not pull a bar from an artificially low position when small plates are used. Raise it with stable blocks or rack supports.",
    ),
)

@Composable
fun ExerciseGuidesScreen(
    exercises: List<ExerciseEntity>,
    onBack: () -> Unit,
) {
    var selectedGuide by remember { mutableStateOf<ExerciseGuide?>(null) }
    var selectedCustom by remember { mutableStateOf<ExerciseEntity?>(null) }

    val guide = selectedGuide
    val custom = selectedCustom
    if (guide != null) {
        BackHandler { selectedGuide = null }
        GuideDetail(guide, onBack = { selectedGuide = null })
        return
    }
    if (custom != null) {
        BackHandler { selectedCustom = null }
        CustomGuideDetail(custom, onBack = { selectedCustom = null })
        return
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            SectionTitle(
                "Exercise guides",
                subtitle = "Brief references, not personalized medical or coaching advice.",
            )
        }
        guides.forEach { item ->
            OutlinedCard(onClick = { selectedGuide = item }) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        RoutineEngine.builtInExercises.getValue(item.slot).name,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Setup, movement, cues, mistakes, and safety")
                }
            }
        }
        val customExercises = exercises.filter { it.builtInSlot == null }
        if (customExercises.isNotEmpty()) {
            Text("Your exercises", style = MaterialTheme.typography.titleLarge)
            customExercises.forEach { exercise ->
                OutlinedCard(onClick = { selectedCustom = exercise }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(exercise.name, fontWeight = FontWeight.SemiBold)
                        Text(exercise.notes.ifBlank { "No notes yet." })
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideDetail(
    guide: ExerciseGuide,
    onBack: () -> Unit,
) {
    var playing by remember {
        mutableStateOf(ValueAnimator.areAnimatorsEnabled())
    }
    val name = RoutineEngine.builtInExercises.getValue(guide.slot).name
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
            Text(name, style = MaterialTheme.typography.headlineSmall)
        }
        ExerciseMotionDiagram(guide.slot, playing)
        OutlinedButton(onClick = { playing = !playing }) {
            Icon(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
            )
            Text(if (playing) " Pause animation" else " Play animation")
        }
        GuideSection("Setup", guide.setup)
        GuideSection("Movement", guide.movement)
        GuideSection("Key cues", guide.cues.joinToString("\n") { "• $it" })
        GuideSection("Common mistakes", guide.mistakes.joinToString("\n") { "• $it" })
        GuideSection("Safety", guide.safety)
    }
}

@Composable
private fun ExerciseMotionDiagram(
    slot: CoreSlot,
    playing: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "exercise-motion")
    val animated by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar-position",
    )
    val progress = if (playing) animated else 0.5f
    val color = MaterialTheme.colorScheme.onSurface
    val name = RoutineEngine.builtInExercises.getValue(slot).name
    Card {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .semantics {
                        contentDescription = "$name schematic motion reference"
                    },
            ) {
                val centerX = size.width / 2
                val (start, end) = when (slot) {
                    CoreSlot.SQUAT -> 0.34f to 0.62f
                    CoreSlot.BENCH_PRESS -> 0.28f to 0.48f
                    CoreSlot.BARBELL_ROW -> 0.66f to 0.48f
                    CoreSlot.OVERHEAD_PRESS -> 0.54f to 0.16f
                    CoreSlot.DEADLIFT -> 0.76f to 0.40f
                }
                val barY = size.height * (start + (end - start) * progress)
                val bodyOffset = if (slot == CoreSlot.SQUAT) {
                    size.height * 0.20f * progress
                } else {
                    0f
                }
                drawLine(
                    color,
                    Offset(size.width * 0.2f, barY),
                    Offset(size.width * 0.8f, barY),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color,
                    radius = 28f,
                    center = Offset(centerX, size.height * 0.22f + bodyOffset),
                )
                drawLine(
                    color,
                    Offset(centerX, size.height * 0.31f + bodyOffset),
                    Offset(centerX, size.height * 0.67f + bodyOffset),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round,
                )
                if (slot == CoreSlot.BENCH_PRESS) {
                    drawLine(
                        color,
                        Offset(size.width * 0.25f, size.height * 0.68f),
                        Offset(size.width * 0.75f, size.height * 0.68f),
                        strokeWidth = 10f,
                        cap = StrokeCap.Round,
                    )
                }
                if (slot == CoreSlot.DEADLIFT || slot == CoreSlot.BARBELL_ROW) {
                    drawLine(
                        color,
                        Offset(size.width * 0.15f, size.height * 0.82f),
                        Offset(size.width * 0.85f, size.height * 0.82f),
                        strokeWidth = 4f,
                    )
                }
            }
            Text(
                "Schematic $name motion reference",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuideSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CustomGuideDetail(
    exercise: ExerciseEntity,
    onBack: () -> Unit,
) {
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
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "FreeLift5 does not invent instructions for user-created exercises.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GuideSection("Your notes", exercise.notes.ifBlank { "No notes have been added." })
    }
}
