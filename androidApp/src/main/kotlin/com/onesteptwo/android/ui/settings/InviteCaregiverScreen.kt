package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.organizations.createInvitation
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Admin caregiver invitation screen per UI-SPEC Invite Caregiver Screen specification (REQ-017).
 *
 * Only reachable from the admin [onInvite] action — role gate is enforced at both the
 * [PostAuthStub] (button visibility) and the AppNavigation `invite` route (REQ-016 / T-3-05).
 *
 * - Title: "Invite a caregiver" (Display 28sp / headlineMedium)
 * - Primary CTA: "Send invitation" — disabled while email is empty or request is in-flight
 * - Sending state: [CircularProgressIndicator] inside the button (same dimensions)
 * - Success: Material3 Snackbar "Invitation sent" (auto-dismiss ~4s) + inline success body
 * - Errors rendered below the email field in Label (14sp) / colorScheme.error, Polite live region
 *
 * Calls [Clerk.organization.createInvitation] with role exactly `"org:caregiver"` (CRITICAL:
 * role string must match the pre-created Clerk Dashboard role; org: prefix mandatory — see
 * RESEARCH.md Pitfall 5 and PROJECT.md constraint).
 */
@Composable
fun InviteCaregiverScreen(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Stores the email that was successfully invited; drives the inline success body.
    var successEmail by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun sendInvitation() {
        if (isSending || email.isEmpty()) return

        // Client-side email format validation (UI-SPEC: "Enter a valid email address.").
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            errorMessage = "Enter a valid email address."
            return
        }

        val trimmedEmail = email.trim()
        isSending = true
        errorMessage = null
        successEmail = null

        coroutineScope.launch {
            try {
                val org = Clerk.organization
                if (org == null) {
                    Timber.w("InviteCaregiverScreen: Clerk.organization is null — no active org")
                    errorMessage = "Couldn't send the invitation. Try again."
                    return@launch
                }

                Timber.d("InviteCaregiverScreen: sending invitation to ${trimmedEmail.take(1)}***")

                when (val result = org.createInvitation(
                    emailAddress = trimmedEmail,
                    role = "org:caregiver" // CRITICAL: must match pre-created Clerk Dashboard role
                )) {
                    is ClerkResult.Success<*> -> {
                        Timber.d("InviteCaregiverScreen: invitation sent to $trimmedEmail")
                        successEmail = trimmedEmail
                        email = ""
                        // Snackbar auto-dismisses after ~4 s (SnackbarDuration.Short)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Invitation sent",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                    else -> {
                        val failure = result as? ClerkResult.Failure<*>
                        errorMessage = mapInviteError(failure)
                        Timber.w("InviteCaregiverScreen: invitation failed — $errorMessage")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "InviteCaregiverScreen: exception sending invitation")
                errorMessage = "Couldn't send the invitation. Try again."
            } finally {
                isSending = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // Screen title — Display 28sp per UI-SPEC
            Text(
                text = "Invite a caregiver",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email field — KeyboardType.Email, no autocap/autocorrect (UI-SPEC Interaction)
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    // Reset error and success on edit so state stays consistent.
                    if (errorMessage != null) errorMessage = null
                    if (successEmail != null) successEmail = null
                },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Inline error — Label (14sp), colorScheme.error, Polite live region (UI-SPEC)
            errorMessage?.let { msg ->
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

            Spacer(modifier = Modifier.height(16.dp))

            // Primary CTA — disabled while email is empty or request in-flight
            Button(
                onClick = ::sendInvitation,
                enabled = email.isNotEmpty() && !isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Send invitation")
                }
            }

            // Inline success body shown after a successful invite send (UI-SPEC)
            successEmail?.let { sentEmail ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "We sent an invitation to $sentEmail. " +
                        "They'll receive a link to join your family.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

/**
 * Maps a Clerk invitation failure to exact UI-SPEC error copy.
 *
 * Error mapping:
 *  - UNKNOWN error type with throwable → network failure → "Couldn't send the invitation. Try again."
 *  - Clerk error code containing "email" or "format" → "Enter a valid email address."
 *  - Clerk error code containing "duplicate" or "already" → "This person is already in your family."
 *  - Default → "Couldn't send the invitation. Try again."
 */
private fun mapInviteError(failure: ClerkResult.Failure<*>?): String {
    if (failure == null) return "Couldn't send the invitation. Try again."
    if (failure.errorType == ClerkResult.Failure.ErrorType.UNKNOWN && failure.throwable != null) {
        return "Couldn't send the invitation. Try again."
    }
    val code = (failure.error as? ClerkErrorResponse)?.errors?.firstOrNull()?.code ?: ""
    return when {
        code.contains("email", ignoreCase = true) || code.contains("format", ignoreCase = true) ->
            "Enter a valid email address."
        code.contains("duplicate", ignoreCase = true) || code.contains("already", ignoreCase = true) ->
            "This person is already in your family."
        else -> "Couldn't send the invitation. Try again."
    }
}
