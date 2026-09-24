package com.techexactly.eventmanager.util

import com.techexactly.eventmanager.data.model.Event

/** The three ways [com.techexactly.eventmanager.ui.events.EventListFragment] can narrow the
 *  list, on top of free-text search. */
enum class EventStatusFilter { ALL, UPCOMING, PAST }

/**
 * What [com.techexactly.eventmanager.ui.events.EventListFragment] actually renders: the current
 * page of (search + status filtered) events, whether there are more already-fetched events
 * beyond this page, and whether a search/filter is even active (so the empty state can say
 * "no matches" instead of "no events yet" when that's what's really going on).
 */
data class EventListUiState(
    val events: List<Event> = emptyList(),
    val canLoadMore: Boolean = false,
    val isFiltered: Boolean = false
)

/**
 * Pure search + status-filter + pagination logic behind the events list - deliberately free of
 * any Android/Firebase/ViewModel dependency so it can be unit tested directly (see
 * app/src/test/.../EventListPagingTest.kt), the same way EventStats/Validators are.
 * [com.techexactly.eventmanager.ui.events.EventListViewModel] is a thin wrapper that feeds the
 * live Firestore stream, search box and filter chips into [compute].
 */
object EventListPaging {

    fun compute(events: List<Event>, query: String, filter: EventStatusFilter, visibleCount: Int): EventListUiState {
        val matched = events.filter { matchesQuery(it, query) && matchesFilter(it, filter) }
        return EventListUiState(
            events = matched.take(visibleCount),
            canLoadMore = matched.size > visibleCount,
            isFiltered = query.isNotBlank() || filter != EventStatusFilter.ALL
        )
    }

    private fun matchesQuery(event: Event, query: String): Boolean =
        query.isBlank() || event.title.contains(query, ignoreCase = true) || event.location.contains(query, ignoreCase = true)

    private fun matchesFilter(event: Event, filter: EventStatusFilter): Boolean = when (filter) {
        EventStatusFilter.ALL -> true
        EventStatusFilter.UPCOMING -> event.isUpcoming
        EventStatusFilter.PAST -> !event.isUpcoming
    }
}
