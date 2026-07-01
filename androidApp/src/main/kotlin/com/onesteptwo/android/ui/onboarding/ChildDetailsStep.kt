package com.onesteptwo.android.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Year
import java.time.format.TextStyle
import java.util.Locale

private val MonthNames = (1..12).map { month ->
    java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailsStep(
    nickname: String,
    birthMonth: Int?,
    birthYear: Int?,
    errorMessage: String?,
    onNicknameChange: (String) -> Unit,
    onBirthMonthChange: (Int) -> Unit,
    onBirthYearChange: (Int) -> Unit,
    onContinue: () -> Unit,
    showStepDots: Boolean = true
) {
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    val currentYear = Year.now().value
    val years = (currentYear downTo currentYear - 10).toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (showStepDots) {
            StepDots(activeIndex = 3)
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(text = "Your child", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text("Nickname") },
            placeholder = { Text("Alex") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExposedDropdownMenuBox(
                expanded = monthExpanded,
                onExpandedChange = { monthExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                MonthYearDropdownContent(
                    value = birthMonth?.let { MonthNames[it - 1] } ?: "",
                    label = "Birth month",
                    expanded = monthExpanded
                )
                ExposedDropdownMenu(
                    expanded = monthExpanded,
                    onDismissRequest = { monthExpanded = false }
                ) {
                    MonthNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onBirthMonthChange(index + 1)
                                monthExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = yearExpanded,
                onExpandedChange = { yearExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                MonthYearDropdownContent(
                    value = birthYear?.toString() ?: "",
                    label = "Birth year",
                    expanded = yearExpanded
                )
                ExposedDropdownMenu(
                    expanded = yearExpanded,
                    onDismissRequest = { yearExpanded = false }
                ) {
                    years.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                onBirthYearChange(year)
                                yearExpanded = false
                            }
                        )
                    }
                }
            }
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onContinue,
            enabled = nickname.isNotBlank() && birthMonth != null && birthYear != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue setup")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExposedDropdownMenuBoxScope.MonthYearDropdownContent(value: String, label: String, expanded: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = Modifier
            .menuAnchor()
            .fillMaxWidth()
    )
}
