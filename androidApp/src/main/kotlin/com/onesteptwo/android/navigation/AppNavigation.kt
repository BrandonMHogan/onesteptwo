package com.onesteptwo.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clerk.api.Clerk
import com.clerk.api.auth.AuthEvent
import com.clerk.api.network.ClerkPaginatedResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.organizations.OrganizationMembership
import com.clerk.api.session.Session
import com.clerk.api.user.getOrganizationMemberships
import com.onesteptwo.android.ClerkApp
import com.onesteptwo.android.ui.auth.OrgPickerScreen
import com.onesteptwo.android.ui.auth.SignInScreen
import com.onesteptwo.android.ui.auth.SignUpScreen
import com.onesteptwo.android.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Root navigation graph for the app. Auth-gates all routes:
 *
 *   No active session   → "signin" (start)
 *   After sign-in/up:
 *     0 orgs            → "postauth" → no active org → onboarding wizard
 *     1 org             → activate org → "postauth" → has-children? main shell : onboarding
 *     2+ orgs           → "orgpicker" → activates one → "postauth" (same decision as above)
 *
 * Auth screens are popped from the back stack once the user is signed in and an
 * organisation is active, preventing navigation back to Sign In after authentication
 * (UI-SPEC Navigation Flow).
 */
@Composable
fun AppNavigation() {
    // Clerk.isInitialized is a StateFlow<Boolean> that emits true once the SDK has finished
    // loading the persisted client+session from disk. Reading Clerk.isSignedIn before this
    // point always returns false — causing the race condition on cold restart.
    var isInitialized by remember { mutableStateOf(Clerk.isInitialized.value) }
    var startupComplete by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf("signin") }

    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val container = (LocalContext.current.applicationContext as ClerkApp).container

    // Keep isInitialized in sync with the SDK's async init signal.
    LaunchedEffect(Unit) {
        Clerk.isInitialized.collect { isInitialized = it }
    }

    // Once the SDK is ready: detect and clear stale pending sessions, then lock in startDestination.
    LaunchedEffect(isInitialized) {
        if (!isInitialized) return@LaunchedEffect

        val currentSession = Clerk.session
        Timber.d(
            "AppNavigation: SDK initialized — sessionId=${currentSession?.id}, " +
                "status=${currentSession?.status}, isSignedIn=${Clerk.isSignedIn}, " +
                "activeSession=${Clerk.activeSession?.id}"
        )

        if (currentSession?.status == Session.SessionStatus.PENDING) {
            // A stale pending session (e.g. from a prior incomplete sign-up) prevents new
            // sign-in attempts at the API level. signOut() always clears local credentials
            // even on network failure, so startDestination stays "signin" (already default).
            Timber.w(
                "AppNavigation: stale pending session detected " +
                    "(id=${currentSession.id}, tasks=${currentSession.tasks.map { it.key }}), " +
                    "signing out to clear"
            )
            Clerk.auth.signOut()
        } else {
            startDestination = if (Clerk.activeSession != null) "postauth" else "signin"
            Timber.d("AppNavigation: startDestination=$startDestination")
        }

        startupComplete = true
    }

    // Log auth state changes throughout the session lifetime.
    LaunchedEffect("clerk_auth_events") {
        Clerk.auth.events.collect { event ->
            when (event) {
                is AuthEvent.SignedIn ->
                    Timber.d(
                        "AuthEvent.SignedIn: sessionId=${event.session.id}, " +
                            "userId=${event.user.id}, isSignedIn=${Clerk.isSignedIn}"
                    )
                is AuthEvent.SignedOut ->
                    Timber.d("AuthEvent.SignedOut: isSignedIn=${Clerk.isSignedIn}")
                is AuthEvent.SessionChanged ->
                    Timber.d(
                        "AuthEvent.SessionChanged: sessionId=${event.session?.id}, " +
                            "status=${event.session?.status}"
                    )
                is AuthEvent.Error ->
                    Timber.w("AuthEvent.Error: ${event.message}", event.throwable)
                else -> {}
            }
        }
    }

    if (!startupComplete) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("signin") {
                SignInScreen(
                    onSignedIn = { coroutineScope.launch { navigateAfterAuth(navController) } },
                    onNavigateToSignUp = { navController.navigate("signup") }
                )
            }

            composable("signup") {
                SignUpScreen(
                    onSignedIn = { coroutineScope.launch { navigateAfterAuth(navController) } },
                    onNavigateToSignIn = { navController.popBackStack() }
                )
            }

            // Decides onboarding-wizard vs main-tab-shell once an org is (or isn't yet) active.
            composable("postauth") {
                PostAuthRouter(
                    container = container,
                    onNeedsOnboarding = {
                        navController.navigate("onboarding") { popUpTo(0) { inclusive = true } }
                    },
                    onHasChildren = {
                        navController.navigate("main") { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            // Onboarding wizard (REQ-036) — steps 2-5; step 1 (Clerk sign-up) already happened.
            composable("onboarding") {
                OnboardingScreen(
                    container = container,
                    onComplete = {
                        navController.navigate("main") { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            // Main 4-tab shell (REQ-035).
            composable("main") {
                MainTabNavigation(
                    container = container,
                    onSignOut = {
                        coroutineScope.launch {
                            Clerk.auth.signOut()
                            navController.navigate("signin") { popUpTo(0) { inclusive = true } }
                        }
                    }
                )
            }

            // Org picker: shown when user belongs to 2+ Clerk organisations (REQ-018).
            composable("orgpicker") {
                OrgPickerScreen(
                    onOrgActivated = {
                        navController.navigate("postauth") { popUpTo(0) { inclusive = true } }
                    }
                )
            }
        }
    }
}

/**
 * Suspend helper: inspects the signed-in user's Clerk organisation memberships and
 * navigates to the appropriate post-auth screen.
 *
 *  - 0 orgs  → postauth router (no active org → routes straight to onboarding)
 *  - 1 org   → activate it via Clerk.auth.setActive, then navigate to postauth router
 *  - 2+ orgs → navigate to orgpicker
 *
 * Clears the back stack on navigation so the user cannot press Back to reach Sign In.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun navigateAfterAuth(navController: NavHostController) {
    val user = Clerk.user
    Timber.d("navigateAfterAuth: user=${user?.id ?: "null"}, isSignedIn=${Clerk.isSignedIn}")
    if (user == null) {
        // Defensive: Clerk.user should be non-null immediately after sign-in.
        Timber.w("navigateAfterAuth: Clerk.user is null — navigating to postauth defensively")
        navController.navigate("postauth") { popUpTo(0) { inclusive = true } }
        return
    }

    val memberships: List<OrganizationMembership> = try {
        when (val result = user.getOrganizationMemberships()) {
            is ClerkResult.Success<*> ->
                (result.value as? ClerkPaginatedResponse<OrganizationMembership>)?.data
                    ?: emptyList()
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
    Timber.d("navigateAfterAuth: ${memberships.size} org memberships")

    when (memberships.size) {
        0 -> {
            // User has no org yet — postauth router sends them straight to onboarding.
            navController.navigate("postauth") { popUpTo(0) { inclusive = true } }
        }
        1 -> {
            // Single org — activate it so the Clerk session JWT carries ActiveOrganizationID.
            // setActive requires (sessionId, organizationId). Pass the current session ID
            // (non-null after sign-in) and the org ID from the single membership.
            val orgId = memberships.first().organization.id
            val sessionId = Clerk.session?.id ?: ""
            try {
                Clerk.auth.setActive(sessionId, orgId)
                // WR-09: Log post-setActive state so silent result failures surface in debug.
                Timber.d(
                    "navigateAfterAuth: post-setActive " +
                        "activeOrg=${Clerk.organizationMembership?.organization?.id}, " +
                        "membership=${Clerk.organizationMembership?.role}"
                )
            } catch (e: Exception) {
                Timber.e(e, "navigateAfterAuth: setActive failed for org=$orgId")
                // Proceed to postauth; the JWT will lack org_id, but the router below handles it.
            }
            navController.navigate("postauth") { popUpTo(0) { inclusive = true } }
        }
        else -> {
            // Multiple orgs — show the picker (built in 03-04).
            navController.navigate("orgpicker") { popUpTo(0) { inclusive = true } }
        }
    }
}
