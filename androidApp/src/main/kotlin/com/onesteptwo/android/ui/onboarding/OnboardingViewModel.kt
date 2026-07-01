package com.onesteptwo.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.organizations.Organization
import com.clerk.api.network.serialization.ClerkResult
import com.onesteptwo.android.BuildConfig
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.ConsentEventsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** REQ-009 — must match the exact attestation text shown in ConsentStep verbatim. */
const val CONSENT_CHECKBOX_TEXT =
    "I confirm I am the parent or legal guardian of this child and am 18 years of age or older."

/** Identifies which version of the consent copy the parent saw (D-06, 02-CONTEXT.md). */
private const val CONSENT_TEXT_VERSION = "consent_v1"

enum class OnboardingStep { FAMILY_NAME, CHILD_DETAILS, CONSENT, SUCCESS }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.FAMILY_NAME,
    val familyName: String = "",
    val nickname: String = "",
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val consentChecked: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class OnboardingViewModel(
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val consentEventsRepository: ConsentEventsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun updateFamilyName(value: String) {
        _state.update { it.copy(familyName = value, errorMessage = null) }
    }

    fun updateNickname(value: String) {
        _state.update { it.copy(nickname = value, errorMessage = null) }
    }

    fun updateBirthMonth(month: Int) {
        _state.update { it.copy(birthMonth = month, errorMessage = null) }
    }

    fun updateBirthYear(year: Int) {
        _state.update { it.copy(birthYear = year, errorMessage = null) }
    }

    fun updateConsentChecked(checked: Boolean) {
        _state.update { it.copy(consentChecked = checked) }
    }

    /** Step 2 CTA — REQ-036 step 2: creates the Clerk org, then advances to child details. */
    fun continueFromFamilyName() {
        val name = _state.value.familyName.trim()
        if (name.isEmpty() || _state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                when (val result = Organization.create(name = name)) {
                    is ClerkResult.Success -> {
                        val org = result.value
                        val sessionId = Clerk.session?.id ?: ""
                        Clerk.auth.setActive(sessionId, org.id)
                        Timber.d("OnboardingViewModel: created org=${org.id}")
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                step = OnboardingStep.CHILD_DETAILS
                            )
                        }
                    }
                    else -> {
                        Timber.w("OnboardingViewModel: org creation failed")
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = "Couldn't create your family. Try again."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "OnboardingViewModel: org creation exception")
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "Couldn't connect. Check your internet connection and try again."
                    )
                }
            }
        }
    }

    /** Step 3 CTA — local-only validation, no network (nickname + birth month/year). */
    fun continueFromChildDetails() {
        val s = _state.value
        if (s.nickname.isBlank() || s.birthMonth == null || s.birthYear == null) {
            _state.update { it.copy(errorMessage = "Enter your child's nickname and birth month/year.") }
            return
        }
        _state.update { it.copy(step = OnboardingStep.CONSENT, errorMessage = null) }
    }

    /**
     * Step 4 CTA ("I agree — continue") — REQ-036 step 5: calls POST /v1/children
     * (server inserts consent_events then children atomically, D-07), then mirrors both rows
     * locally via the Stage 1 repositories (consent insert before children insert, no bypass).
     */
    @OptIn(ExperimentalUuidApi::class)
    fun continueFromConsent() {
        val s = _state.value
        if (!s.consentChecked || s.isSubmitting) return
        val birthMonth = s.birthMonth ?: return
        val birthYear = s.birthYear ?: return
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = childrenApiClient.createChild(
                    nickname = s.nickname.trim(),
                    birthMonth = birthMonth,
                    birthYear = birthYear,
                    appVersion = BuildConfig.VERSION_NAME,
                    consentTextVersion = CONSENT_TEXT_VERSION
                )
            ) {
                is ApiResult.Success -> {
                    val child = result.value
                    val now = Instant.now().toString()
                    val clerkUserId = Clerk.user?.id ?: ""
                    // Local mirror only — the real consent legal record is the server-side row
                    // created atomically by POST /v1/children, which doesn't echo its id/timestamp
                    // back. This local consent_events row exists solely to satisfy the on-device
                    // FK so offline event logging works; it is not the system of record.
                    val localConsentId = Uuid.random().toString()
                    consentEventsRepository.insert(
                        id = localConsentId,
                        clerkUserId = clerkUserId,
                        consentedAt = now,
                        appVersion = BuildConfig.VERSION_NAME,
                        consentTextVersion = CONSENT_TEXT_VERSION
                    )
                    childrenRepository.insert(
                        id = child.id,
                        clerkOrgId = child.clerk_org_id,
                        nickname = child.nickname,
                        birthMonth = child.birth_month.toLong(),
                        birthYear = child.birth_year.toLong(),
                        consentEventId = localConsentId,
                        createdAt = now,
                        updatedAt = now
                    )
                    _state.update { it.copy(isSubmitting = false, step = OnboardingStep.SUCCESS) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
            }
        }
    }
}

class OnboardingViewModelFactory(
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val consentEventsRepository: ConsentEventsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OnboardingViewModel(childrenApiClient, childrenRepository, consentEventsRepository) as T
    }
}
