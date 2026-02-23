package com.weightsmart.client.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weightsmart.client.domain.model.User
import com.weightsmart.client.domain.repository.AuthRepository
import com.weightsmart.client.platform.worker.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * AuthViewModel
 * Manages the login screen state: loading indicator, auto-login check, and login result.
 *
 * Architecture Role:
 * Hilt-injected ViewModel bridging [LoginActivity] (View) and [AuthRepository] (Data).
 * Holds UI state via LiveData and survives configuration changes (e.g. screen rotation).
 * On init, checks for a persisted session and triggers auto-login if valid.
 *
 * State Management (LiveData):
 * - [isLoading]: Boolean toggled during the network call; Activity disables buttons while true.
 * - [loginResult]: Result<User> emitted on success (auto-login or manual); Activity navigates on Success,
 *   shows Toast on Failure.
 *
 * Data Flow:
 * 1. init -> checkIfAlreadyLoggedIn() -> if token exists, emit loginResult(Success) + delta sync
 * 2. login(username, password) -> authRepository.login() -> emit loginResult + initial sync on success
 *
 * Key Concepts & Documentation:
 * ViewModel: Stores UI-related data that is not destroyed on app rotations.
 * <a href="https://developer.android.com/topic/libraries/architecture/viewmodel">Reference: ViewModel</a>
 * LiveData: Observable data holder class; the Activity observes this to update the UI.
 * <a href="https://developer.android.com/topic/libraries/architecture/livedata">Reference: LiveData</a>
 * Coroutines (viewModelScope): Runs login operations off the main thread; auto-cancelled on ViewModel clear.
 * <a href="https://developer.android.com/topic/libraries/architecture/coroutines">Reference: Android Coroutines</a>
 * SyncManager: Orchestrates offline-first sync (P0); initial sync on first login, delta sync on auto-login.
 *
 * @author James Chase
 * @version 1.2 (P5: adapted to suspend AuthRepository API from DataStore migration)
 * @since 2026-01-25
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager  // P0: Offline-First Sync
) : ViewModel() {

    //Loading state; when true the Activity disables buttons (prevents double-tap).
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    //Login result; Success carries the [User] object, Failure carries a user-friendly error message.
    private val _loginResult = MutableLiveData<Result<User>>()
    val loginResult: LiveData<Result<User>> = _loginResult

    init {
        checkIfAlreadyLoggedIn()
    }

    /**
     * Requirement: "Unless an existing user token is saved... brought into the home page"
     * This runs immediately when the Login Screen is created.
     */
    private fun checkIfAlreadyLoggedIn() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                val storedUser = authRepository.getCurrentUser()
                if (storedUser != null) {
                    // Trigger delta sync for returning users (P0: Offline-First Sync)
                    syncManager.requestImmediateSync()

                    // Use the actual stored user for navigation
                    _loginResult.value = Result.success(storedUser)
                }
                // If storedUser is null, session is corrupted - stay on login screen
            }
        }
    }

    /**
     * Initiates the login process.
     *
     * @param username User input.
     * @param password Password input (converted to CharArray for security best practices).
     */
    fun login(username: String, password: CharArray) {
        _isLoading.value = true

        viewModelScope.launch {
            val result = authRepository.login(username, password)

            // Clear the CharArray immediately from memory for security
            password.fill('0')

            _isLoading.value = false

            // Handle Custom Error Messages based on Requirements
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                val message = mapErrorToUserMessage(error)
                _loginResult.value = Result.failure(Exception(message))
            } else {
                // Trigger initial sync after successful login (P0: Offline-First Sync)
                syncManager.triggerInitialSync()
                _loginResult.value = result
            }
        }
    }

    /**
     * Convenience overload that accepts a plain String password.
     * Converts to CharArray and delegates to the primary [login] method.
     * This exists because the Activity reads EditText as String, but the primary
     * method accepts CharArray for secure memory clearing.
     */
    fun login(username: String, passwordStr: String) {
        login(username, passwordStr.toCharArray())
    }

    /**
     * Translates technical server errors into user-friendly messages.
     * Requirement: Distinguish between "Wrong Password", "Locked", and "No Internet".
     */
    private fun mapErrorToUserMessage(error: Throwable?): String {
        val msg = error?.message ?: ""

        return when {
            // Requirement: "Wrong Password 5 times -> Excessive Attempts"
            msg.contains("423") || msg.contains("429") || msg.contains("Locked") ->
                "Excessive Attempts. Account Locked."

            // Requirement: "Fail a login -> Wrong Username or Password"
            msg.contains("401") || msg.contains("Unauthorized") ->
                "Wrong Username or Password"

            // Network Errors
            msg.contains("Unable to resolve host") ->
                "No Internet Connection"

            else -> "Login Failed. Please try again."
        }
    }
}