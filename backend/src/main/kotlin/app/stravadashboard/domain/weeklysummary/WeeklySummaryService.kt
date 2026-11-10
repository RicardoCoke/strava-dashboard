package app.stravadashboard.domain.weeklysummary

import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.WellnessRepository
import app.stravadashboard.domain.trainingload.HrZones
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Builds the weekly summaries from the local run and wellness caches; never calls Garmin. */
class WeeklySummaryService(
    private val lthr: LthrSetting,
    private val runs: RunRepository,
    private val wellness: WellnessRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): List<WeeklySummary> {
        val today = LocalDate.now(clock.withZone(zone))
        val zones = (lthr as? LthrSetting.Valid)?.let { HrZones.of(it.lthr) }
        val days = wellness.findBetween(WeeklySummaryBuilder.oldestNeeded(today), today)
        return WeeklySummaryBuilder.build(runs.findAll(), days, zones, today)
    }
}
