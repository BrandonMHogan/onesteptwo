package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk

/**
 * Stage 1 placeholder — child management, notification preferences, and account erasure land in
 * 05-02-PLAN.md (Stage 2). Reuses the existing Phase 3 [com.onesteptwo.android.ui.settings.InviteCaregiverScreen]
 * via [onNavigateToInvite] rather than rebuilding it (REQ-017 already implemented).
 */
@Composable
fun SettingsScreen(onNavigateToInvite: () -> Unit, onSignOut: () -> Unit) {
    val isAdmin by remember {
        derivedStateOf { Clerk.organizationMembership?.role == "org:admin" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        if (isAdmin) {
            Text(
                text = "Invite a caregiver",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToInvite)
                    .padding(vertical = 12.dp)
            )
        }

        Text(
            text = "Family, children, notifications and account management are coming soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Sign out",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSignOut)
                .padding(vertical = 12.dp)
        )
    }
}
