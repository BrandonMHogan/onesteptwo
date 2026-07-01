package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clerk.api.organizations.OrganizationMembership
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.common.DestructiveConfirmDialog
import com.onesteptwo.db.Children

/**
 * Settings tab shell (04-UI-SPEC.md §Main App — Settings Tab). Sections build up across
 * 05-02-PLAN.md tasks: Family (Task 7, this file), Children (Task 8), Notifications + Account
 * (Task 9). Role gate is structural — a caregiver never composes the Family or Children sections
 * at all (WIREFRAMES.md: "removed from view tree, not visibility:hidden").
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigateToInvite: () -> Unit,
    onNavigateToAddChild: () -> Unit,
    onNavigateToEditChild: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container.childrenRepository))
    val state by viewModel.state.collectAsState()
    val children by viewModel.children.collectAsState()
    var pendingRemoveCaregiver by remember { mutableStateOf<OrganizationMembership?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        if (state.isAdmin) {
            SectionLabel("Family")
            state.familyMembers.forEach { member ->
                FamilyMemberRow(member = member, onRemove = { pendingRemoveCaregiver = member })
            }
            SettingsRow(text = "Invite caregiver", onClick = onNavigateToInvite)

            // Inline error — bodyMedium, colorScheme.error, Polite live region — same treatment
            // as InviteCaregiverScreen's inline error (family list load / caregiver removal
            // failures both surface here since neither has its own dedicated screen).
            state.familyError?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel("Children")
            children.forEach { child ->
                SettingsRow(
                    text = "${child.nickname}  (${childBirthLabel(child)})",
                    onClick = { onNavigateToEditChild(child.id) }
                )
            }
            SettingsRow(text = "Add child", onClick = onNavigateToAddChild)
            Spacer(modifier = Modifier.height(24.dp))
        }

        SectionLabel("Notifications")
        Text(
            text = "Coming soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Account")
        Text(text = state.userEmail, style = MaterialTheme.typography.bodyLarge)
        SettingsRow(text = "Sign out", onClick = onSignOut)
        Spacer(modifier = Modifier.height(24.dp))
    }

    pendingRemoveCaregiver?.let { member ->
        val name = memberDisplayName(member)
        DestructiveConfirmDialog(
            title = "Remove $name?",
            body = "They will lose access to your family immediately.",
            confirmLabel = "Remove",
            dismissLabel = "Keep $name",
            onConfirm = {
                viewModel.removeCaregiver(member.publicUserData?.userId ?: "")
                pendingRemoveCaregiver = null
            },
            onDismiss = { pendingRemoveCaregiver = null }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(text: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun FamilyMemberRow(member: OrganizationMembership, onRemove: () -> Unit) {
    val name = memberDisplayName(member)
    val roleLabel = if (member.role == "org:admin") "Admin" else "Caregiver"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$name  ·  $roleLabel", style = MaterialTheme.typography.bodyLarge)
        // D-11: v1 has exactly one admin — never render remove on the admin's own row.
        if (member.role != "org:admin") {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove $name, destructive")
            }
        }
    }
}

private fun memberDisplayName(member: OrganizationMembership): String {
    val u = member.publicUserData ?: return "Member"
    return "${u.firstName.orEmpty()} ${u.lastName.orEmpty()}".trim().ifBlank { u.identifier }
}

private fun childBirthLabel(child: Children): String {
    val monthName = java.time.Month.of(child.birth_month.toInt())
        .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
    return "$monthName ${child.birth_year}"
}
