package com.onesteptwo.android.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer

/**
 * Hosts onboarding steps 2-5 (step 1, Clerk sign-up, already happened before this route is
 * reached). One route with an internal step enum — steps share an in-progress draft rather
 * than being a nested nav graph (05-01-PLAN.md architecture decision).
 */
@Composable
fun OnboardingScreen(container: AppContainer, onComplete: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModelFactory(
            container.childrenApiClient,
            container.childrenRepository,
            container.consentEventsRepository
        )
    )
    val state by viewModel.state.collectAsState()

    when (state.step) {
        OnboardingStep.FAMILY_NAME -> FamilyNameStep(
            familyName = state.familyName,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onFamilyNameChange = viewModel::updateFamilyName,
            onContinue = viewModel::continueFromFamilyName
        )
        OnboardingStep.CHILD_DETAILS -> ChildDetailsStep(
            nickname = state.nickname,
            birthMonth = state.birthMonth,
            birthYear = state.birthYear,
            errorMessage = state.errorMessage,
            onNicknameChange = viewModel::updateNickname,
            onBirthMonthChange = viewModel::updateBirthMonth,
            onBirthYearChange = viewModel::updateBirthYear,
            onContinue = viewModel::continueFromChildDetails
        )
        OnboardingStep.CONSENT -> ConsentStep(
            consentChecked = state.consentChecked,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onConsentCheckedChange = viewModel::updateConsentChecked,
            onContinue = viewModel::continueFromConsent
        )
        OnboardingStep.SUCCESS -> SuccessStep(
            childNickname = state.nickname,
            onStartTracking = onComplete
        )
    }
}
