package com.techexactly.eventmanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techexactly.eventmanager.data.model.Resource
import com.techexactly.eventmanager.data.repository.EventRepository
import com.techexactly.eventmanager.util.EventStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val total: Int = 0,
    val upcoming: Int = 0,
    val past: Int = 0,
    val monthLabels: List<String> = emptyList(),
    val monthCounts: List<Int> = emptyList(),
    val isFromCache: Boolean = false
)

/**
 * Derives dashboard stats + the "events per month" chart data purely from the same
 * real-time event stream [com.techexactly.eventmanager.ui.events.EventListViewModel] uses -
 * no separate query, so the dashboard updates live too. The actual grouping/counting math
 * lives in [EventStats] so it can be unit tested without Firebase or Android in the loop.
 * [DashboardUiState.isFromCache] mirrors Firestore's own metadata so the dashboard can show
 * "may be a moment out of date" instead of quietly presenting cached numbers as fresh.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeEvents().collect { resource ->
                _uiState.value = when (resource) {
                    is Resource.Loading -> _uiState.value.copy(isLoading = true)
                    is Resource.Error -> _uiState.value.copy(isLoading = false)
                    is Resource.Success -> {
                        val stats = EventStats.compute(resource.data.events)
                        DashboardUiState(
                            isLoading = false,
                            total = stats.total,
                            upcoming = stats.upcoming,
                            past = stats.past,
                            monthLabels = stats.monthLabels,
                            monthCounts = stats.monthCounts,
                            isFromCache = resource.data.isFromCache
                        )
                    }
                }
            }
        }
    }
}
