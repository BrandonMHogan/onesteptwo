package com.onesteptwo.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onesteptwo.android.ui.theme.Radius
import com.onesteptwo.android.ui.theme.ToastBackground
import com.onesteptwo.android.ui.theme.ToastOnBackground

/** Locked 5-value event_type set (05-CONTEXT.md D-04) — not the 6-value split in the imported design mockup. */
val EventTypeChips = listOf("Pee", "Poo", "Both", "Accident", "Tried")

/** Post-log toast (04-UI-SPEC.md Component 6 / docs/SCREEN-FLOWS.md flow step 4-6). */
@Composable
fun LogToast(
    visible: Boolean,
    onChipTap: (String) -> Unit,
    onAddDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(animationSpec = tween(150)) { it } + fadeOut(tween(150))
    ) {
        Surface(
            color = ToastBackground,
            shape = RoundedCornerShape(Radius.lg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Logged. Add a type?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ToastOnBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(EventTypeChips) { label ->
                        ToastChip(label = label, onClick = { onChipTap(label) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAddDetails, modifier = Modifier.wrapContentWidth(Alignment.Start)) {
                    Text(
                        text = "add details",
                        style = MaterialTheme.typography.labelLarge,
                        color = ToastOnBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastChip(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}
