package com.onesteptwo.android.ui.auth

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Email/password sign-in screen per UI-SPEC Copywriting Contract and Interaction Contracts.
 *
 * - Title: "Sign in" (Display 28sp)
 * - Primary CTA: "Sign in"; disabled while any required field is empty; spinner while in-flight
 * - Secondary link: "Don't have an account? Create one" → [onNavigateToSignUp]
 * - Password field: obscured by default; 48dp show/hide toggle with accessible contentDescription
 * - Email field: KeyboardType.Email, autocapitalization/autocorrect off
 * - IME: email → Next (advances to password); password → Done (submits)
 * - Errors rendered above the CTA in Label (14sp) / colorScheme.error, Polite live region
 * - Screen scrolls above the keyboard (pair with WindowCompat.setDecorFitsSystemWindows(false))
 *
 * Error mapping (UI-SPEC):
 *   wrong credentials  → "Incorrect email or password. Try again."
 *   network failure    → "Couldn't connect. Check your internet connection and try again."
 *   account locked     → "Too many failed attempts. Try again later."
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val passwordFocusRequester = remember { FocusRequester() }

    fun submit() {
        if (isLoading || email.isEmpty() || password.isEmpty()) return
        isLoading = true
        errorMessage = null
        Timber.d("SignIn attempt for: ${email.take(1)}***")
        coroutineScope.launch {
            try {
                when (val result = Clerk.auth.signInWithPassword {
                    identifier = email
                    this.password = password
                }) {
                    is ClerkResult.Success<*> -> {
                        Timber.d("SignIn success")
                        onSignedIn()
                    }
                    else -> {
                        val failure = result as? ClerkResult.Failure<*>
                        val msg = mapSignInError(failure)
                        Timber.w("SignIn failed: $msg")
                        errorMessage = msg
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "SignIn exception")
                errorMessage = "Couldn't connect. Check your internet connection and try again."
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = "Sign in",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { submit() }
            ),
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible)
                            Icons.Filled.VisibilityOff
                        else
                            Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible)
                            "Hide password"
                        else
                            "Show password"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Screen-level error (above CTA) — UI-SPEC error display
        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Primary CTA — disabled while fields empty; spinner while loading
        Button(
            onClick = ::submit,
            enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Sign in")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary link
        TextButton(
            onClick = onNavigateToSignUp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Don't have an account? Create one",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}

/** Maps a Clerk sign-in failure to the exact UI-SPEC error copy. */
private fun mapSignInError(failure: ClerkResult.Failure<*>?): String {
    if (failure == null) return "Incorrect email or password. Try again."
    val errorCode = (failure.error as? ClerkErrorResponse)
        ?.errors?.firstOrNull()?.code

    return when {
        failure.errorType == ClerkResult.Failure.ErrorType.UNKNOWN &&
                failure.throwable != null ->
            "Couldn't connect. Check your internet connection and try again."
        errorCode != null && (errorCode.contains("locked") || errorCode.contains("lockout") ||
                errorCode.contains("too_many")) ->
            "Too many failed attempts. Try again later."
        else ->
            "Incorrect email or password. Try again."
    }
}
