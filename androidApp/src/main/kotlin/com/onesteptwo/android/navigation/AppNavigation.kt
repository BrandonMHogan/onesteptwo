package com.onesteptwo.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clerk.api.Clerk
import com.clerk.api.network.ClerkPaginatedResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.organizations.OrganizationMembership
import com.clerk.api.user.getOrganizationMemberships
import com.onesteptwo.android.ui.PostAuthStub
import com.onesteptwo.android.ui.auth.SignInScreen
import com.onesteptwo.android.ui.auth.SignUpScreen
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Root navigation graph for the app. Auth-gates all routes:
 *
 *   No active session   → "signin" (start)
 *   After sign-in/up:
 *     0 orgs            → "postauth" (stub — user not yet in a family)
 *     1 org             → activate org → "postauth"
 *     2+ orgs           → "orgpicker" (built in 03-04; placeholder here)
 *
 * Auth screens are popped from the back stack once the user is signed in and an
 * organisation is active, preventing navigation back to Sign In after authentication
 * (UI-SPEC Navigation Flow).
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    // Determine start destination from current Clerk session state.
    val startDestination = if (Clerk.isSignedIn) "postauth" else "signin"

    /**
     * Called by SignInScreen and SignUpScreen on successful authentication.
     * Fetches org memberships, activates the single org if present, and navigates
     * to the correct destination (postauth or orgpicker).
     */
    fun onAuthSuccess() {
        coroutineScope.launch {
            navigateAfterAuth(navController)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("signin") {
            SignInScreen(
                onSignedIn = ::onAuthSuccess,
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }

        composable("signup") {
            SignUpScreen(
                onSignedIn = ::onAuthSuccess,
                onNavigateToSignIn = { navController.popBackStack() }
            )
        }

        composable("postauth") {
            PostAuthStub(
                onInvite = {
                    // Invite Caregiver screen is built in plan 03-04.
                }
            )
        }

        // Org picker: shown when user belongs to 2+ Clerk organisations.
        // Full implementation in plan 03-04 (OrgPickerScreen).
        composable("orgpicker") {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Choose a family (coming soon)")
            }
        }
    }
}

/**
 * Suspend helper: inspects the signed-in user's Clerk organisation memberships and
 * navigates to the appropriate post-auth screen.
 *
 *  - 0 orgs  → postauth stub (user not yet added to a family; org creation is Phase 5)
 *  - 1 org   → activate it via Clerk.auth.setActive, then navigate to postauth
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
            // User has no org yet (invited in 03-04 / Phase 5 onboarding).
            navController.navigate("postauth") { popUpTo(0) { inclusive = true } }
        }
        1 -> {
            // Single org — activate it so the Clerk session JWT carries ActiveOrganizationID.
            // setActive requires (sessionId, organizationId). Pass the current session ID
            // (non-null after sign-in) and the org ID from the single membership.
            val orgId = memberships.first().organization.id
            val sessionId = Clerk.session?.id ?: ""
            Clerk.auth.setActive(sessionId, orgId)
            navController.navigate("postauth") { popUpTo(0) { inclusive = true } }
        }
        else -> {
            // Multiple orgs — show the picker (built in 03-04).
            navController.navigate("orgpicker") { popUpTo(0) { inclusive = true } }
        }
    }
}
