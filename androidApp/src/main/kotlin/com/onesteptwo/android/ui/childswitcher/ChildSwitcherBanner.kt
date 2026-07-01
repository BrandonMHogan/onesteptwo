package com.onesteptwo.android.ui.childswitcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onesteptwo.db.Children

/**
 * Persistent Child Switcher Banner (04-UI-SPEC.md §Component 9, revised D-12) shared by Home,
 * History, and Progress tab roots. Centered name + age; page dots and tap affordance only render
 * for multi-child families ([isInteractive]) — single-child families get name + age only, per
 * REQ-031's "shows no [interactive] switcher" for one child.
 */
@Composable
fun ChildSwitcherBanner(
    activeChild: Children?,
    isInteractive: Boolean,
    dotCount: Int,
    activeDotIndex: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = activeChild?.nickname ?: ""
    val age = activeChild?.let { formatChildAge(it.birth_month.toInt(), it.birth_year.toInt()) } ?: ""
    val description = if (isInteractive) {
        "$name, active child. Double tap to open child list, swipe left or right to switch."
    } else {
        "$name, active child"
    }

    val interactionModifier = if (isInteractive) {
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onTap)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            }
    } else {
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(interactionModifier)
    ) {
        Text(text = name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = age,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (isInteractive && dotCount > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(dotCount) { i ->
                    val dotColor = if (i == activeDotIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(color = dotColor, shape = CircleShape)
                    )
                }
            }
        }
    }
}
