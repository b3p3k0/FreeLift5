package org.freelift5.app.ui.progress

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import org.freelift5.app.data.BodyMeasurementEntity
import org.freelift5.app.data.ExerciseProgressPoint
import org.freelift5.app.data.SetRecordEntity
import org.freelift5.app.data.WorkoutSessionWithExercises
import org.freelift5.app.domain.TrackingMode
import org.freelift5.app.domain.UnitSystem
import org.freelift5.app.domain.WeightMath
import org.freelift5.app.ui.AppUiState

internal data class ChartPoint(
    val timestampMillis: Long,
    val value: Double,
)

internal data class ChartAxisLabels(
    val min: String,
    val mid: String,
    val max: String,
)

internal enum class ExerciseMetric(val label: String) {
    WORK_WEIGHT("Work weight"),
    ESTIMATED_ONE_REP_MAX("Estimated 1RM"),
    VOLUME("Volume"),
    RELATIVE_STRENGTH("Relative strength"),
}

internal fun bodyWeightChartPoints(
    measurements: List<BodyMeasurementEntity>,
    unitSystem: UnitSystem,
): List<ChartPoint> = measurements
    .sortedBy(BodyMeasurementEntity::recordedAtEpochMillis)
    .map {
        ChartPoint(
            timestampMillis = it.recordedAtEpochMillis,
            value = WeightMath.fromGrams(it.bodyWeightGrams, unitSystem),
        )
    }

internal fun exerciseMetricChartPoints(
    metric: ExerciseMetric,
    points: List<ExerciseProgressPoint>,
    state: AppUiState,
): List<ChartPoint> = points
    .sortedBy(ExerciseProgressPoint::completedAtEpochMillis)
    .mapNotNull { point ->
        val value = when (metric) {
            ExerciseMetric.WORK_WEIGHT ->
                WeightMath.fromGrams(point.targetWeightGrams, state.settings.unitSystem)
            ExerciseMetric.ESTIMATED_ONE_REP_MAX ->
                WeightMath.fromGrams(
                    WeightMath.epleyEstimate(
                        point.targetWeightGrams,
                        point.targetReps.coerceIn(1, 10),
                    ),
                    state.settings.unitSystem,
                )
            ExerciseMetric.VOLUME -> {
                val workout = state.history.firstOrNull { it.session.id == point.sessionId }
                val relation = workout?.exercises?.firstOrNull {
                    it.exercise.exerciseId == point.exerciseId
                }
                relation?.sets
                    ?.filterNot(SetRecordEntity::isWarmup)
                    ?.sumOf {
                        WeightMath.fromGrams(
                            it.actualWeightGrams,
                            state.settings.unitSystem,
                        ) * it.actualReps
                    }
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
        } ?: return@mapNotNull null
        ChartPoint(timestampMillis = point.completedAtEpochMillis, value = value)
    }

internal fun weightValueFormatter(unitSystem: UnitSystem): (Double) -> String = { value ->
    "${formatCompactNumber(value)} ${unitLabel(unitSystem)}"
}

internal fun exerciseValueFormatter(
    metric: ExerciseMetric,
    unitSystem: UnitSystem,
): (Double) -> String = { value ->
    when (metric) {
        ExerciseMetric.WORK_WEIGHT,
        ExerciseMetric.ESTIMATED_ONE_REP_MAX,
        -> "${formatCompactNumber(value)} ${unitLabel(unitSystem)}"
        ExerciseMetric.VOLUME ->
            "${formatCompactNumber(value)} ${unitLabel(unitSystem)}-reps"
        ExerciseMetric.RELATIVE_STRENGTH ->
            String.format(Locale.US, "%.2fx BW", value)
    }
}

internal fun chartAxisLabels(
    points: List<ChartPoint>,
    valueFormatter: (Double) -> String,
): ChartAxisLabels {
    val min = points.minOfOrNull(ChartPoint::value) ?: 0.0
    val max = points.maxOfOrNull(ChartPoint::value) ?: min
    return ChartAxisLabels(
        min = valueFormatter(min),
        mid = valueFormatter((min + max) / 2.0),
        max = valueFormatter(max),
    )
}

internal fun chartDateLabels(
    points: List<ChartPoint>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<String> {
    val sorted = points.sortedBy(ChartPoint::timestampMillis)
    val samples = when (sorted.size) {
        0 -> return emptyList()
        1 -> listOf(sorted.first().timestampMillis)
        2 -> listOf(sorted.first().timestampMillis, sorted.last().timestampMillis)
        else -> {
            val start = sorted.first().timestampMillis
            val end = sorted.last().timestampMillis
            listOf(start, start + (end - start) / 2L, end)
        }
    }
    return samples.map { formatAxisDate(it, zoneId) }
}

internal fun chartContentDescription(
    title: String,
    points: List<ChartPoint>,
    valueFormatter: (Double) -> String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val sorted = points.sortedBy(ChartPoint::timestampMillis)
    if (sorted.isEmpty()) return "$title chart. No data."
    val min = sorted.minOf(ChartPoint::value)
    val max = sorted.maxOf(ChartPoint::value)
    val start = formatAxisDate(sorted.first().timestampMillis, zoneId)
    val end = formatAxisDate(sorted.last().timestampMillis, zoneId)
    return if (sorted.size == 1) {
        "$title chart. 1 point on $start. Value ${valueFormatter(sorted.first().value)}."
    } else {
        "$title chart. ${sorted.size} points from $start to $end. Values range " +
            "${valueFormatter(min)} to ${valueFormatter(max)}."
    }
}

private fun formatAxisDate(epochMillis: Long, zoneId: ZoneId): String =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)
        .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

private fun unitLabel(unitSystem: UnitSystem): String =
    if (unitSystem == UnitSystem.POUNDS) "lb" else "kg"

private fun formatCompactNumber(value: Double): String {
    val absValue = abs(value)
    val scaled = if (absValue >= 1_000.0) value / 1_000.0 else value
    val suffix = if (absValue >= 1_000.0) "k" else ""
    val decimals = when {
        absValue >= 100.0 && suffix.isEmpty() -> 0
        scaled.rem(1.0) == 0.0 -> 0
        else -> 1
    }
    return BigDecimal.valueOf(scaled)
        .setScale(decimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString() + suffix
}

internal data class WeightPersonalRecord(
    val weightGrams: Long,
    val sets: Int,
    val reps: Int,
)

internal fun weightPersonalRecord(
    history: List<WorkoutSessionWithExercises>,
    exerciseName: String,
): WeightPersonalRecord? = history
    .asSequence()
    .flatMap { it.exercises.asSequence() }
    .filter {
        it.exercise.exerciseName == exerciseName &&
            it.exercise.trackingMode == TrackingMode.WEIGHT.name
    }
    .mapNotNull { relation ->
        val exercise = relation.exercise
        val prescribedSets = relation.sets
            .filterNot(SetRecordEntity::isWarmup)
            .associateBy(SetRecordEntity::setNumber)
        val completedSets = (1..exercise.targetSets).mapNotNull(prescribedSets::get)
        if (
            completedSets.size != exercise.targetSets ||
            completedSets.any {
                it.actualReps < it.targetReps || it.actualWeightGrams <= 0L
            }
        ) {
            null
        } else {
            WeightPersonalRecord(
                weightGrams = completedSets.minOf(SetRecordEntity::actualWeightGrams),
                sets = exercise.targetSets,
                reps = exercise.targetReps,
            )
        }
    }
    .maxByOrNull(WeightPersonalRecord::weightGrams)
