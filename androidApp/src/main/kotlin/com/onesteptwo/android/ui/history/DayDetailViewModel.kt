package com.onesteptwo.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.Potty_events
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayDetailViewModel(
    private val pottyEventsRepository: PottyEventsRepository,
    date: LocalDate,
    childId: String
) : ViewModel() {

    private val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
    private val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    val events: StateFlow<List<Potty_events>> =
        pottyEventsRepository.observeByChildAndDayRange(childId, dayStart, dayEnd)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedEvent = MutableStateFlow<Potty_events?>(null)
    val selectedEvent: StateFlow<Potty_events?> = _selectedEvent.asStateFlow()

    fun openEventDetail(event: Potty_events) {
        _selectedEvent.value = event
    }

    fun dismissEventDetail() {
        _selectedEvent.value = null
    }

    /** Event Detail sheet "Save details" — preserves the original occurred_at unless the caller passes an edited one. */
    fun saveEventDetails(eventType: String?, notes: String?, occurredAt: String) {
        val event = _selectedEvent.value ?: return
        viewModelScope.launch {
            pottyEventsRepository.updateDetails(
                id = event.id,
                eventType = eventType,
                notes = notes,
                occurredAt = occurredAt,
                updatedBy = Clerk.user?.id,
                now = Instant.now().toString()
            )
            _selectedEvent.value = null
        }
    }

    /** "Delete event" (05-CONTEXT.md D-06) — soft delete (REQ-007); excluded from the day list immediately via the reactive Flow. */
    fun deleteEvent(id: String) {
        viewModelScope.launch {
            pottyEventsRepository.deleteEvent(id, Clerk.user?.id, Instant.now().toString())
        }
    }
}

class DayDetailViewModelFactory(
    private val pottyEventsRepository: PottyEventsRepository,
    private val date: LocalDate,
    private val childId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DayDetailViewModel(pottyEventsRepository, date, childId) as T
}
