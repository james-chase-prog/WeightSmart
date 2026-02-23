package com.weightsmart.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.repository.WeightRepository
import com.weightsmart.client.domain.usecase.AddWeightUseCase
import com.weightsmart.client.domain.usecase.GoalReachedUseCase
import com.weightsmart.client.platform.notifications.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * HomeUiState
 * Immutable snapshot of the home screen display data.
 *
 * @property userName Display name (nickname preferred, falls back to username).
 * @property currentWeight Formatted most-recent weight from Room (e.g. "184.6"), or "000.0" if none.
 * @property goalWeight Formatted goal weight from SessionManager (e.g. "175.0"), or "000.0" if not set.
 */
data class HomeUiState(
    val userName: String = "",
    val currentWeight: String = "000.0",
    val goalWeight: String = "000.0"
)

/**
 * HomeEvent
 * One-shot UI events consumed exactly once by [HomeFragment].
 * Sent via a Channel (not StateFlow) to avoid replaying stale events on resubscription.
 */
sealed class HomeEvent {
    //Weight entry was successfully persisted.
    data object WeightAdded : HomeEvent()
    //Goal weight has been reached; triggers a celebratory notification + Toast.
    data object GoalReached : HomeEvent()
    //An error occurred during weight submission.
    data class Error(val message: String) : HomeEvent()
}

/**
 * HomeViewModel
 * Manages the home dashboard state: current weight, goal weight, user display name, and weight entry.
 *
 * Architecture Role:
 * Hilt-injected ViewModel that sits between [HomeFragment] (View) and the data layer
 * ([WeightRepository], [SessionManager], use cases). Uses the StateFlow + Channel pattern:
 * - [uiState] (StateFlow): Continuous state that the Fragment collects for display updates.
 * - [events] (Channel -> Flow): One-shot effects consumed exactly once (Toast, notification).
 *
 * Data Flow Pipeline:
 * 1. init -> hydrate SessionManager -> observe user profile (nickname/goalWeight) + Room weight Flow
 * 2. commitWeight() -> AddWeightUseCase -> GoalReachedUseCase check -> emit event(s)
 * 3. currentWeight auto-updates from Room Flow (any write: manual entry, sync, delete triggers update)
 * 4. userName/goalWeight auto-updates from SessionManager.userFlow (login, profile save triggers update)
 *
 * Key Concepts & Documentation:
 * StateFlow: Hot flow that always holds the latest value; Fragment collects it in repeatOnLifecycle.
 * <a href="https://developer.android.com/kotlin/flow/stateflow-and-sharedflow">Reference: StateFlow</a>
 * Channel: Used for one-shot events that should not replay on resubscription.
 * <a href="https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/-channel/">Reference: Channel</a>
 *
 * @author James Chase
 * @version 1.1 (P5: adapted to suspend SessionManager API from DataStore migration)
 * @since 2026-01-20
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: SessionManager,
    private val addWeight: AddWeightUseCase,
    private val goalReached: GoalReachedUseCase,
    private val scheduler: NotificationScheduler,
    private val weightRepository: WeightRepository
) : ViewModel() {

    //Continuous UI state; Fragment collects this to update display fields.
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // One-shot event channel; Fragment consumes each event exactly once for Toasts/notifications.
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Ensure the flow is hydrated from disk if this is a cold start
            session.getUser()
        }

        observeProfile()
        observeCurrentWeight()
    }

    /**
     * Observes user profile changes from SessionManager.
     * Auto-updates userName (nickname ?? username) and goalWeight
     * when any code path calls saveUser() or updateCurrentWeight():
     * login, registration, ProfileActivity save, etc.
     */
    private fun observeProfile() {
        viewModelScope.launch {
            session.userFlow.filterNotNull().collect { user ->
                _uiState.update { current ->
                    current.copy(
                        userName = user.nickname?.takeIf { it.isNotBlank() } ?: user.username,
                        goalWeight = user.goalWeight?.let { String.format("%.1f", it) } ?: "000.0"
                    )
                }
            }
        }
    }

    /**
     * Observes the most recent weight entry from Room.
     * Auto-updates when any code path writes to the weight_entries table:
     * manual entry, SyncWorker pull, delete, etc.
     */
    private fun observeCurrentWeight() {
        viewModelScope.launch {
            weightRepository.observeMostRecentWeight().collect { weight ->
                _uiState.update { current ->
                    current.copy(
                        currentWeight = weight?.let { String.format("%.1f", it) } ?: "000.0"
                    )
                }
            }
        }
    }

    /**
     * Persists a new weight entry and checks for goal completion.
     * Called by [HomeFragment.submitWeight] after input validation.
     *
     * Flow: validate session -> AddWeightUseCase -> GoalReachedUseCase check ->
     *   if goal reached: fire notification + GoalReached event ->
     *   always: fire WeightAdded event (currentWeight auto-updates via Room Flow).
     *
     * @param weightLbs Weight value in pounds.
     * @param measuredAt Timestamp when the measurement was taken (user-selected date/time).
     */
    fun commitWeight(weightLbs: Double, measuredAt: Instant) {
        viewModelScope.launch {
            val user = session.getUser()
            if (user == null) {
                _events.send(HomeEvent.Error("Not logged in."))
                return@launch
            }

            runCatching { addWeight(weightLbs, measuredAt) }
                .onSuccess {
                    // Check if goal was reached
                    if (goalReached(weightLbs)) {
                        scheduler.showGoalReachedNotification()
                        _events.send(HomeEvent.GoalReached)
                    }

                    // currentWeight auto-updates via Room Flow
                    // userName/goalWeight auto-updates via SessionManager Flow
                    _events.send(HomeEvent.WeightAdded)
                }
                .onFailure {
                    _events.send(HomeEvent.Error(it.message ?: "Failed to add weight."))
                }
        }
    }
}
