package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.common.DestructiveConfirmDialog
import com.onesteptwo.android.ui.onboarding.MonthYearDropdownContent
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.NotificationPreferencesRepository
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Year
import java.time.format.TextStyle
import java.util.Locale

data class EditChildUiState(
    val nickname: String = "",
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

class EditChildViewModel(
    private val child: Children,
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val pottyEventsRepository: PottyEventsRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditChildUiState(
            nickname = child.nickname,
            birthMonth = child.birth_month.toInt(),
            birthYear = child.birth_year.toInt()
        )
    )
    val state: StateFlow<EditChildUiState> = _state.asStateFlow()

    fun updateNickname(value: String) = _state.update { it.copy(nickname = value, errorMessage = null) }
    fun updateBirthMonth(month: Int) = _state.update { it.copy(birthMonth = month, errorMessage = null) }
    fun updateBirthYear(year: Int) = _state.update { it.copy(birthYear = year, errorMessage = null) }

    fun save() {
        val s = _state.value
        val birthMonth = s.birthMonth ?: return
        val birthYear = s.birthYear ?: return
        if (s.nickname.isBlank() || s.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = childrenApiClient.patchChild(
                    id = child.id,
                    nickname = s.nickname.trim(),
                    birthMonth = birthMonth,
                    birthYear = birthYear
                )
            ) {
                is ApiResult.Success -> {
                    val updated = result.value
                    childrenRepository.update(
                        id = updated.id,
                        nickname = updated.nickname,
                        birthMonth = updated.birth_month.toLong(),
                        birthYear = updated.birth_year.toLong(),
                        updatedAt = Instant.now().toString()
                    )
                    _state.update { it.copy(isSubmitting = false, done = true) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
            }
        }
    }

    /** "Remove child" destructive row — delete lives inside the edit screen (05-CONTEXT.md D-06 pattern, applied to children). */
    fun removeChild() {
        viewModelScope.launch {
            when (childrenApiClient.deleteChild(child.id)) {
                is ApiResult.Success -> {
                    childrenRepository.deleteById(child.id)
                    pottyEventsRepository.deleteAllForChild(child.id)
                    notificationPreferencesRepository.deleteLocal(child.id)
                    _state.update { it.copy(done = true) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(errorMessage = "Couldn't remove child. Try again.") }
                }
            }
        }
    }
}

class EditChildViewModelFactory(
    private val child: Children,
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val pottyEventsRepository: PottyEventsRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditChildViewModel(child, childrenApiClient, childrenRepository, pottyEventsRepository, notificationPreferencesRepository) as T
}

private val MonthNames = (1..12).map { month ->
    java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChildScreen(container: AppContainer, child: Children, onDone: () -> Unit) {
    val viewModel: EditChildViewModel = viewModel(
        factory = EditChildViewModelFactory(
            child,
            container.childrenApiClient,
            container.childrenRepository,
            container.pottyEventsRepository,
            container.notificationPreferencesRepository
        )
    )
    val state by viewModel.state.collectAsState()
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    val currentYear = Year.now().value
    val years = (currentYear downTo currentYear - 10).toList()

    if (state.done) {
        onDone()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Edit ${child.nickname}", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        androidx.compose.material3.OutlinedTextField(
            value = state.nickname,
            onValueChange = viewModel::updateNickname,
            label = { Text("Nickname") },
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
                    value = state.birthMonth?.let { MonthNames[it - 1] } ?: "",
                    label = "Birth month",
                    expanded = monthExpanded
                )
                ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                    MonthNames.forEachIndexed { index, name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            viewModel.updateBirthMonth(index + 1)
                            monthExpanded = false
                        })
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = yearExpanded,
                onExpandedChange = { yearExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                MonthYearDropdownContent(
                    value = state.birthYear?.toString() ?: "",
                    label = "Birth year",
                    expanded = yearExpanded
                )
                ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                    years.forEach { year ->
                        DropdownMenuItem(text = { Text(year.toString()) }, onClick = {
                            viewModel.updateBirthYear(year)
                            yearExpanded = false
                        })
                    }
                }
            }
        }

        state.errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = viewModel::save, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Save changes")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { showRemoveConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Remove child", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showRemoveConfirm) {
        DestructiveConfirmDialog(
            title = "Remove ${child.nickname}?",
            body = "This permanently erases all events and data for ${child.nickname}. This cannot be undone.",
            confirmLabel = "Remove ${child.nickname}",
            dismissLabel = "Keep ${child.nickname}",
            onConfirm = {
                showRemoveConfirm = false
                viewModel.removeChild()
            },
            onDismiss = { showRemoveConfirm = false }
        )
    }
}
