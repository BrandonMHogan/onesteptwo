package com.onesteptwo.android.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel

/**
 * Phase 5 only needs the child switcher to update active-child context here (REQ-031) — full
 * streak/milestone UI is Phase 7 (REQ-034). Participates in [ChildSelectionViewModel] so the
 * active-child label stays in sync with Home/History even though there's nothing else to show yet.
 */
@Composable
fun ProgressScreen(childSelectionViewModel: ChildSelectionViewModel) {
    val activeChild by childSelectionViewModel.activeChild.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Progress", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        val childName = activeChild?.nickname
        Text(
            text = if (childName != null) "Streaks and milestones for $childName are coming soon."
            else "Streaks and milestones are coming soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
