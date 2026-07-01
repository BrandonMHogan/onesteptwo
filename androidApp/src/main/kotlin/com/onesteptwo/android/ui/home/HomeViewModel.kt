package com.onesteptwo.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.Children
import com.onesteptwo.db.Potty_events
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val activeChild: Children? = null,
    val todayEventCount: Int = 0,
    val pendingDetailsCount: Long = 0,
    val pendingSyncCount: Long = 0,
    val toastVisible: Boolean = false,
    val pressed: Boolean = false,
    val isLogging: Boolean = false
)

private const val TOAST_AUTO_DISMISS_MS = 12_000L

class HomeViewModel(
    private val pottyEventsRepository: PottyEventsRepository,
    activeChildFlow: StateFlow<Children?>
) : ViewModel() {

    private var todayStart: String = startOfTodayIso()
    private var todayEnd: String = startOfTomorrowIso()

    private val toastVisible = MutableStateFlow(false)
    private val pressed = MutableStateFlow(false)
    private val isLogging = MutableStateFlow(false)
    private val lastLoggedEventId = MutableStateFlow<String?>(null)

    private val selectedEvent = MutableStateFlow<Potty_events?>(null)
    val selectedEventForSheet: StateFlow<Potty_events?> = selectedEvent.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pendingDetailsCountFlow = activeChildFlow.flatMapLatest { child ->
        if (child == null) flowOf(0L)
        else pottyEventsRepository.observePendingDetailsCount(child.id, todayStart, todayEnd)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pendingSyncCountFlow = activeChildFlow.flatMapLatest { child ->
        if (child == null) flowOf(0L) else pottyEventsRepository.observePendingSyncCount(child.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayEventsFlow = activeChildFlow.flatMapLatest { child ->
        if (child == null) flowOf(emptyList<Potty_events>())
        else pottyEventsRepository.observeByChildAndDayRange(child.id, todayStart, todayEnd)
    }

    val state: StateFlow<HomeUiState> = combine(
        activeChildFlow,
        todayEventsFlow,
        pendingDetailsCountFlow,
        pendingSyncCountFlow,
        toastVisible,
        pressed,
        isLogging
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val child = values[0] as Children?
        @Suppress("UNCHECKED_CAST")
        val events = values[1] as List<Potty_events>
        val pendingDetails = values[2] as Long
        val pendingSync = values[3] as Long
        val toast = values[4] as Boolean
        val isPressed = values[5] as Boolean
        val logging = values[6] as Boolean
        HomeUiState(
            activeChild = child,
            todayEventCount = events.size,
            pendingDetailsCount = pendingDetails,
            pendingSyncCount = pendingSync,
            toastVisible = toast,
            pressed = isPressed,
            isLogging = logging
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun onPress(down: Boolean) {
        pressed.value = down
    }

    /** Log button tap (D-35 flow step 1-4): immediate SQLDelight insert, no network dependency. */
    fun logEvent() {
        val childId = state.value.activeChild?.id ?: return
        if (isLogging.value) return
        isLogging.value = true
        viewModelScope.launch {
            val loggedBy = Clerk.user?.id ?: ""
            val now = Instant.now().toString()
            val id = pottyEventsRepository.logNewEvent(childId, loggedBy, now)
            lastLoggedEventId.value = id
            toastVisible.value = true
            isLogging.value = false
            Timber.d("HomeViewModel: logged event id=$id")
            delay(TOAST_AUTO_DISMISS_MS)
            if (lastLoggedEventId.value == id) toastVisible.value = false
        }
    }

    /** Toast chip tap (flow step 5): sets event_type, dismisses toast. */
    fun pickEventType(eventType: String) {
        val id = lastLoggedEventId.value ?: return
        viewModelScope.launch {
            val updatedBy = Clerk.user?.id
            pottyEventsRepository.setEventType(id, eventType, updatedBy, Instant.now().toString())
            toastVisible.value = false
        }
    }

    fun dismissToast() {
        toastVisible.value = false
    }

    /** "add details" link in the toast, or tapping the "need details" status chip. */
    fun openAddDetails() {
        val id = lastLoggedEventId.value
        toastVisible.value = false
        if (id != null) {
            viewModelScope.launch {
                selectedEvent.value = pottyEventsRepository.getById(id)
            }
        } else {
            openFirstPendingDetailsEvent()
        }
    }

    fun openFirstPendingDetailsEvent() {
        val childId = state.value.activeChild?.id ?: return
        viewModelScope.launch {
            selectedEvent.value =
                pottyEventsRepository.getFirstPendingDetailsEvent(childId, todayStart, todayEnd)
        }
    }

    fun openEventDetail(event: Potty_events) {
        selectedEvent.value = event
    }

    fun dismissEventDetail() {
        selectedEvent.value = null
    }

    /** Event Detail sheet "Save details" — preserves the event's original created_at. */
    fun saveEventDetails(eventType: String?, notes: String?, occurredAt: String) {
        val event = selectedEvent.value ?: return
        viewModelScope.launch {
            pottyEventsRepository.updateDetails(
                id = event.id,
                eventType = eventType,
                notes = notes,
                occurredAt = occurredAt,
                updatedBy = Clerk.user?.id,
                now = Instant.now().toString()
            )
            selectedEvent.value = null
        }
    }
}

private fun startOfTodayIso(): String =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

private fun startOfTomorrowIso(): String =
    LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

class HomeViewModelFactory(
    private val pottyEventsRepository: PottyEventsRepository,
    private val activeChildFlow: StateFlow<Children?>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(pottyEventsRepository, activeChildFlow) as T
    }
}
