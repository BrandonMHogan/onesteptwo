package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.NotificationPreferencesApiClient
import com.onesteptwo.data.NotificationPreferencesRepository
import kotlinx.coroutines.launch

/** REQ-023 opt-in default: [NotificationPreferencesRepository.observe] defaults every unseen
 * child to enabled=true. Toggling writes optimistically to the local cache, fires the real PUT
 * (05-CONTEXT.md D-02 — Settings hits the API directly), and reverts on failure. */
@Composable
fun NotificationPreferenceRow(
    childId: String,
    childNickname: String,
    repository: NotificationPreferencesRepository,
    apiClient: NotificationPreferencesApiClient
) {
    val enabled by repository.observe(childId).collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Notify me for $childNickname",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = { newValue ->
                scope.launch {
                    repository.setLocal(childId, newValue)
                    val result = apiClient.putPreference(childId, newValue)
                    if (result is ApiResult.Failure) {
                        repository.setLocal(childId, !newValue)
                    }
                }
            },
            modifier = Modifier.semantics {
                contentDescription = "Notify me for $childNickname, ${if (enabled) "on" else "off"}"
            }
        )
    }
}
