package app.stravadashboard.garmin

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.model.roundHalfUpToInt
import com.garmin.fit.FitDecoder
import com.garmin.fit.LapMesg
import com.garmin.fit.RecordMesg
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.zip.ZipInputStream

private const val MIN_STREAM_SAMPLES = 2
private const val METERS_PER_KM = 1000.0
private const val MAX_FIT_BYTES = 32 * 1024 * 1024

/**
 * One FIT `record` message, reduced to what the app uses. There is deliberately no position field: GPS is never
 * read from the file (feature 021, R5). [seconds] counts from the first record.
 */
internal data class FitSample(
    val seconds: Int,
    val distance: Double?,
    val speed: Double?,
    val heartRate: Int?,
    val altitude: Double?,
)

/** What the app keeps of one activity's FIT file: its laps and its per-second samples. */
internal data class FitData(
    val laps: List<Lap>,
    val samples: List<FitSample>,
) {
    val splits: List<Split> get() = kmSplits(samples)

    fun speedStream(): SpeedStream? =
        samples
            .filter { it.speed != null }
            .takeIf { it.size >= MIN_STREAM_SAMPLES }
            ?.let { s -> SpeedStream(s.map { Seconds(it.seconds) }, s.map { MetersPerSecond(it.speed ?: 0.0) }) }

    fun heartRateStream(): HeartRateStream? =
        samples
            .filter { it.heartRate != null }
            .takeIf { it.size >= MIN_STREAM_SAMPLES }
            ?.let { s -> HeartRateStream(s.map { Seconds(it.seconds) }, s.map { Bpm(it.heartRate ?: 0) }) }

    /** Garmin records have no moving flag, so every interval counts as moving (as with a Strava stream without one). */
    fun driftStream(): DriftStream? =
        samples
            .filter { it.speed != null && it.heartRate != null }
            .takeIf { it.size >= MIN_STREAM_SAMPLES }
            ?.let { s ->
                DriftStream(
                    s.map { Seconds(it.seconds) },
                    s.map { MetersPerSecond(it.speed ?: 0.0) },
                    s.map { Bpm(it.heartRate ?: 0) },
                    null,
                )
            }
}

/** Reads the activity file Garmin's download endpoint returns: a zip holding the original `.fit` file. */
internal object GarminFit {
    private val log = LoggerFactory.getLogger(GarminFit::class.java)

    /**
     * Null when the zip has no `.fit` entry, is larger than 32 MB unzipped, or doesn't decode. Any runtime exception
     * from the decoder counts as unreadable: a file never changes, so failing the sync on it would fail every sync.
     */
    @Suppress("TooGenericExceptionCaught")
    fun parse(zip: ByteArray): FitData? =
        try {
            fitBytes(zip)?.let(::decode)
        } catch (e: IOException) {
            unreadable(e)
        } catch (e: RuntimeException) {
            unreadable(e)
        }

    private fun fitBytes(zip: ByteArray): ByteArray? =
        ZipInputStream(zip.inputStream()).use { entries ->
            generateSequence { entries.nextEntry }
                .firstOrNull { it.name.endsWith(".fit", ignoreCase = true) }
                ?.let { entries.readNBytes(MAX_FIT_BYTES + 1) }
                ?.takeIf { it.size <= MAX_FIT_BYTES }
        }

    private fun decode(fit: ByteArray): FitData {
        val messages = FitDecoder().decode(fit.inputStream())
        return FitData(laps(messages.lapMesgs), samples(messages.recordMesgs))
    }

    private fun laps(mesgs: List<LapMesg>): List<Lap> =
        mesgs.mapIndexed { i, lap ->
            Lap(
                index = i + 1,
                elapsed = Seconds(roundHalfUpToInt(lap.totalElapsedTime?.toDouble() ?: 0.0)),
                distance = Meters(lap.totalDistance?.toDouble() ?: 0.0),
                averageHeartRateBpm = lap.avgHeartRate?.toDouble(),
            )
        }

    /** Position fields are never read; a record without a timestamp is skipped. */
    private fun samples(mesgs: List<RecordMesg>): List<FitSample> {
        val timed = mesgs.mapNotNull { record -> record.timestamp?.timestamp?.let { it to record } }
        val first = timed.firstOrNull()?.first ?: return emptyList()
        return timed.map { (timestamp, record) ->
            FitSample(
                seconds = (timestamp - first).toInt(),
                distance = record.distance?.toDouble(),
                speed = (record.enhancedSpeed ?: record.speed)?.toDouble(),
                heartRate = record.heartRate?.toInt(),
                altitude = (record.enhancedAltitude ?: record.altitude)?.toDouble(),
            )
        }
    }

    private fun unreadable(e: Exception): FitData? {
        // Class name only: a decoder message could quote file content.
        log.warn("Garmin activity file could not be read: {}", e::class.simpleName)
        return null
    }
}

private class SplitPoint(
    val seconds: Int,
    val distance: Double,
    val heartRate: Int?,
    val altitude: Double?,
)

/**
 * Per-km splits from the records' cumulative distance (feature 021, AC4.4). A split closes at the first record at or
 * past the next whole kilometre; the remainder becomes a last, shorter split. Moving time counts the intervals in
 * which distance grew, and heart rate is time-weighted over them using each interval's later record. FIT has no
 * grade-adjusted speed, so the split carries the elevation difference instead and feature 016's hill adjustment
 * applies.
 */
internal fun kmSplits(samples: List<FitSample>): List<Split> {
    val points = samples.mapNotNull { s -> s.distance?.let { SplitPoint(s.seconds, it, s.heartRate, s.altitude) } }
    if (points.size < MIN_STREAM_SAMPLES) return emptyList()
    val splits = mutableListOf<Split>()
    var acc = SplitAccumulator(points.first())
    for ((previous, current) in points.zipWithNext()) {
        acc.add(previous, current)
        if (current.distance >= (splits.size + 1) * METERS_PER_KM) {
            splits += acc.close(splits.size + 1, current)
            acc = SplitAccumulator(current)
        }
    }
    val last = points.last()
    if (last.distance > acc.start.distance && acc.movingSeconds > 0) splits += acc.close(splits.size + 1, last)
    return splits
}

private class SplitAccumulator(
    val start: SplitPoint,
) {
    var movingSeconds = 0
        private set
    private var hrWeighted = 0.0
    private var hrSeconds = 0

    fun add(
        previous: SplitPoint,
        current: SplitPoint,
    ) {
        val dt = current.seconds - previous.seconds
        if (dt <= 0 || current.distance <= previous.distance) return
        movingSeconds += dt
        current.heartRate?.let {
            hrWeighted += it.toDouble() * dt
            hrSeconds += dt
        }
    }

    fun close(
        index: Int,
        end: SplitPoint,
    ): Split =
        Split(
            index = index,
            distance = Meters(end.distance - start.distance),
            movingTime = Seconds(movingSeconds),
            elevationDifference =
                if (end.altitude != null && start.altitude != null) Meters(end.altitude - start.altitude) else null,
            averageHeartRateBpm = if (hrSeconds > 0) hrWeighted / hrSeconds else null,
            gradeAdjustedSpeed = null,
        )
}
