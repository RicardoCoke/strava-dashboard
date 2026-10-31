package app.stravadashboard.domain

import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.NOW
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StravaCallBudgetTest {
    @Test
    fun `R4 - budget resets on quarter hour boundaries`() {
        // NOW is 10:00:00 UTC, the start of a window.
        val clock = MutableClock(NOW.plus(Duration.ofMinutes(14)))
        val budget = StravaCallBudget(clock, callsPerWindow = 2)

        assertTrue(budget.tryConsume())
        assertTrue(budget.tryConsume())
        assertFalse(budget.tryConsume())
        assertEquals(0, budget.remaining())
        assertEquals(NOW.plus(Duration.ofMinutes(15)), budget.windowEnd())

        clock.advance(Duration.ofMinutes(1))
        assertEquals(2, budget.remaining())
        assertEquals(NOW.plus(Duration.ofMinutes(30)), budget.windowEnd())
    }
}
