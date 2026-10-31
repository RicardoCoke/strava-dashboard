package app.stravadashboard.domain

import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.SecondsPerKm

/** Finds the treadmill pace line (e.g. "4:15 4:10") in a run description. */
object TreadmillPaceParser {
    private val PACE_LINE = Regex("""^\s*\d{1,2}:[0-5]\d(\s+\d{1,2}:[0-5]\d)*\s*$""")
    private val WHITESPACE = Regex("""\s+""")
    private const val MIN_PACE_SECONDS = 120
    private const val MAX_PACE_SECONDS = 720

    /** The paces of the first line made up only of in-range `m:ss` tokens, or null if there is none. */
    fun parsePaceLine(description: String?): List<SecondsPerKm>? =
        description
            ?.lines()
            ?.asSequence()
            ?.filter { PACE_LINE.matches(it) }
            ?.map { line -> line.trim().split(WHITESPACE).map(::toSeconds) }
            ?.firstOrNull { paces -> paces.all { it in MIN_PACE_SECONDS..MAX_PACE_SECONDS } }
            ?.map(::SecondsPerKm)

    private fun toSeconds(token: String): Int {
        val (minutes, seconds) = token.split(":")
        return minutes.toInt() * SECONDS_PER_MINUTE + seconds.toInt()
    }
}
