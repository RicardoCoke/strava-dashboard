package app.stravadashboard.api

import app.stravadashboard.domain.efficiency.EfChange
import app.stravadashboard.domain.efficiency.EfCurrent
import app.stravadashboard.domain.efficiency.EfficiencyFactorReport
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class EfficiencyFactorMappersTest {
    @Test
    fun `R5 - change JSON has its exact fields and rounding`() {
        val from = EfCurrent(1.58504, LocalDate.of(2026, 5, 3), 4)
        val to = EfCurrent(1.65196, LocalDate.of(2026, 10, 29), 6)
        val report =
            EfficiencyFactorReport(
                windowStart = LocalDate.of(2026, 4, 30),
                windowEnd = LocalDate.of(2026, 10, 31),
                current = to,
                change = EfChange(4.2219, from, to),
                points = emptyList(),
                runsPendingDetail = 0,
            )

        val json = Json.encodeToString(EfficiencyFactorResponse.serializer(), report.toDto())

        assertEquals(
            """{"windowStart":"2026-04-30","windowEnd":"2026-10-31","rollingDays":28,"minRunsForRolling":3,""" +
                """"current":{"efficiency":1.652,"date":"2026-10-29","runCount":6},""" +
                """"change":{"percent":4.2,"fromDate":"2026-05-03","fromEfficiency":1.585,""" +
                """"toDate":"2026-10-29","toEfficiency":1.652},"points":[],"runsPendingDetail":0}""",
            json,
        )
    }
}
