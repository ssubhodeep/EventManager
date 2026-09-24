package com.techexactly.eventmanager.ui.events

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techexactly.eventmanager.data.model.Event
import com.techexactly.eventmanager.data.repository.EventRepository
import com.techexactly.eventmanager.notifications.EventReminderWorker
import com.techexactly.eventmanager.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AddEditUiState {
    data object Idle : AddEditUiState()
    data object Loading : AddEditUiState()
    data object Saved : AddEditUiState()
    data class Error(val message: String) : AddEditUiState()
}

@HiltViewModel
class AddEditEventViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddEditUiState>(AddEditUiState.Idle)
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private val _loadedEvent = MutableStateFlow<Event?>(null)
    val loadedEvent: StateFlow<Event?> = _loadedEvent.asStateFlow()

    fun loadEvent(eventId: String) {
        viewModelScope.launch {
            repository.getEvent(eventId)
                .onSuccess { _loadedEvent.value = it }
                .onFailure { _uiState.value = AddEditUiState.Error(it.message ?: "Could not load event") }
        }
    }

    fun save(eventId: String?, title: String, description: String, location: String, dateTimeMillis: Long) {
        Validators.eventTitleError(title)?.let { _uiState.value = AddEditUiState.Error(it); return }
        Validators.eventDateError(dateTimeMillis)?.let { _uiState.value = AddEditUiState.Error(it); return }

        val trimmedTitle = title.trim()
        val trimmedLocation = location.trim()
        val event = Event(
            id = eventId.orEmpty(),
            title = trimmedTitle,
            description = description.trim(),
            location = trimmedLocation,
            dateTimeMillis = dateTimeMillis
        )

        viewModelScope.launch {
            _uiState.value = AddEditUiState.Loading

            if (eventId.isNullOrBlank()) {
                repository.addEvent(event)
                    .onSuccess { newId ->
                        scheduleReminder(newId, trimmedTitle, trimmedLocation, dateTimeMillis)
                        _uiState.value = AddEditUiState.Saved
                    }
                    .onFailure { _uiState.value = AddEditUiState.Error(it.message ?: "Could not save event") }
            } else {
                repository.updateEvent(event)
                    .onSuccess {
                        scheduleReminder(eventId, trimmedTitle, trimmedLocation, dateTimeMillis)
                        _uiState.value = AddEditUiState.Saved
                    }
                    .onFailure { _uiState.value = AddEditUiState.Error(it.message ?: "Could not save event") }
            }
        }
    }

    private fun scheduleReminder(eventId: String, title: String, location: String, dateTimeMillis: Long) {
        EventReminderWorker.schedule(context, eventId, title, location, dateTimeMillis)
    }
}
