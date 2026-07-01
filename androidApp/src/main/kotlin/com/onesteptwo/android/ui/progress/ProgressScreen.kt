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
import com.onesteptwo.android.ui.childswitcher.ChildSwitcherPagerHost
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import com.onesteptwo.db.Children

/**
 * Phase 5 only needs the persistent Child Switcher Banner + swipe pager here (REQ-031, revised
 * D-12) — full streak/milestone UI is Phase 7 (REQ-034). Each page is bound to one specific child
 * ([ProgressContent]) rather than reading a shared "active child," consistent with Home/History.
 */
@Composable
fun ProgressScreen(childSelectionViewModel: ChildSelectionViewModel) {
    val children by childSelectionViewModel.children.collectAsState()
    val activeChild by childSelectionViewModel.activeChild.collectAsState()

    ChildSwitcherPagerHost(
        children = children,
        activeChild = activeChild,
        onSelectChild = childSelectionViewModel::selectChild
    ) { child ->
        ProgressContent(child = child)
    }
}

@Composable
private fun ProgressContent(child: Children) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Progress", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Streaks and milestones for ${child.nickname} are coming soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
