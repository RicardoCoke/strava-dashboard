package app.stravadashboard.domain.wellness

/** The facts-only readiness note of the wellness card (feature 015, R7). Never gives advice. */
object ReadinessNote {
    private const val LOAD_SUFFIX = " while load is high"

    fun build(
        view: WellnessView,
        loadHigh: Boolean?,
    ): String? {
        val hrv = view.hrv?.takeIf { it.baseline != null }
        val restingHr = view.restingHr?.takeIf { it.baseline != null }
        if (hrv == null && restingHr == null) return null
        val anyFlagged = hrv?.flagged == true || restingHr?.flagged == true
        val body = if (anyFlagged) offBaseline(hrv, restingHr) else withinBaseline(hrv != null, restingHr != null)
        return if (loadHigh == true) body + LOAD_SUFFIX else body
    }

    private fun withinBaseline(
        hrvEvaluable: Boolean,
        restingHrEvaluable: Boolean,
    ): String =
        when {
            hrvEvaluable && restingHrEvaluable -> "Resting HR and HRV within baseline"
            restingHrEvaluable -> "Resting HR within baseline"
            else -> "HRV within baseline"
        }

    /** HRV first; the streak is the longest of the flagged metrics, shown from 2 days. */
    private fun offBaseline(
        hrv: LatestReading?,
        restingHr: LatestReading?,
    ): String {
        val parts =
            listOfNotNull(
                hrv?.takeIf { it.flagged }?.let { "HRV below baseline" },
                restingHr?.takeIf { it.flagged }?.let { "Resting HR above baseline" },
            )
        val text = parts.first() + parts.drop(1).joinToString("") { " and " + it.replaceFirstChar(Char::lowercase) }
        val streak = listOfNotNull(hrv, restingHr).filter { it.flagged }.maxOf { it.flagStreak }
        return if (streak > 1) "$text $streak days running" else text
    }
}
