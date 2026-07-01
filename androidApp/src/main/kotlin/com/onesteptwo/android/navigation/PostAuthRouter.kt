package com.onesteptwo.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.clerk.api.Clerk
import com.onesteptwo.android.AppContainer
import com.onesteptwo.api.ApiResult
import timber.log.Timber
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Decides onboarding-wizard vs main-tab-shell on every post-auth landing (REQ-036's "returning
 * caregiver skips the wizard" rule):
 *
 *  1. No active org at all → onboarding (org creation is wizard step 2 — nothing to check yet).
 *  2. Local `children` rows exist → main shell (fast path, no network).
 *  3. Local DB empty but the org has children server-side (fresh install, accepted invite or
 *     reinstall) → hydrate them locally via `GET /v1/children`, then main shell.
 *  4. Otherwise → onboarding.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun PostAuthRouter(
    container: AppContainer,
    onNeedsOnboarding: () -> Unit,
    onHasChildren: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (Clerk.organizationMembership == null) {
            Timber.d("PostAuthRouter: no active org — routing to onboarding")
            onNeedsOnboarding()
            return@LaunchedEffect
        }

        val localChildren = container.childrenRepository.getAll()
        if (localChildren.isNotEmpty()) {
            Timber.d("PostAuthRouter: ${localChildren.size} local children — routing to main")
            onHasChildren()
            return@LaunchedEffect
        }

        when (val result = container.childrenApiClient.getChildren()) {
            is ApiResult.Success -> {
                if (result.value.isEmpty()) {
                    Timber.d("PostAuthRouter: org has no children yet — routing to onboarding")
                    onNeedsOnboarding()
                } else {
                    Timber.d("PostAuthRouter: hydrating ${result.value.size} children from server")
                    val clerkUserId = Clerk.user?.id ?: ""
                    result.value.forEach { child ->
                        val now = Instant.now().toString()
                        // Synthetic local consent row — see OnboardingViewModel for why this
                        // can't carry the real server-side consent_events id/values.
                        val localConsentId = Uuid.random().toString()
                        container.consentEventsRepository.insert(
                            id = localConsentId,
                            clerkUserId = clerkUserId,
                            consentedAt = now,
                            appVersion = "unknown",
                            consentTextVersion = "unknown"
                        )
                        container.childrenRepository.insert(
                            id = child.id,
                            clerkOrgId = child.clerk_org_id,
                            nickname = child.nickname,
                            birthMonth = child.birth_month.toLong(),
                            birthYear = child.birth_year.toLong(),
                            consentEventId = localConsentId,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                    onHasChildren()
                }
            }
            is ApiResult.Failure -> {
                Timber.w("PostAuthRouter: getChildren failed (${result.message}) — routing to onboarding")
                onNeedsOnboarding()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
