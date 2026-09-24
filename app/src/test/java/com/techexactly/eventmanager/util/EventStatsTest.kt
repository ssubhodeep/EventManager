package com.techexactly.eventmanager.util

import com.techexactly.eventmanager.data.model.Event
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the dashboard's stats/chart-bucketing math (total/upcoming/past counts and
 * grouping events into "events per month" buckets), independent of the ViewModel/Firestore
 * that feeds it in the running app - see DashboardViewModel, which just calls
 * EventStats.compute(...) on whatever the real-time listener returns.
 */
class EventStatsTest {

    private fun millisFor(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `empty list produces zeroed stats`() {
        val stats = EventStats.compute(emptyList(), nowMillis = millisFor(2026, 0, 1))
        assertEquals(0, stats.total)
        assertEquals(0, stats.upcoming)
        assertEquals(0, stats.past)
        assertEquals(emptyList<String>(), stats.monthLabels)
    }

    @Test
    fun `splits events into upcoming and past relative to now`() {
        val now = millisFor(2026, 5, 15) // 15 Jun 2026
        val events = listOf(
            Event(id = "1", title = "Past", dateTimeMillis = millisFor(2026, 5, 1)),
            Event(id = "2", title = "Future", dateTimeMillis = millisFor(2026, 5, 20)),
            Event(id = "3", title = "Future2", dateTimeMillis = millisFor(2026, 6, 1))
        )

        val stats = EventStats.compute(events, nowMillis = now)

        assertEquals(3, stats.total)
        assertEquals(2, stats.upcoming)
        assertEquals(1, stats.past)
    }

    @Test
    fun `groups events into chronologically ordered month buckets`() {
        val events = listOf(
            Event(id = "1", title = "A", dateTimeMillis = millisFor(2026, 8, 5)),  // Sep 2026
            Event(id = "2", title = "B", dateTimeMillis = millisFor(2026, 8, 20)), // Sep 2026
            Event(id = "3", title = "C", dateTimeMillis = millisFor(2026, 6, 10)), // Jul 2026 (earlier, listed first)
            Event(id = "4", title = "D", dateTimeMillis = millisFor(2026, 9, 1))   // Oct 2026 (later, listed last)
        )

        val stats = EventStats.compute(events, nowMillis = millisFor(2026, 0, 1))

        assertEquals(3, stats.monthLabels.size)
        assertEquals(listOf(1, 2, 1), stats.monthCounts) // Jul: 1, Sep: 2, Oct: 1, in chronological order
    }
}
