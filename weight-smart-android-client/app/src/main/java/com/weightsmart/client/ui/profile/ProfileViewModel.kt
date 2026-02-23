package com.weightsmart.client.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.repository.WeightRepository
import com.weightsmart.client.domain.usecase.UpdateProfileUseCase
import com.weightsmart.client.platform.worker.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ProfileUiState
 * Immutable snapshot of the profile screen display data.
 *
 * @property headerName Display name for the greeting header (nickname or username).
 * @property nickname Current nickname field value.
 * @property age Current age field value (e.g. "25").
 * @property height Current height field value (e.g. "68.0").
 * @property goalWeight Current goal weight field value (formatted, e.g. "175.0").
 * @property goalDate Current goal date field value (formatted "MM/dd/yyyy").
 * @property isSaving True while a save operation is in progress.
 */
data class ProfileUiState(
    val headerName: String = "",
    val nickname: String = "",
    val age: String = "",
    val height: String = "",
    val goalWeight: String = "",
    val goalDate: String = "",
    val isSaving: Boolean = false
)

/**
 * ProfileEvent
 * One-shot UI events consumed exactly once by [ProfileActivity].
 * Sent via a Channel (not StateFlow) to avoid replaying stale events on resubscription.
 */
sealed class ProfileEvent {
    data object SaveSuccess : ProfileEvent()
    data class SaveError(val message: String) : ProfileEvent()
    data object LoggedOut : ProfileEvent()
}

/**
 * ProfileViewModel
 * Manages the profile editing screen state: nickname, goal weight, goal date, and save operations.
 *
 * Architecture Role:
 * Hilt-injected ViewModel following the same StateFlow + Channel pattern established in
 * [HomeViewModel] and [TableViewModel]. Sits between [ProfileActivity] (View) and the
 * data layer ([SessionManager], [UpdateProfileUseCase]).
 *
 * Data Flow Pipeline:
 * 1. init -> hydrate SessionManager -> observe userFlow -> emit ProfileUiState
 * 2. saveProfile() -> validate input -> UpdateProfileUseCase -> emit SaveSuccess/SaveError event
 * 3. userName/goalWeight auto-update from SessionManager.userFlow
 *
 * @author James Chase
 * @version 2.1 (P5: adapted to suspend SessionManager API from DataStore migration)
 * @since 2026-01-20
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val session: SessionManager,
    private val updateProfile: UpdateProfileUseCase,
    private val syncManager: SyncManager,
    private val weightRepository: WeightRepository
) : ViewModel() {

    // Continuous UI state; Activity collects this to update display fields.
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    // One-shot event channel; Activity consumes each event exactly once for Toast/finish.
    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val displayDateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    init {
        viewModelScope.launch {
            // Ensure the flow is hydrated from disk if this is a cold start
            session.getUser()
        }
        observeProfile()
    }

    /**
     * Observes user profile changes from SessionManager.
     * Populates all editable fields from the current User.
     */
    private fun observeProfile() {
        viewModelScope.launch {
            session.userFlow.filterNotNull().collect { user ->
                _uiState.update {
                    ProfileUiState(
                        headerName = user.nickname?.takeIf { n -> n.isNotBlank() } ?: user.username,
                        nickname = user.nickname ?: "",
                        age = if (user.age > 0) user.age.toString() else "",
                        height = user.height?.let { h -> String.format("%.1f", h) } ?: "",
                        goalWeight = user.goalWeight?.let { w -> String.format("%.1f", w) } ?: "",
                        goalDate = user.targetDate?.format(displayDateFmt) ?: ""
                    )
                }
            }
        }
    }

    /**
     * Validates input and persists profile changes via [UpdateProfileUseCase].
     * Emits [ProfileEvent.SaveSuccess] on success or [ProfileEvent.SaveError] on failure.
     *
     * @param nickname Raw nickname text from the EditText.
     * @param ageStr Raw age text from the EditText.
     * @param heightStr Raw height text from the EditText.
     * @param goalWeightStr Raw goal weight text from the EditText.
     * @param goalDateStr Raw goal date text from the EditText (MM/dd/yyyy format).
     */
    fun saveProfile(nickname: String, ageStr: String, heightStr: String, goalWeightStr: String, goalDateStr: String) {
        val trimmedNickname = nickname.trim().ifBlank { null }
        val trimmedAge = ageStr.trim()
        val trimmedHeight = heightStr.trim()
        val trimmedGoalWeight = goalWeightStr.trim()
        val trimmedGoalDate = goalDateStr.trim()

        // Validate age
        val age: Int? = if (trimmedAge.isNotEmpty()) {
            trimmedAge.toIntOrNull()
                ?: run {
                    viewModelScope.launch { _events.send(ProfileEvent.SaveError("Age must be a whole number")) }
                    return
                }
        } else null

        // Validate height
        val height: Double? = if (trimmedHeight.isNotEmpty()) {
            trimmedHeight.toDoubleOrNull()
                ?: run {
                    viewModelScope.launch { _events.send(ProfileEvent.SaveError("Height must be a number")) }
                    return
                }
        } else null

        // Validate goal weight
        val goalWeight: Double? = if (trimmedGoalWeight.isNotEmpty()) {
            trimmedGoalWeight.toDoubleOrNull()
                ?: run {
                    viewModelScope.launch { _events.send(ProfileEvent.SaveError("Goal weight must be a number")) }
                    return
                }
        } else null

        // Parse goal date
        val goalDate: LocalDate? = if (trimmedGoalDate.isNotEmpty()) {
            try {
                LocalDate.parse(trimmedGoalDate, displayDateFmt)
            } catch (_: Exception) {
                null
            }
        } else null

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val result = updateProfile(
                nickname = trimmedNickname,
                age = age,
                height = height,
                goalWeight = goalWeight,
                targetDate = goalDate
            )

            _uiState.update { it.copy(isSaving = false) }

            if (result.isSuccess) {
                _events.send(ProfileEvent.SaveSuccess)
            } else {
                _events.send(ProfileEvent.SaveError(result.exceptionOrNull()?.message ?: "Failed to update profile."))
            }
        }
    }

    /**
     * Ends the user session: cancels background sync jobs, clears all local session data,
     * and emits [ProfileEvent.LoggedOut] so the Activity can navigate to the login screen.
     */
    fun logout() {
        viewModelScope.launch {
            syncManager.cancelAllSync()
            weightRepository.pushPendingChanges()
            session.clearSyncState()
            session.clearSession()
            _events.send(ProfileEvent.LoggedOut)
        }
    }
}
