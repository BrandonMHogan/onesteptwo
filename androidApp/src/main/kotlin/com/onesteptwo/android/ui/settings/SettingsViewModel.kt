package com.onesteptwo.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.ClerkPaginatedResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.organizations.OrganizationMembership
import com.clerk.api.organizations.delete
import com.clerk.api.organizations.getOrganizationMemberships
import com.clerk.api.organizations.removeMember
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.api.NotificationPreferencesApiClient
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.NotificationPreferencesRepository
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SettingsUiState(
    val isAdmin: Boolean = false,
    val userEmail: String = "",
    val familyMembers: List<OrganizationMembership> = emptyList(),
    val isLoadingFamily: Boolean = false,
    val familyError: String? = null,
    val accountError: String? = null
)

/**
 * Backs the Settings tab shell. Role/email come straight from the live Clerk session (no
 * loading state needed); the family member list requires a network call. Children (Task 8) and
 * notification preferences (Task 9) are added to this same view model in later tasks rather than
 * split into per-section view models, since every section shares one Settings screen lifecycle.
 */
class SettingsViewModel(
    private val childrenRepository: ChildrenRepository,
    private val childrenApiClient: ChildrenApiClient,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notificationPreferencesApiClient: NotificationPreferencesApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            isAdmin = Clerk.organizationMembership?.role == "org:admin",
            userEmail = Clerk.user?.primaryEmailAddress?.emailAddress ?: ""
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val children: StateFlow<List<Children>> =
        childrenRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadFamily()
    }

    fun loadFamily() {
        val org = Clerk.organization ?: return
        _state.update { it.copy(isLoadingFamily = true, familyError = null) }
        viewModelScope.launch {
            when (val result = org.getOrganizationMemberships(limit = 100)) {
                is ClerkResult.Success<*> -> {
                    val members = (result.value as? ClerkPaginatedResponse<OrganizationMembership>)?.data ?: emptyList()
                    _state.update { it.copy(isLoadingFamily = false, familyMembers = members) }
                }
                else -> {
                    Timber.w("SettingsViewModel: failed to load family members")
                    _state.update {
                        it.copy(isLoadingFamily = false, familyError = "Couldn't load data. Pull down to refresh.")
                    }
                }
            }
        }
    }

    /** Family section [✕] — admin removes a caregiver (05-CONTEXT.md D-08, direct Clerk SDK call). */
    fun removeCaregiver(userId: String) {
        val org = Clerk.organization ?: return
        viewModelScope.launch {
            when (org.removeMember(userId)) {
                is ClerkResult.Success -> loadFamily()
                else -> _state.update { it.copy(familyError = "Couldn't remove caregiver. Try again.") }
            }
        }
    }

    /**
     * Caregiver's own "Delete my data" — self-leaves the family org via the Clerk SDK (same
     * direct-Clerk pattern as [removeCaregiver], applied to the current user's own membership).
     * No Go endpoint exists for this: DELETE /v1/account is admin-only (REQ-016) and full-family
     * erasure, so it cannot be reused for a caregiver's own data.
     */
    suspend fun leaveFamily(): Boolean {
        val membership = Clerk.organizationMembership ?: return false
        return when (membership.delete()) {
            is ClerkResult.Success -> true
            else -> {
                _state.update { it.copy(accountError = "Couldn't leave the family. Try again.") }
                false
            }
        }
    }

    /** Refreshes the local notification-preference cache from the server (Settings screen load). */
    fun refreshNotificationPreferences() {
        viewModelScope.launch {
            when (val result = notificationPreferencesApiClient.getPreferences()) {
                is ApiResult.Success -> result.value.forEach { pref ->
                    notificationPreferencesRepository.setLocal(pref.child_id, pref.enabled)
                }
                is ApiResult.Failure -> Unit // local cache already shows last-known/default state
            }
        }
    }

    /** Admin "Delete my data" — REQ-012 full family erasure via DELETE /v1/account. */
    suspend fun deleteAccount(): Boolean =
        when (childrenApiClient.deleteAccount()) {
            is ApiResult.Success -> true
            is ApiResult.Failure -> {
                _state.update { it.copy(accountError = "Couldn't delete your data. Try again.") }
                false
            }
        }

    /** Clears a stale account-deletion error before the confirm dialog reopens. */
    fun clearAccountError() {
        _state.update { it.copy(accountError = null) }
    }
}

class SettingsViewModelFactory(
    private val childrenRepository: ChildrenRepository,
    private val childrenApiClient: ChildrenApiClient,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notificationPreferencesApiClient: NotificationPreferencesApiClient
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            childrenRepository,
            childrenApiClient,
            notificationPreferencesRepository,
            notificationPreferencesApiClient
        ) as T
    }
}
