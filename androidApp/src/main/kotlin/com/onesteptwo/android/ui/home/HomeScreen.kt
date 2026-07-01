package com.onesteptwo.android.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.childswitcher.ChildSwitcherPagerHost
import com.onesteptwo.android.ui.theme.Radius
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow

/** Home tab (04-UI-SPEC.md Screen Inventory): persistent Child Switcher Banner (D-12) -> per-child
 * content (count/status/Log button, M6). */
@Composable
fun HomeScreen(container: AppContainer, childSelectionViewModel: ChildSelectionViewModel) {
    val children by childSelectionViewModel.children.collectAsState()
    val activeChild by childSelectionViewModel.activeChild.collectAsState()

    ChildSwitcherPagerHost(
        children = children,
        activeChild = activeChild,
        onSelectChild = childSelectionViewModel::selectChild
    ) { child ->
        HomeContent(container = container, child = child)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(container: AppContainer, child: Children) {
    val activeChildFlow = remember(child.id) { MutableStateFlow<Children?>(child) }
    val homeViewModel: HomeViewModel = viewModel(
        key = child.id,
        factory = HomeViewModelFactory(container.pottyEventsRepository, activeChildFlow)
    )
    val state by homeViewModel.state.collectAsState()
    val selectedEvent by homeViewModel.selectedEventForSheet.collectAsState()
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Event count / empty state.
            if (state.todayEventCount == 0) {
                Text(text = "No events yet", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Log your first potty trip to see it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                Text(text = "${state.todayEventCount}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "events today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status chips — hidden entirely when both counts are zero.
            if (state.pendingDetailsCount > 0 || state.pendingSyncCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.pendingDetailsCount > 0) {
                        StatusChip(
                            text = "${state.pendingDetailsCount} need details",
                            contentDescription = "${state.pendingDetailsCount} events need details",
                            onClick = homeViewModel::openFirstPendingDetailsEvent
                        )
                    }
                    if (state.pendingSyncCount > 0) {
                        StatusChip(
                            text = "${state.pendingSyncCount} syncing…",
                            contentDescription = "${state.pendingSyncCount} events pending sync",
                            onClick = {}
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Log button — wide pill, spring press scale, haptic on release.
            val scale by animateFloatAsState(
                targetValue = if (state.pressed) 0.95f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.7f),
                label = "logButtonScale"
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(52.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .semantics {
                            contentDescription = "Log potty trip"
                            role = Role.Button
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    homeViewModel.onPress(true)
                                    val released = tryAwaitRelease()
                                    homeViewModel.onPress(false)
                                    if (released) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        homeViewModel.logEvent()
                                    }
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Log",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Post-log toast — overlays above the Log button.
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 84.dp), contentAlignment = Alignment.BottomCenter) {
            LogToast(
                visible = state.toastVisible,
                onChipTap = { homeViewModel.pickEventType(it.lowercase()) },
                onAddDetails = homeViewModel::openAddDetails,
                onDismiss = homeViewModel::dismissToast
            )
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            onSave = { type, notes -> homeViewModel.saveEventDetails(type, notes, event.occurred_at) },
            onDismiss = homeViewModel::dismissEventDetail
        )
    }
}

@Composable
private fun StatusChip(text: String, contentDescription: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
        modifier = Modifier
            .height(28.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}
