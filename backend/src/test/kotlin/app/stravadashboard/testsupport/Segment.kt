package app.stravadashboard.testsupport

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.workoutcompare.RunStream

// All streams here are invented.

/** [seconds] of 1 Hz samples at [speed] m/s and [bpm]. */
data class Segment(
    val seconds: Int,
    val speed: Double,
    val bpm: Int,
)

fun driftStream(vararg segments: Segment): DriftStream {
    val samples = segments.flatMap { s -> List(s.seconds) { s } }
    return DriftStream(
        times = samples.indices.map(::Seconds),
        speeds = samples.map { MetersPerSecond(it.speed) },
        bpm = samples.map { Bpm(it.bpm) },
        moving = null,
    )
}

/** A 5-minute warm-up, then [reps] reps of [repSeconds] with a [recoverySeconds] jog after each. */
@Suppress("LongParameterList")
fun intervalStream(
    reps: Int,
    repSeconds: Int,
    rep: List<Segment> = listOf(Segment(repSeconds, 4.0, 170)),
    recoverySeconds: Int = 120,
    recovery: Segment = Segment(recoverySeconds, 2.5, 140),
): DriftStream =
    driftStream(
        Segment(300, 2.5, 130),
        *(1..reps).flatMap { rep + recovery }.toTypedArray(),
    )

fun DriftStream.toRunStream(withHr: Boolean = true): RunStream =
    RunStream(SpeedStream(times, speeds), this.takeIf { withHr })
