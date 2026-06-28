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
import com.clerk.api.signup.SignUp
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Email/password sign-up screen per UI-SPEC Copywriting Contract and Interaction Contracts.
 *
 * - Title: "Create account" (Display 28sp)
 * - Fields: email, password, confirm password
 * - Primary CTA: "Create account"; disabled while any required field is empty
 * - Secondary link: "Already have an account? Sign in" → [onNavigateToSignIn]
 * - Client-side validation:
 *     weak password  → "Password must be at least 8 characters."  (field-level, below password)
 *     mismatch       → "Passwords don't match."                   (field-level, below confirm field)
 * - Server errors rendered above the CTA (screen-level):
 *     email taken    → "An account with this email already exists. Sign in instead."
 *     network        → "Couldn't create your account. Check your connection and try again."
 * - Password fields: 48dp show/hide toggle with "Show password"/"Hide password" contentDescription
 * - IME: each field "Next" advances; last field "Done" submits
 */
@Composable
fun SignUpScreen(
    onSignedIn: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Field-level errors
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    // Screen-level error (above CTA)
    var screenError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    fun validateAndSubmit() {
        if (isLoading) return

        // Client-side validation (UI-SPEC)
        passwordError = null
        confirmPasswordError = null
        screenError = null

        if (password.length < 8) {
            passwordError = "Password must be at least 8 characters."
            return
        }
        if (password != confirmPassword) {
            confirmPasswordError = "Passwords don't match."
            return
        }
        if (email.isEmpty() || password.isEmpty()) return

        isLoading = true
        val atIdx = email.indexOf('@')
        val maskedEmail = "${email.take(1)}***${if (atIdx >= 0) email.substring(atIdx) else ""}"
        Timber.d("SignUp attempt for: $maskedEmail")
        coroutineScope.launch {
            try {
                when (val result = Clerk.auth.signUp {
                    this.email = email
                    this.password = password
                }) {
                    is ClerkResult.Success<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val signUp = (result as ClerkResult.Success<SignUp>).value
                        Timber.d("SignUp result: status=${signUp.status}, missingFields=${signUp.missingFields}, createdUserId=${signUp.createdUserId}")
                        when (signUp.status) {
                            SignUp.Status.COMPLETE -> onSignedIn()
                            SignUp.Status.MISSING_REQUIREMENTS -> {
                                val missing = signUp.missingFields.joinToString(", ").ifEmpty { "unknown" }
                                screenError = "Account creation needs more steps: $missing"
                                Timber.w("SignUp missing requirements: $missing")
                            }
                            else -> {
                                screenError = "Couldn't create your account. Check your connection and try again."
                                Timber.w("SignUp unexpected status: ${signUp.status}")
                            }
                        }
                    }
                    else -> {
                        val failure = result as? ClerkResult.Failure<*>
                        screenError = mapSignUpError(failure)
                    }
                }
            } catch (e: Exception) {
                screenError = "Couldn't create your account. Check your connection and try again."
                Timber.e(e, "SignUp exception")
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
            text = "Create account",
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
            onValueChange = {
                password = it
                if (passwordError != null) passwordError = null
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { confirmPasswordFocusRequester.requestFocus() }
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
            isError = passwordError != null,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
        )

        // Field-level: weak password error (inline, below field)
        passwordError?.let { msg ->
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

        Spacer(modifier = Modifier.height(8.dp))

        // Confirm password field
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                if (confirmPasswordError != null) confirmPasswordError = null
            },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { validateAndSubmit() }
            ),
            trailingIcon = {
                IconButton(
                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (confirmPasswordVisible)
                            Icons.Filled.VisibilityOff
                        else
                            Icons.Filled.Visibility,
                        contentDescription = if (confirmPasswordVisible)
                            "Hide password"
                        else
                            "Show password"
                    )
                }
            },
            isError = confirmPasswordError != null,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(confirmPasswordFocusRequester)
        )

        // Field-level: password mismatch error (inline, below confirm field)
        confirmPasswordError?.let { msg ->
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

        // Screen-level error (above CTA) — network / email-taken
        screenError?.let { msg ->
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

        // Primary CTA
        val allFieldsFilled = email.isNotEmpty() && password.isNotEmpty() &&
                confirmPassword.isNotEmpty()
        Button(
            onClick = ::validateAndSubmit,
            enabled = allFieldsFilled && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Create account")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary link
        TextButton(
            onClick = onNavigateToSignIn,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Already have an account? Sign in",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}

/** Maps a Clerk sign-up failure to the exact UI-SPEC error copy. */
private fun mapSignUpError(failure: ClerkResult.Failure<*>?): String {
    if (failure == null) {
        return "Couldn't create your account. Check your connection and try again."
    }
    val errorCode = (failure.error as? ClerkErrorResponse)
        ?.errors?.firstOrNull()?.code

    return when {
        failure.errorType == ClerkResult.Failure.ErrorType.UNKNOWN &&
                failure.throwable != null ->
            "Couldn't create your account. Check your connection and try again."
        errorCode != null && (errorCode.contains("exists") || errorCode.contains("taken") ||
                errorCode.contains("duplicate") || errorCode.contains("form_identifier_exists")) ->
            "An account with this email already exists. Sign in instead."
        else ->
            "Couldn't create your account. Check your connection and try again."
    }
}
