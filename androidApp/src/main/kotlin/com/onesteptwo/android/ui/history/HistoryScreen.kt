package com.onesteptwo.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.childswitcher.ChildSwitcherPagerHost
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** History tab (04-UI-SPEC.md §Main App — History Tab): persistent Child Switcher Banner (D-12)
 * above the rolling heatmap, or empty state if that page's child has never logged an event. */
@Composable
fun HistoryScreen(
    container: AppContainer,
    childSelectionViewModel: ChildSelectionViewModel,
    onDayClick: (LocalDate) -> Unit
) {
    val children by childSelectionViewModel.children.collectAsState()
    val activeChild by childSelectionViewModel.activeChild.collectAsState()

    ChildSwitcherPagerHost(
        children = children,
        activeChild = activeChild,
        onSelectChild = childSelectionViewModel::selectChild
    ) { child ->
        HistoryContent(container = container, child = child, onDayClick = onDayClick)
    }
}

@Composable
private fun HistoryContent(container: AppContainer, child: Children, onDayClick: (LocalDate) -> Unit) {
    val activeChildFlow = remember(child.id) { MutableStateFlow<Children?>(child) }
    val historyViewModel: HistoryViewModel = viewModel(
        key = child.id,
        factory = HistoryViewModelFactory(container.pottyEventsRepository, activeChildFlow)
    )
    val state by historyViewModel.state.collectAsState()

    if (!state.hasEverLogged) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "No events yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log your first potty trip to see it here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HeatmapView(weeks = state.weeks, onDayClick = onDayClick)
        }
    }
}
