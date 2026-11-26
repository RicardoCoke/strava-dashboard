package app.stravadashboard.api

import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.model.METERS_PER_KM
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.model.toKmOneDecimal
import app.stravadashboard.domain.workoutcompare.Coarse
import app.stravadashboard.domain.workoutcompare.CompareCalculator
import app.stravadashboard.domain.workoutcompare.CompareSide
import app.stravadashboard.domain.workoutcompare.RepCell
import app.stravadashboard.domain.workoutcompare.RepComparison
import app.stravadashboard.domain.workoutcompare.RepRow
import app.stravadashboard.domain.workoutcompare.WorkoutComparison

fun List<RunActivity>.toCompareRunsDto(): CompareRunsResponse =
    CompareRunsResponse(
        map { run ->
            CompareRunItemDto(
                id = run.id.value,
                date = run.localDate.toString(),
                name = run.name,
                typeKey = RunClassifier.classify(run).key,
                distanceKm = run.distance.toKmOneDecimal(),
                averagePaceSecondsPerKm = CompareCalculator.averagePace(run)?.value,
                treadmill = run.trainer,
            )
        },
    )

fun WorkoutComparison.toDto(): WorkoutCompareResponse =
    WorkoutCompareResponse(
        resolution = resolution.name.lowercase(),
        coarse = coarse?.toDto(),
        runs = listOf(earlier.toDto(), later.toDto()),
        reps = reps?.toDto(),
    )

private fun Coarse.toDto(): CoarseDto = CoarseDto(camelCase(reason.name), runId?.value, retryAfter?.toString())

private fun CompareSide.toDto(): CompareRunDto =
    CompareRunDto(
        id = run.id.value,
        date = run.localDate.toString(),
        name = run.name,
        typeKey = RunClassifier.classify(run).key,
        treadmill = run.trainer,
        stats =
            CompareStatsDto(
                distanceKm = stats.distance.toKmOneDecimal(),
                movingTimeSeconds = stats.movingTime?.value,
                averagePaceSecondsPerKm = stats.pace?.value,
                averageHeartRateBpm = stats.averageHeartRateBpm?.let(::roundHalfUpToInt),
                elevationGainMeters = stats.elevationGain?.value?.let(::roundHalfUpToInt),
                efficiencyFactor = stats.efficiency?.let(::roundEfficiency),
                hrDriftPct = stats.driftPct?.let(::roundOneDecimal),
            ),
        points =
            points.map {
                ComparePointDto(
                    distanceKm = roundHalfUpToInt(it.distance.value) / METERS_PER_KM,
                    timeSeconds = it.time.value,
                    paceSecondsPerKm = it.pace?.value,
                    heartRateBpm = it.heartRate?.let(::roundHalfUpToInt),
                )
            },
    )

private fun RepComparison.toDto(): RepComparisonDto =
    RepComparisonDto(
        mode = mode.name.lowercase(),
        unavailableReason = unavailableReason?.let { camelCase(it.name) },
        unavailableRunId = unavailableRunId?.value,
        pieceMinutes = pieceMinutes,
        piecesRunId = piecesRunId?.value,
        average = average?.toDto(),
        pairs = pairs.map { it.toDto() },
    )

/** Differences are later − earlier, from the rounded values the page shows. */
private fun RepRow.toDto(): RepRowDto {
    val a = earlier?.toDto()
    val b = later?.toDto()
    val hrA = a?.heartRateBpm
    val hrB = b?.heartRateBpm
    return RepRowDto(
        earlier = a,
        later = b,
        paceDiffSecondsPerKm = if (a != null && b != null) b.paceSecondsPerKm - a.paceSecondsPerKm else null,
        heartRateDiffBpm = if (hrA != null && hrB != null) hrB - hrA else null,
    )
}

private fun RepCell.toDto(): RepCellDto = RepCellDto(pace.value, heartRate?.let(::roundHalfUpToInt))

private val WORD_BREAK = Regex("_([a-z])")

/** `NO_DETAIL_FILE` → `noDetailFile`. */
private fun camelCase(name: String): String = name.lowercase().replace(WORD_BREAK) { it.groupValues[1].uppercase() }
