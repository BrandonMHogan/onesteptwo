package com.onesteptwo.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.common.DestructiveConfirmDialog
import com.onesteptwo.android.ui.home.EventDetailSheet
import com.onesteptwo.android.ui.theme.Radius
import com.onesteptwo.db.Potty_events
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** History Day-Detail (04-UI-SPEC.md §Main App — History Day-Detail View): every card tappable
 * (05-CONTEXT.md D-06), tab bar hidden by the caller (MainTabNavigation). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    date: LocalDate,
    childId: String,
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: DayDetailViewModel = viewModel(
        factory = DayDetailViewModelFactory(container.pottyEventsRepository, date, childId)
    )
    val events by viewModel.events.collectAsState()
    val selectedEvent by viewModel.selectedEvent.collectAsState()
    var pendingDelete by remember { mutableStateOf<Potty_events?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Go back to History" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events, key = { it.id }) { event ->
                EventCard(event = event, onClick = { viewModel.openEventDetail(event) })
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            onSave = { type, notes -> viewModel.saveEventDetails(type, notes, event.occurred_at) },
            onDelete = {
                pendingDelete = event
                viewModel.dismissEventDetail()
            },
            onDismiss = viewModel::dismissEventDetail
        )
    }

    pendingDelete?.let { event ->
        DestructiveConfirmDialog(
            title = "Remove event?",
            body = "This permanently deletes this event. This cannot be undone.",
            confirmLabel = "Remove event",
            dismissLabel = "Keep event",
            onConfirm = {
                viewModel.deleteEvent(event.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun EventCard(event: Potty_events, onClick: () -> Unit) {
    val time = Instant.parse(event.occurred_at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val type = event.event_type
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.md),
        tonalElevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (type == null) "$time event — tap to add details" else "$time $type"
                role = Role.Button
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type == null) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Text("Needs details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                } else {
                    Icon(eventTypeIcon(type), contentDescription = null)
                    Text(type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            event.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun eventTypeIcon(type: String): ImageVector = when (type) {
    "pee" -> Icons.Outlined.WaterDrop
    "poo" -> Icons.Outlined.Circle
    "both" -> Icons.Outlined.WaterDrop
    "accident" -> Icons.Outlined.Warning
    "tried" -> Icons.Outlined.RadioButtonUnchecked
    else -> Icons.Outlined.Circle
}
