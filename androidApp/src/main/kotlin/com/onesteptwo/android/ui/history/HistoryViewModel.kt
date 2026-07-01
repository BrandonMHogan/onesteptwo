package com.onesteptwo.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.Children
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val MAX_WEEKS = 12

data class HistoryUiState(
    val hasEverLogged: Boolean = false,
    val weeks: List<HeatmapWeekRow> = emptyList()
)

/** History tab (REQ-033): rolling heatmap that grows from week 1 and caps at 12 weeks (05-CONTEXT.md D-07). */
class HistoryViewModel(
    private val pottyEventsRepository: PottyEventsRepository,
    activeChildFlow: StateFlow<Children?>
) : ViewModel() {

    private val windowStart = mondayOfWeeksAgo(MAX_WEEKS - 1)
    private val windowStartIso = windowStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val earliestFlow = activeChildFlow.flatMapLatest { child ->
        if (child == null) flowOf(null) else pottyEventsRepository.observeEarliestOccurredAt(child.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val countsFlow = activeChildFlow.flatMapLatest { child ->
        if (child == null) flowOf(emptyList()) else pottyEventsRepository.observeDailyCounts(child.id, windowStartIso)
    }

    val state: StateFlow<HistoryUiState> = combine(earliestFlow, countsFlow) { earliest, counts ->
        if (earliest == null) {
            HistoryUiState(hasEverLogged = false, weeks = emptyList())
        } else {
            val countsByDate = counts.associate { LocalDate.parse(it.day) to it.eventCount.toInt() }
            val earliestDate = Instant.parse(earliest).atZone(ZoneId.systemDefault()).toLocalDate()
            val earliestMonday = earliestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val thisMonday = mondayOfWeeksAgo(0)
            val weeksElapsed = ChronoUnit.WEEKS.between(earliestMonday, thisMonday).toInt() + 1
            val weeksToShow = weeksElapsed.coerceIn(1, MAX_WEEKS)
            HistoryUiState(hasEverLogged = true, weeks = buildWeeks(weeksToShow, countsByDate))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    private fun buildWeeks(weeksToShow: Int, countsByDate: Map<LocalDate, Int>): List<HeatmapWeekRow> {
        val thisMonday = mondayOfWeeksAgo(0)
        val startMonday = thisMonday.minusWeeks((weeksToShow - 1).toLong())
        var lastMonth = -1
        return (0 until weeksToShow).map { w ->
            val weekMonday = startMonday.plusWeeks(w.toLong())
            val days = (0..6).map { d ->
                val date = weekMonday.plusDays(d.toLong())
                HeatmapDay(date, countsByDate[date] ?: 0)
            }
            val label = if (weekMonday.monthValue != lastMonth) {
                lastMonth = weekMonday.monthValue
                weekMonday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } else null
            HeatmapWeekRow(monthLabel = label, days = days)
        }
    }
}

private fun mondayOfWeeksAgo(weeksAgo: Int): LocalDate =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeksAgo.toLong())

class HistoryViewModelFactory(
    private val pottyEventsRepository: PottyEventsRepository,
    private val activeChildFlow: StateFlow<Children?>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HistoryViewModel(pottyEventsRepository, activeChildFlow) as T
}
