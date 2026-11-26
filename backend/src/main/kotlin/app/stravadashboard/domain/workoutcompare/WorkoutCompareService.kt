package app.stravadashboard.domain.workoutcompare

import app.stravadashboard.domain.CallBudget
import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncStateRepository
import java.time.Clock
import java.time.LocalDate

/** A run's streams, or why the comparison falls back to per-km splits. */
private sealed interface Fetch {
    data class Got(
        val stream: RunStream,
    ) : Fetch

    data class Fallback(
        val coarse: Coarse,
    ) : Fetch
}

private fun failure(result: SourceResult<*>): Fetch =
    Fetch.Fallback(
        if (result is SourceResult.RateLimited) {
            Coarse(CoarseReason.RATE_LIMITED, retryAfter = result.until)
        } else {
            Coarse(CoarseReason.GARMIN_UNAVAILABLE)
        },
    )

private fun CompareFilter.matches(type: RunType): Boolean =
    when (this) {
        CompareFilter.ALL -> true
        CompareFilter.WORKOUTS -> type is RunType.Workout
        CompareFilter.LONG -> type == RunType.LongRun
        CompareFilter.EASY -> type == RunType.Easy
    }

/**
 * Feature 025. Reads the person's store; per-second data comes from [source] on demand, charged to the [budget] the
 * sync uses, and is never stored. [source] is null when the person has no Garmin key.
 */
class WorkoutCompareService(
    private val runs: RunRepository,
    private val source: ActivitySource?,
    private val budget: CallBudget,
    private val syncState: SyncStateRepository,
    private val activitiesFrom: LocalDate,
    private val clock: Clock,
) {
    suspend fun list(filter: CompareFilter): List<RunActivity> =
        runs
            .findAll()
            .filter { it.isRun && filter.matches(RunClassifier.classify(it)) }
            .sortedWith(compareByDescending<RunActivity> { it.startUtc }.thenByDescending { it.id.value })
            .take(LIST_SIZE)

    suspend fun compare(
        a: ActivityId,
        b: ActivityId,
    ): CompareOutcome {
        val stored = runs.findAll().filter { it.isRun }.associateBy { it.id }
        val first = stored[a]
        val second = stored[b]
        return if (first == null || second == null) {
            CompareOutcome.RunNotFound
        } else {
            val (earlier, later) =
                listOf(first, second).sortedWith(compareBy<RunActivity> { it.startUtc }.thenBy { it.id.value })
            CompareOutcome.Found(comparison(earlier, later))
        }
    }

    private suspend fun comparison(
        earlier: RunActivity,
        later: RunActivity,
    ): WorkoutComparison {
        val fetched = upFront(earlier, later)?.let { listOf(Fetch.Fallback(it)) } ?: fetchEach(listOf(earlier, later))
        val streams = fetched.filterIsInstance<Fetch.Got>().map { it.stream }.takeIf { it.size == 2 }
        val coarse = fetched.filterIsInstance<Fetch.Fallback>().firstOrNull()?.coarse
        return WorkoutComparison(
            resolution = if (streams != null) Resolution.STREAM else Resolution.SPLITS,
            coarse = coarse,
            earlier = side(earlier, streams?.get(0)),
            later = side(later, streams?.get(1)),
            reps = RepComparer.compare(earlier, later, streams?.get(0), streams?.get(1)),
        )
    }

    private fun side(
        run: RunActivity,
        stream: RunStream?,
    ): CompareSide =
        CompareSide(
            run = run,
            stats = CompareCalculator.stats(run, stream?.drift),
            points = stream?.let(CompareCalculator::streamPoints) ?: CompareCalculator.splitPoints(run.splits),
        )

    /** The first reason, found before any Garmin call, why per-second data can't be fetched. */
    private suspend fun upFront(
        earlier: RunActivity,
        later: RunActivity,
    ): Coarse? {
        val limitedUntil = syncState.get().rateLimitedUntil?.takeIf { it.isAfter(clock.instant()) }
        val runCheck =
            listOf(earlier, later).firstNotNullOfOrNull { run ->
                when {
                    run.localDate.isBefore(activitiesFrom) -> Coarse(CoarseReason.BEFORE_GARMIN, run.id)
                    run.trainer -> Coarse(CoarseReason.TREADMILL, run.id)
                    else -> null
                }
            }
        return when {
            source == null -> Coarse(CoarseReason.NOT_CONFIGURED)
            runCheck != null -> runCheck
            else -> limitedUntil?.let { Coarse(CoarseReason.RATE_LIMITED, retryAfter = it) }
        }
    }

    /** One fetch per run, earlier first; the first fallback stops further calls. */
    private suspend fun fetchEach(pair: List<RunActivity>): List<Fetch> {
        val garmin = requireNotNull(source) { "upFront reports a missing source" }
        val fetched = mutableListOf<Fetch>()
        for (run in pair) {
            fetched += fetch(garmin, run)
            if (fetched.last() is Fetch.Fallback) break
        }
        return fetched
    }

    /** The drift stream when the run has HR (a NotFound then tries speed only), else the speed stream. */
    private suspend fun fetch(
        garmin: ActivitySource,
        run: RunActivity,
    ): Fetch =
        if (run.averageHeartRateBpm == null) {
            speed(garmin, run)
        } else {
            when (val drift = charged { garmin.driftStream(run.id) }) {
                null -> callLimit()
                is SourceResult.Success ->
                    Fetch.Got(RunStream(SpeedStream(drift.value.times, drift.value.speeds), drift.value))
                SourceResult.NotFound -> speed(garmin, run)
                else -> failure(drift)
            }
        }

    private suspend fun speed(
        garmin: ActivitySource,
        run: RunActivity,
    ): Fetch =
        when (val speed = charged { garmin.speedStream(run.id) }) {
            null -> callLimit()
            is SourceResult.Success -> Fetch.Got(RunStream(speed.value, null))
            SourceResult.NotFound -> Fetch.Fallback(Coarse(CoarseReason.NO_DETAIL_FILE, run.id))
            else -> failure(speed)
        }

    /** Takes one budget unit first; null when the window has none left. */
    private suspend fun <T> charged(call: suspend () -> SourceResult<T>): SourceResult<T>? =
        if (budget.tryConsume()) call() else null

    private fun callLimit(): Fetch = Fetch.Fallback(Coarse(CoarseReason.CALL_LIMIT, retryAfter = budget.windowEnd()))

    companion object {
        const val LIST_SIZE = 30
    }
}
