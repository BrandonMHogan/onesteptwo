package com.onesteptwo.android.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import com.clerk.api.network.ClerkPaginatedResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.organizations.OrganizationMembership
import com.clerk.api.user.getOrganizationMemberships
import kotlinx.coroutines.launch
import timber.log.Timber

private sealed interface OrgLoadState {
    object Loading : OrgLoadState
    data class Loaded(val memberships: List<OrganizationMembership>) : OrgLoadState
    object Empty : OrgLoadState
    object Error : OrgLoadState
}

/**
 * Multi-org picker screen per UI-SPEC Org Picker Screen specification (REQ-018).
 *
 * Shown after sign-in when the user belongs to 2+ Clerk organisations.
 *
 * - Title: "Choose a family" (Display 28sp / headlineMedium)
 * - Primary CTA: "Continue" — enabled after an org is selected; spinner while activating
 * - Org rows: family name in Heading 22sp / headlineSmall; single focusable row per UI-SPEC
 * - Loading: full-screen centered spinner (no skeleton rows per UI-SPEC)
 * - Empty: "No families yet" heading + invite-prompt body copy
 * - Error: exact error copy from UI-SPEC with a retry action
 *
 * On Continue: calls [Clerk.auth.setActive] positionally (sessionId, orgId) — named
 * params are stripped by R8 obfuscation (see 03-03 deviation #4) — then invokes [onOrgActivated].
 */
@Composable
fun OrgPickerScreen(onOrgActivated: () -> Unit) {
    var loadState by remember { mutableStateOf<OrgLoadState>(OrgLoadState.Loading) }
    var selectedOrgId by remember { mutableStateOf<String?>(null) }
    var isActivating by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

    // Reload whenever refreshTrigger changes (initial load + retry on error).
    LaunchedEffect(refreshTrigger) {
        loadState = OrgLoadState.Loading
        selectedOrgId = null
        val user = Clerk.user
        if (user == null) {
            Timber.w("OrgPickerScreen: Clerk.user is null after sign-in — loading error")
            loadState = OrgLoadState.Error
            return@LaunchedEffect
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val memberships: List<OrganizationMembership> =
                when (val result = user.getOrganizationMemberships()) {
                    is ClerkResult.Success<*> ->
                        (result.value as? ClerkPaginatedResponse<OrganizationMembership>)?.data
                            ?: emptyList()
                    else -> {
                        Timber.w("OrgPickerScreen: getOrganizationMemberships failed")
                        loadState = OrgLoadState.Error
                        return@LaunchedEffect
                    }
                }
            Timber.d("OrgPickerScreen: loaded ${memberships.size} memberships")
            loadState = if (memberships.isEmpty()) OrgLoadState.Empty
                        else OrgLoadState.Loaded(memberships)
        } catch (e: Exception) {
            Timber.e(e, "OrgPickerScreen: exception loading memberships")
            loadState = OrgLoadState.Error
        }
    }

    when (val state = loadState) {
        // ── Loading ───────────────────────────────────────────────────────────
        is OrgLoadState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // ── Error ─────────────────────────────────────────────────────────────
        is OrgLoadState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Couldn't load your families. Pull down to try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { refreshTrigger++ }) {
                        Text("Try again")
                    }
                }
            }
        }

        // ── Empty ─────────────────────────────────────────────────────────────
        is OrgLoadState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "No families yet",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You haven't been added to a family. " +
                            "Ask your family admin to send you an invitation.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // ── Loaded ────────────────────────────────────────────────────────────
        is OrgLoadState.Loaded -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(64.dp))

                // Screen title — Display 28sp per UI-SPEC
                Text(
                    text = "Choose a family",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Org list — each row is a single focusable element (UI-SPEC Accessibility)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.memberships.forEach { membership ->
                        val orgId = membership.organization.id
                        val orgName = membership.organization.name
                        val isSelected = selectedOrgId == orgId

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOrgId = orgId }
                                .padding(vertical = 12.dp)
                                .semantics { contentDescription = orgName }
                        ) {
                            // Heading 22sp per UI-SPEC Typography; primary color when selected
                            Text(
                                text = orgName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary CTA — disabled until a selection is made; spinner while activating
                Button(
                    onClick = {
                        val orgId = selectedOrgId ?: return@Button
                        isActivating = true
                        coroutineScope.launch {
                            try {
                                // Positional call required — R8 strips named param metadata.
                                // Order: (sessionId, organizationId) per 03-03 deviation #4.
                                val sessionId = Clerk.session?.id ?: ""
                                Clerk.auth.setActive(sessionId, orgId)
                                Timber.d("OrgPickerScreen: activated org=$orgId")
                                onOrgActivated()
                            } catch (e: Exception) {
                                Timber.e(e, "OrgPickerScreen: setActive failed")
                                // Surface error so user can retry rather than silently failing.
                                loadState = OrgLoadState.Error
                            } finally {
                                isActivating = false
                            }
                        }
                    },
                    enabled = selectedOrgId != null && !isActivating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isActivating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Continue")
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}
