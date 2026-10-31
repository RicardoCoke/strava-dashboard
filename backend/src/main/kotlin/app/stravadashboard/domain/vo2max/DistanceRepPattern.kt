package app.stravadashboard.domain.vo2max

/**
 * Distance-rep workouts ("6x1km", "10x400m", "5 x 1000m") have no rule yet to find their rep laps, so the VO2max
 * estimate leaves them out. Only the run name is checked.
 */
object DistanceRepPattern {
    private val PATTERN =
        Regex("""(?<![\d.])(\d{1,2})\s*[x×]\s*\d+(?:[.,]\d+)?\s*(?:km|k|m)(?![a-z])""", RegexOption.IGNORE_CASE)

    fun matches(name: String): Boolean = PATTERN.containsMatchIn(name)
}
