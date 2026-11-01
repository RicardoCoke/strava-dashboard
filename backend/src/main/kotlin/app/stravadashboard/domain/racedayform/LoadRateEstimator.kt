package app.stravadashboard.domain.racedayform

import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator
import app.stravadashboard.domain.trainingload.TrimpCalculator
import java.time.LocalDate

/**
 * Learns load per moving minute from the user's runs with heart rate over the last 6 months (R4): one rate for
 * easy and long runs, one for the SubT reps of `NxMmin` workouts.
 */
object LoadRateEstimator {
    const val MIN_RATE_RUNS = 3
    const val SUBT_FALLBACK_FACTOR = TrimpCalculator.SUBT_WEIGHT.toDouble() / TrimpCalculator.EASY_WEIGHT
    private const val SECONDS_PER_MINUTE = 60.0
    private const val METERS_PER_KM = 1000.0

    private data class Sample(
        val run: RunActivity,
        val load: Double,
        val minutes: Double,
    )

    fun estimate(
        runs: List<RunActivity>,
        zones: HrZones,
        today: LocalDate,
    ): RatesResult {
        val samples = sample(runs, zones, today)
        val easy = samples.filter { RunClassifier.classify(it.run) !is RunType.Workout }
        if (easy.size < MIN_RATE_RUNS) return RatesResult.NotEnoughEasyRuns
        val easyRate = easy.sumOf { it.load } / easy.sumOf { it.minutes }
        val workouts = samples.mapNotNull { s -> (RunClassifier.classify(s.run) as? RunType.Workout)?.let { s to it } }
        val subtRate = if (workouts.size >= MIN_RATE_RUNS) subtRate(workouts, easyRate) else null
        return RatesResult.Learned(
            LoadRates(
                easyPerMinute = easyRate,
                subtPerMinute = subtRate ?: (easyRate * SUBT_FALLBACK_FACTOR),
                subtEstimated = subtRate == null,
                easyPaceSecondsPerKm = averagePace(easy),
            ),
        )
    }

    /** Runs with a computed HR histogram and a moving time, in `today − 6 months … today` (AC4.5). */
    private fun sample(
        runs: List<RunActivity>,
        zones: HrZones,
        today: LocalDate,
    ): List<Sample> {
        val start = TrainingLoadCalculator.windowStart(today)
        return runs
            .filter { it.isRun && it.averageHeartRateBpm != null && it.localDate in start..today }
            .mapNotNull { run ->
                val histogram = run.hrHistogram?.takeIf { it.status == HrHistogramStatus.COMPUTED }?.histogram
                val seconds = run.movingTime?.value?.takeIf { it > 0 }
                if (histogram != null && seconds != null) {
                    Sample(run, TrimpCalculator.load(histogram, zones), seconds / SECONDS_PER_MINUTE)
                } else {
                    null
                }
            }
    }

    /** The workout load left after the warm-up and cool-down at the easy rate, per minute of reps (AC4.2). */
    private fun subtRate(
        workouts: List<Pair<Sample, RunType.Workout>>,
        easyRate: Double,
    ): Double {
        val work = workouts.map { (sample, type) -> minOf((type.reps * type.minutes).toDouble(), sample.minutes) }
        val easyPart = workouts.zip(work).sumOf { (pair, workMinutes) -> pair.first.minutes - workMinutes }
        val load = workouts.sumOf { it.first.load }
        return maxOf(0.0, (load - easyRate * easyPart) / work.sum())
    }

    private fun averagePace(easy: List<Sample>): Double? {
        val km = easy.sumOf { it.run.distance.value } / METERS_PER_KM
        return if (km > 0) easy.sumOf { it.minutes * SECONDS_PER_MINUTE } / km else null
    }
}
