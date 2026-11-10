package app.stravadashboard.domain.rings

import java.time.Duration

/** Feature 020 constants. These approximate Whoop's published behaviour; Whoop's own formulas aren't public. */
object RingsPolicy {
    // The first graph day (today − 89) minus 3 nights of sleep debt (023 R4).
    const val HISTORY_DAYS = 92L
    const val RETENTION_DAYS = HISTORY_DAYS
    val MAX_SAMPLE_GAP: Duration = Duration.ofMinutes(5)
    val ZONE_PERCENTS = listOf(55, 66, 77, 88, 100)
    const val STRAIN_MAX = 21.0
    const val STRAIN_SCALE = 8.35
    const val STRAIN_KNEE_TRIMP = 65.0

    const val BASE_SLEEP_NEED_S = 28_800
    const val STRAIN_EXTRA_FROM = 10.0
    const val STRAIN_EXTRA_S_PER_POINT = 300
    const val DEBT_NIGHTS = 3L
    const val DEBT_PAYBACK = 0.25

    const val HRV_WEIGHT = 0.60
    const val RHR_WEIGHT = 0.25
    const val SLEEP_WEIGHT = 0.15
    const val HRV_SPAN = 0.20
    const val RHR_SPAN = 0.10

    const val BASELINE_DAYS = 30L
    const val MIN_BASELINE_VALUES = 3
    const val GREEN_FROM = 67
    const val YELLOW_FROM = 34
    const val MAX_PERCENT = 100.0
}
