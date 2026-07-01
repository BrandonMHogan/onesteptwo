package com.onesteptwo.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clerk.api.Clerk
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.BuildConfig
import com.onesteptwo.android.ui.onboarding.CONSENT_TEXT_VERSION
import com.onesteptwo.android.ui.onboarding.ChildDetailsStep
import com.onesteptwo.android.ui.onboarding.ConsentStep
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.ConsentEventsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class AddChildStep { DETAILS, CONSENT }

data class AddChildUiState(
    val step: AddChildStep = AddChildStep.DETAILS,
    val nickname: String = "",
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val consentChecked: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

/**
 * Settings "Add child" — repeats the full consent screen (05-CONTEXT.md D-09), reusing the
 * onboarding wizard's [ChildDetailsStep] and [ConsentStep] composables with `showStepDots =
 * false` (this is a standalone 2-step flow, not the 4-step onboarding wizard). No family/org
 * creation step — the org already exists.
 */
class AddChildViewModel(
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val consentEventsRepository: ConsentEventsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AddChildUiState())
    val state: StateFlow<AddChildUiState> = _state.asStateFlow()

    fun updateNickname(value: String) = _state.update { it.copy(nickname = value, errorMessage = null) }
    fun updateBirthMonth(month: Int) = _state.update { it.copy(birthMonth = month, errorMessage = null) }
    fun updateBirthYear(year: Int) = _state.update { it.copy(birthYear = year, errorMessage = null) }
    fun updateConsentChecked(checked: Boolean) = _state.update { it.copy(consentChecked = checked) }

    fun continueFromDetails() {
        val s = _state.value
        if (s.nickname.isBlank() || s.birthMonth == null || s.birthYear == null) {
            _state.update { it.copy(errorMessage = "Enter your child's nickname and birth month/year.") }
            return
        }
        _state.update { it.copy(step = AddChildStep.CONSENT, errorMessage = null) }
    }

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
                    val localConsentId = Uuid.random().toString()
                    consentEventsRepository.insert(
                        id = localConsentId,
                        clerkUserId = Clerk.user?.id ?: "",
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
                    _state.update { it.copy(isSubmitting = false, done = true) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
            }
        }
    }
}

class AddChildViewModelFactory(
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val consentEventsRepository: ConsentEventsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddChildViewModel(childrenApiClient, childrenRepository, consentEventsRepository) as T
}

@Composable
fun AddChildScreen(container: AppContainer, onDone: () -> Unit) {
    val viewModel: AddChildViewModel = viewModel(
        factory = AddChildViewModelFactory(
            container.childrenApiClient,
            container.childrenRepository,
            container.consentEventsRepository
        )
    )
    val state by viewModel.state.collectAsState()

    if (state.done) {
        onDone()
        return
    }

    when (state.step) {
        AddChildStep.DETAILS -> ChildDetailsStep(
            nickname = state.nickname,
            birthMonth = state.birthMonth,
            birthYear = state.birthYear,
            errorMessage = state.errorMessage,
            onNicknameChange = viewModel::updateNickname,
            onBirthMonthChange = viewModel::updateBirthMonth,
            onBirthYearChange = viewModel::updateBirthYear,
            onContinue = viewModel::continueFromDetails,
            showStepDots = false
        )
        AddChildStep.CONSENT -> ConsentStep(
            consentChecked = state.consentChecked,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onConsentCheckedChange = viewModel::updateConsentChecked,
            onContinue = viewModel::continueFromConsent,
            showStepDots = false
        )
    }
}
