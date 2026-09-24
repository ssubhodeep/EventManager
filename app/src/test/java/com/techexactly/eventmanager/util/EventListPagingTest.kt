package com.techexactly.eventmanager.util

import com.techexactly.eventmanager.data.model.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the events list's search + status-filter + pagination math, independent of the
 * ViewModel/Firestore that feeds it in the running app - see EventListViewModel, which just
 * calls EventListPaging.compute(...) on whatever the real-time listener returns plus the current
 * search box / filter chip / page size.
 */
class EventListPagingTest {

    private fun event(title: String, location: String = "", offsetMillis: Long) = Event(
        id = title,
        title = title,
        location = location,
        dateTimeMillis = System.currentTimeMillis() + offsetMillis
    )

    @Test
    fun `first page caps the list and reports more available`() {
        val events = (1..25).map { event("Event $it", offsetMillis = it * 1000L) }

        val state = EventListPaging.compute(events, query = "", filter = EventStatusFilter.ALL, visibleCount = 10)

        assertEquals(10, state.events.size)
        assertTrue(state.canLoadMore)
        assertFalse(state.isFiltered)
    }

    @Test
    fun `visible count covering every match reports no more pages`() {
        val events = (1..5).map { event("Event $it", offsetMillis = it * 1000L) }

        val state = EventListPaging.compute(events, query = "", filter = EventStatusFilter.ALL, visibleCount = 10)

        assertEquals(5, state.events.size)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `search matches title or location case-insensitively`() {
        val events = listOf(
            event("Team standup", location = "Room A", offsetMillis = 1000),
            event("Board game night", location = "Home", offsetMillis = 2000),
            event("Client call", location = "conference ROOM b", offsetMillis = 3000)
        )

        val state = EventListPaging.compute(events, query = "room", filter = EventStatusFilter.ALL, visibleCount = 10)

        assertEquals(setOf("Team standup", "Client call"), state.events.map { it.title }.toSet())
        assertTrue(state.isFiltered)
    }

    @Test
    fun `status filter narrows to upcoming or past events`() {
        val events = listOf(
            event("Past event", offsetMillis = -100_000),
            event("Future event", offsetMillis = 100_000)
        )

        val upcoming = EventListPaging.compute(events, query = "", filter = EventStatusFilter.UPCOMING, visibleCount = 10)
        val past = EventListPaging.compute(events, query = "", filter = EventStatusFilter.PAST, visibleCount = 10)

        assertEquals(listOf("Future event"), upcoming.events.map { it.title })
        assertEquals(listOf("Past event"), past.events.map { it.title })
        assertTrue(upcoming.isFiltered)
    }

    @Test
    fun `no query and ALL filter is not considered filtered`() {
        val state = EventListPaging.compute(emptyList(), query = "  ", filter = EventStatusFilter.ALL, visibleCount = 10)
        assertFalse(state.isFiltered)
    }
}
