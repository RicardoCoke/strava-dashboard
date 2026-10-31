package app.stravadashboard.domain

import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import kotlin.math.abs

/**
 * Marks the stretches where the rolling average heart rate of one run type is rising
 * (see docs/features/003-hr-rising-highlight/specs.md, R1 and R2).
 *
 * The state flips only after two moves of the average in the same direction that add up to at least
 * [THRESHOLD_TENTHS_BPM] from the extreme of the current stretch, so one odd run can't flip it.
 * The flip is backdated to the run after that extreme.
 */
object HrRisingTrend {
    const val WINDOW_RUNS = 3
    const val THRESHOLD_TENTHS_BPM = 10
    private const val TENTHS_PER_BPM = 10.0

    /** A flip needs the two previous moves of the average. */
    private const val STEPS_TO_CONFIRM = 2

    /** One flag per input bpm (same order); true = point is in a rising stretch. */
    fun flags(bpmInDateOrder: List<Double>): List<Boolean> {
        val flags = MutableList(bpmInDateOrder.size) { false }
        // averages[k] is the rolling average at point k + WINDOW_RUNS - 1.
        val averages = rollingTenths(bpmInDateOrder)
        val offset = WINDOW_RUNS - 1
        var rising = false
        var stretchStart = 0
        for (k in STEPS_TO_CONFIRM until averages.size) {
            val turn = turnStart(averages, k, stretchStart, rising)
            if (turn != null) {
                rising = !rising
                stretchStart = turn
                for (j in turn..k) flags[j + offset] = rising
            } else {
                flags[k + offset] = rising
            }
        }
        return flags
    }

    /** Rolling mean of [WINDOW_RUNS] runs, rounded to one decimal, in integer tenths of a bpm. */
    internal fun rollingTenths(bpm: List<Double>): List<Int> =
        bpm.windowed(WINDOW_RUNS).map { roundHalfUpToInt(roundOneDecimal(it.average()) * TENTHS_PER_BPM) }

    /** Index in [averages] where a confirmed turn at [k] begins, or null when the state holds. */
    private fun turnStart(
        averages: List<Int>,
        k: Int,
        stretchStart: Int,
        rising: Boolean,
    ): Int? {
        val previousStep = averages[k - 1] - averages[k - 2]
        val step = averages[k] - averages[k - 1]
        val stretch = averages.subList(stretchStart, k + 1)
        val extreme = if (rising) stretch.max() else stretch.min()
        val twoStepsSameWay = if (rising) previousStep < 0 && step < 0 else previousStep > 0 && step > 0
        val movedEnough = abs(averages[k] - extreme) >= THRESHOLD_TENTHS_BPM
        return if (twoStepsSameWay && movedEnough) stretchStart + stretch.lastIndexOf(extreme) + 1 else null
    }
}
