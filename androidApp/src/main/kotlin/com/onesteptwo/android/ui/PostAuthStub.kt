package com.onesteptwo.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk

/**
 * Minimal post-authentication placeholder screen (full 4-tab shell is Phase 5).
 *
 * Role gate (UI-SPEC Screen Inventory):
 *  - org:admin  → "Invite" action is exposed, wired to [onInvite] callback
 *  - org:caregiver → "Invite" action is hidden
 *
 * The active organisation membership role is read from [Clerk.organizationMembership.role]
 * which reflects the role in the currently active Clerk organisation. This is set after
 * Clerk.auth.setActive() is called in AppNavigation.navigateAfterAuth().
 */
@Composable
fun PostAuthStub(onInvite: () -> Unit) {
    // Read the active org membership role from Clerk.
    // Clerk.organizationMembership returns the membership for the currently active org session.
    val isAdmin by remember {
        mutableStateOf(Clerk.organizationMembership?.role == "org:admin")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "You're signed in!",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The full app shell is coming in Phase 5.",
            style = MaterialTheme.typography.bodyLarge
        )

        if (isAdmin) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onInvite) {
                Text("Invite")
            }
        }
    }
}
