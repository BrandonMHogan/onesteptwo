package com.onesteptwo.android.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onesteptwo.android.ui.theme.HeatmapHighDark
import com.onesteptwo.android.ui.theme.HeatmapHighLight
import com.onesteptwo.android.ui.theme.HeatmapLowDark
import com.onesteptwo.android.ui.theme.HeatmapLowLight
import com.onesteptwo.android.ui.theme.HeatmapMediumDark
import com.onesteptwo.android.ui.theme.HeatmapMediumLight
import com.onesteptwo.android.ui.theme.Radius
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HeatmapDay(val date: LocalDate, val count: Int)
data class HeatmapWeekRow(val monthLabel: String?, val days: List<HeatmapDay>)

private val WeekdayHeaders = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
private val DateDescriptionFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/** GitHub-contribution-graph-style rolling heatmap (04-UI-SPEC.md Component 4). */
@Composable
fun HeatmapView(weeks: List<HeatmapWeekRow>, onDayClick: (LocalDate) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Row(modifier = Modifier.padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WeekdayHeaders.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        weeks.forEach { week ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(28.dp)) {
                    week.monthLabel?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.days.forEach { day ->
                        HeatmapCell(day = day, isDark = isDark, emptyColor = emptyColor, onClick = onDayClick)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        HeatmapLegend(isDark = isDark, emptyColor = emptyColor)
    }
}

@Composable
private fun HeatmapCell(day: HeatmapDay, isDark: Boolean, emptyColor: Color, onClick: (LocalDate) -> Unit) {
    val color = intensityColor(day.count, isDark, emptyColor)
    val dateLabel = day.date.format(DateDescriptionFormatter)
    val description = if (day.count > 0) "${day.count} events on $dateLabel" else "$dateLabel, no events"
    Surface(
        color = color,
        shape = RoundedCornerShape(Radius.sm),
        modifier = Modifier
            .size(32.dp)
            .semantics {
                contentDescription = description
                if (day.count > 0) role = Role.Button
            }
            .let { m -> if (day.count > 0) m.clickable { onClick(day.date) } else m }
    ) {}
}

private fun intensityColor(count: Int, isDark: Boolean, emptyColor: Color): Color = when {
    count == 0 -> emptyColor
    count <= 2 -> if (isDark) HeatmapLowDark else HeatmapLowLight
    count <= 5 -> if (isDark) HeatmapMediumDark else HeatmapMediumLight
    else -> if (isDark) HeatmapHighDark else HeatmapHighLight
}

@Composable
private fun HeatmapLegend(isDark: Boolean, emptyColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "less", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        listOf(0, 1, 3, 6).forEach { sample ->
            Surface(
                color = intensityColor(sample, isDark, emptyColor),
                shape = RoundedCornerShape(Radius.sm),
                modifier = Modifier.size(16.dp)
            ) {}
        }
        Text(text = "more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
