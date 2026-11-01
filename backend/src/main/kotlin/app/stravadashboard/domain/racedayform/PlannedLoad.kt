package app.stravadashboard.domain.racedayform

import app.stravadashboard.domain.trainingplan.PlanDay
import app.stravadashboard.domain.trainingplan.TargetPace

/** The load a planned day is expected to bring, from its minutes and the learned rates (R5). */
object PlannedLoad {
    const val EASY_PACE_KEY = "easy"
    private const val SECONDS_PER_MINUTE = 60.0

    /**
     * Easy minutes × easy rate + SubT minutes × SubT rate. A day with distance but no minutes is run at [easyPace]
     * (seconds per km) as easy minutes; a rest day, an empty day or no plan entry is 0.
     */
    fun of(
        day: PlanDay?,
        rates: LoadRates,
        easyPace: EasyPace?,
    ): Double =
        when {
            day == null -> 0.0
            day.totalMinutes > 0 -> day.easyMinutes * rates.easyPerMinute + day.subtMinutes * rates.subtPerMinute
            easyPace != null && day.distance.value > 0 ->
                day.distance.value * easyPace.secondsPerKm / SECONDS_PER_MINUTE * rates.easyPerMinute
            else -> 0.0
        }

    /** The plan's easy pace (its target, or the middle of its range), else the pace of the sampled easy runs. */
    fun easyPace(
        paces: List<TargetPace>,
        rates: LoadRates,
    ): EasyPace? {
        val easy = paces.find { it.key == EASY_PACE_KEY }
        val min = easy?.min
        val max = easy?.max
        val fromPlan =
            easy?.target?.value?.toDouble()
                ?: if (min != null && max != null) (min.value + max.value) / 2.0 else null
        return fromPlan?.let(::EasyPace) ?: rates.easyPace
    }
}
