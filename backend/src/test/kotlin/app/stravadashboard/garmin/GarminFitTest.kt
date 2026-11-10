package app.stravadashboard.garmin

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.testsupport.FitLap
import app.stravadashboard.testsupport.FitRecord
import app.stravadashboard.testsupport.fitZip
import app.stravadashboard.testsupport.zip
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// All activity data here is invented.
class GarminFitTest {
    private fun parse(bytes: ByteArray): FitData = assertNotNull(GarminFit.parse(bytes))

    @Test
    fun `R4 - laps from FIT lap messages`() {
        val fit =
            parse(
                fitZip(
                    records = listOf(FitRecord(0), FitRecord(1)),
                    laps = listOf(FitLap(359.5f, 1000f, 150), FitLap(90.4f, 200f, null), FitLap(360f, null, 160)),
                ),
            )
        assertEquals(
            listOf(
                Lap(1, Seconds(360), Meters(1000.0), 150.0),
                Lap(2, Seconds(90), Meters(200.0), null),
                Lap(3, Seconds(360), Meters(0.0), 160.0),
            ),
            fit.laps,
        )
    }

    @Test
    fun `R4 - per-km splits from FIT records`() {
        // 2.5 km: 5 m/s for 500 s, then a 10 s stop, with HR 150 for the first km and 160 after.
        val records =
            (0..500).map { s ->
                FitRecord(s, distance = s * 5f, heartRate = if (s <= 200) 150 else 160, altitude = 100f + s / 10f)
            } + FitRecord(510, distance = 2500f, heartRate = 100, altitude = 150f)
        val splits = parse(fitZip(records)).splits

        assertEquals(listOf(1, 2, 3), splits.map { it.index })
        assertEquals(listOf(1000.0, 1000.0, 500.0), splits.map { it.distance.value })
        // The stop doesn't count as moving, and its HR is ignored.
        assertEquals(listOf(200, 200, 100), splits.map { it.movingTime.value })
        assertEquals(listOf(150.0, 160.0, 160.0), splits.map { it.averageHeartRateBpm })
        assertEquals(20.0, assertNotNull(splits.first().elevationDifference).value, 0.01)
        assertNull(splits.first().gradeAdjustedSpeed)
    }

    @Test
    fun `R4 - records without distance give no splits`() {
        assertEquals(emptyList(), parse(fitZip(listOf(FitRecord(0), FitRecord(1)))).splits)
    }

    @Test
    fun `R5 - speed HR and drift streams from FIT records`() {
        val fit =
            parse(
                fitZip(
                    listOf(
                        FitRecord(0, speed = 3.0f, heartRate = 140),
                        FitRecord(1, speed = 3.5f),
                        FitRecord(3, heartRate = 142),
                        FitRecord(4, speed = 4.0f, heartRate = 144),
                    ),
                ),
            )

        val speed = assertNotNull(fit.speedStream())
        assertEquals(listOf(0, 1, 4), speed.times.map { it.value })
        assertEquals(listOf(3.0, 3.5, 4.0), speed.speeds.map { it.value })
        val hr = assertNotNull(fit.heartRateStream())
        assertEquals(listOf(0, 3, 4), hr.times.map { it.value })
        assertEquals(listOf(Bpm(140), Bpm(142), Bpm(144)), hr.bpm)
        val drift = assertNotNull(fit.driftStream())
        assertEquals(listOf(0, 4), drift.times.map { it.value })
        assertEquals(listOf(MetersPerSecond(3.0), MetersPerSecond(4.0)), drift.speeds)
        assertNull(drift.moving)
    }

    @Test
    fun `R5 - fewer than two samples is no stream`() {
        val fit = parse(fitZip(listOf(FitRecord(0, speed = 3.0f, heartRate = 140), FitRecord(1))))
        assertNull(fit.speedStream())
        assertNull(fit.heartRateStream())
        assertNull(fit.driftStream())
    }

    @Test
    fun `R5 - positions in FIT are ignored and never logged`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        val fit =
            try {
                parse(fitZip(listOf(FitRecord(0, distance = 0f, lat = 987_654_321), FitRecord(1, distance = 5f))))
            } finally {
                root.detachAppender(appender)
            }

        // FitSample has no position field; its text shows everything it holds.
        assertFalse("987654321" in fit.toString())
        assertFalse(appender.list.any { "987654321" in it.formattedMessage })
    }

    @Test
    fun `R4 - corrupt file, zip without a fit entry and plain bytes are unreadable`() {
        assertNull(GarminFit.parse(zip("invented.fit", "not a fit file".toByteArray())))
        assertNull(GarminFit.parse(zip("invented.txt", fitZip(listOf(FitRecord(0))))))
        assertNull(GarminFit.parse("not a zip".toByteArray()))
    }
}
