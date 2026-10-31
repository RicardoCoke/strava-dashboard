package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.HrHistogram

/** Reduces a heart-rate stream to seconds per bpm, so the stream itself never needs to be stored. */
object HrHistogramBuilder {
    /** Longer gaps between samples are pauses (auto-pause, stops) and add no time. */
    const val MAX_SAMPLE_GAP_SECONDS = 30

    /** Each interval adds its length to the bpm at its start; the last sample adds nothing. */
    fun build(stream: HeartRateStream): HrHistogram {
        val secondsByBpm = mutableMapOf<Int, Int>()
        for (i in 0 until stream.times.size - 1) {
            val dt = stream.times[i + 1].value - stream.times[i].value
            val bpm = stream.bpm[i].value
            if (dt in 1..MAX_SAMPLE_GAP_SECONDS && bpm > 0) {
                secondsByBpm[bpm] = (secondsByBpm[bpm] ?: 0) + dt
            }
        }
        return HrHistogram(secondsByBpm)
    }
}
