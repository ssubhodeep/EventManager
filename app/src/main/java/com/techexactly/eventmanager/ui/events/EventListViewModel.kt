package com.techexactly.eventmanager.ui.events

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techexactly.eventmanager.data.model.Event
import com.techexactly.eventmanager.data.model.EventsSnapshot
import com.techexactly.eventmanager.data.model.Resource
import com.techexactly.eventmanager.data.repository.EventRepository
import com.techexactly.eventmanager.notifications.EventReminderWorker
import com.techexactly.eventmanager.util.ConnectivityObserver
import com.techexactly.eventmanager.util.EventListPaging
import com.techexactly.eventmanager.util.EventListUiState
import com.techexactly.eventmanager.util.EventStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the small offline/syncing indicator shown above the events list. */
data class SyncStatus(
    val isOnline: Boolean = true,
    val hasPendingWrites: Boolean = false
)

/**
 * Backs [EventListFragment]. Combines the live Firestore event stream with an in-memory
 * search query, status filter and page size so none of search/filter/pagination (bonus
 * requirements) need their own round trip to Firestore - they just re-filter and re-slice
 * whatever the real-time listener already delivered. [loadMore] simply raises the page size,
 * which is instant since the data is already in memory; it's the dashboard
 * ([com.techexactly.eventmanager.ui.dashboard.DashboardViewModel]) that still sees every event,
 * since it reads the same repository stream independently rather than through this pagination.
 *
 * Also surfaces [syncStatus] (device connectivity + "has local writes not yet synced"), which
 * is what lets the UI show an honest "You're offline - changes will sync automatically" banner
 * instead of silently working or throwing a confusing error.
 */
@HiltViewModel
class EventListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EventRepository,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val eventsSnapshotResource = MutableStateFlow<Resource<EventsSnapshot>>(Resource.Loading)
    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow(EventStatusFilter.ALL)
    private val visibleCount = MutableStateFlow(PAGE_SIZE)

    val uiState: StateFlow<Resource<EventListUiState>> =
        combine(eventsSnapshotResource, searchQuery, statusFilter, visibleCount) { resource, query, filter, visible ->
            when (resource) {
                is Resource.Success -> Resource.Success(EventListPaging.compute(resource.data.events, query, filter, visible))
                is Resource.Loading -> Resource.Loading
                is Resource.Error -> Resource.Error(resource.message)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val syncStatus: StateFlow<SyncStatus> =
        combine(connectivityObserver.observe(), eventsSnapshotResource) { isOnline, resource ->
            val pending = (resource as? Resource.Success)?.data?.hasPendingWrites ?: false
            SyncStatus(isOnline = isOnline, hasPendingWrites = pending)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus())

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors

    init {
        viewModelScope.launch {
            repository.observeEvents().collect { eventsSnapshotResource.value = it }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        visibleCount.value = PAGE_SIZE
    }

    fun setStatusFilter(filter: EventStatusFilter) {
        statusFilter.value = filter
        visibleCount.value = PAGE_SIZE
    }

    /** Reveals the next page of already-fetched events - no network round trip needed. */
    fun loadMore() {
        visibleCount.value += PAGE_SIZE
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event.id)
                .onSuccess { EventReminderWorker.cancel(context, event.id) }
                .onFailure { _errors.emit(it.message ?: "Could not delete event") }
        }
    }

    /** Undo for a just-deleted event: writes the same event back under its original id. */
    fun restoreEvent(event: Event) {
        viewModelScope.launch {
            repository.restoreEvent(event)
                .onSuccess {
                    EventReminderWorker.schedule(
                        context, event.id, event.title, event.location, event.dateTimeMillis
                    )
                }
                .onFailure { _errors.emit(it.message ?: "Could not restore event") }
        }
    }

    companion object {
        private const val PAGE_SIZE = 10
    }
}
