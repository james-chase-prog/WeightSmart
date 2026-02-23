package com.weightsmart.client.ui.auth

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weightsmart.client.domain.model.User
import com.weightsmart.client.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * RegistrationViewModel
 * Manages form state, real-time validation, and Trie-based username search for account creation.
 *
 * Architecture Role:
 * Hilt-injected ViewModel that sits between [RegistrationActivity] (View) and [AuthRepository] (Data).
 * Holds all form field values as MutableLiveData so the Activity can push text changes in real-time.
 * Exposes read-only LiveData for error states, form validity, loading, search suggestions,
 * and the final registration result.
 *
 * State Management:
 * - Input fields: MutableLiveData<String> per form field (two-way with EditText via doAfterTextChanged)
 * - Error states: LiveData<String?> per required field (null = no error)
 * - Form validity: LiveData<Boolean> recomputed on every keystroke via [onDataChanged]
 * - Loading: LiveData<Boolean> toggled during the register() network call
 * - Trie search: LiveData<List<String>> debounced via [triggerUsernameSearch]
 * - Result: LiveData<Result<User>> emitted once after register() completes
 *
 * Key Concepts & Documentation:
 * Debounce: The username Trie search uses coroutine Job cancellation + 300ms delay to avoid
 *   flooding the server with requests on every keystroke.
 * <a href="https://developer.android.com/kotlin/coroutines">Reference: Kotlin Coroutines on Android</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-25
 */
@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // --- Input Fields (Two-Way Binding or Observed from UI) ---
    // Required fields (validation enforced before register)
    val username = MutableLiveData("")
    val email = MutableLiveData("")
    val password = MutableLiveData("")
    val age = MutableLiveData("") // Stored as String for EditText; converted to Int on submit

    // Optional profile fields (no validation required)
    val nickname = MutableLiveData("")
    val height = MutableLiveData("")
    val currentWeight = MutableLiveData("")
    val goalWeight = MutableLiveData("")
    val targetDate = MutableLiveData<LocalDate?>() // Nullable; set via DatePickerDialog

    // --- Error States (bound to TextInputLayout.error in the Activity) ---
    private val _usernameError = MutableLiveData<String?>(null)
    val usernameError: LiveData<String?> = _usernameError

    private val _emailError = MutableLiveData<String?>(null)
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>(null)
    val passwordError: LiveData<String?> = _passwordError

    private val _ageError = MutableLiveData<String?>(null)
    val ageError: LiveData<String?> = _ageError

    private val _dateError = MutableLiveData<String?>(null)
    val dateError: LiveData<String?> = _dateError

    // --- Operation State ---
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _registrationResult = MutableLiveData<Result<User>>()
    val registrationResult: LiveData<Result<User>> = _registrationResult

    private val _isFormValid = MutableLiveData(false)
    val isFormValid: LiveData<Boolean> = _isFormValid

    // --- Trie Search State ---
    private val _usernameSuggestions = MutableLiveData<List<String>>(emptyList())
    val usernameSuggestions: LiveData<List<String>> = _usernameSuggestions

    // Helper text shown below the username field (similar names or availability info)
    private val _usernameHelper = MutableLiveData<String?>(null)
    val usernameHelper: LiveData<String?> = _usernameHelper

    // Debounce Job: Cancels previous search if user types again quickly
    private var searchJob: Job? = null

    // --- Validation Logic ---

    /**
     * Called by the Activity on every keystroke in a required field.
     * Runs soft validation (showError=false) to enable/disable the Register button
     * without displaying error messages prematurely.
     */
    fun onDataChanged() {
        val userValid = validateUsername(false) // False = don't show error while typing immediately
        val emailValid = validateEmail(false)
        val passValid = validatePassword(false)
        val ageValid = validateAge(false)

        // Form is valid only if all REQUIRED fields are valid
        _isFormValid.value = userValid && emailValid && passValid && ageValid
    }

    /**
     * Performs "Search As You Go" with a 300ms debounce.
     * Immediately clears previous feedback on each keystroke, then after the debounce
     * fires a Trie prefix search. Results are shown via [usernameError] (taken) or
     * [usernameHelper] (similar usernames).
     */
    fun triggerUsernameSearch() {
        val query = username.value?.trim() ?: ""

        // 1. CANCEL PREVIOUS: If the user typed 'a' then immediately typed 'b',
        // stop processing 'a'. This prevents "Bloat".
        searchJob?.cancel()

        // Immediately clear previous search feedback so stale results don't persist
        _usernameHelper.value = null
        if (_usernameError.value == "Username taken") {
            _usernameError.value = null
        }

        // Don't search if empty or too short (3-char minimum for meaningful prefix matches)
        if (query.length < 3) {
            _usernameSuggestions.value = emptyList()
            return
        }

        // Don't search if too long (Server won't accept it anyway)
        if (query.length > 20) {
            _usernameSuggestions.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            // 2. THE PAUSE: Wait 300ms.
            // If the user types another letter during this 300ms,
            // this line is never passed because searchJob?.cancel() above kills it.
            delay(300)

            // 3. EXECUTE: Only if the user stopped typing for >300ms do we hit the network.
            val result = authRepository.searchUsernames(query)
            result.onSuccess { suggestions ->
                _usernameSuggestions.value = suggestions

                if (suggestions.contains(query)) {
                    _usernameError.value = "Username taken"
                    _usernameHelper.value = null
                } else {
                    _usernameHelper.value = "Username available"
                }
            }
        }
    }

    /**
     * Strict validation pass called on form submission.
     * Unlike [onDataChanged] (soft), this sets showError=true so all invalid fields
     * display their error messages immediately.
     * @return true only if every required field passes validation.
     */
    fun validateAllFields(): Boolean {
        val u = validateUsername(true)
        val e = validateEmail(true)
        val p = validatePassword(true)
        val a = validateAge(true)
        val d = validateTargetDate(true)
        return u && e && p && a && d
    }

    /**
     * Validates username length (3-20 chars).
     * Uniqueness is checked asynchronously by [triggerUsernameSearch] after the debounce.
     * @param showError If true, posts error message to [usernameError]; if false, only returns validity.
     */
    private fun validateUsername(showError: Boolean): Boolean {
        val value = username.value?.trim() ?: ""

        // Min Length Check
        if (value.length < 3) {
            if (showError) _usernameError.value = "Username must be at least 3 characters"
            return false
        }

        // Max length check — matches the server-side guard to prevent wasted API calls.
        if (value.length > 20) {
            if (showError) _usernameError.value = "Username cannot exceed 20 characters"
            return false
        }

        _usernameError.value = null
        return true
    }

    /**
     * Validates email is non-empty and matches Android's EMAIL_ADDRESS pattern.
     * @param showError If true, posts error message to [emailError].
     */
    private fun validateEmail(showError: Boolean): Boolean {
        val value = email.value?.trim() ?: ""
        if (value.isEmpty()) {
            if (showError) _emailError.value = "Email is required"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            if (showError) _emailError.value = "Invalid email address format"
            return false
        }
        _emailError.value = null
        return true
    }

    /**
     * Validates password meets minimum length requirement.
     * Aligned with server-side constraint: RegisterRequest @Size(min = 8).
     */
    private fun validatePassword(showError: Boolean): Boolean {
        val value = password.value ?: ""
        if (value.length < 8) {
            if (showError) _passwordError.value = "Password must be at least 8 characters"
            return false
        }
        _passwordError.value = null
        return true
    }

    /**
     * Validates age is a parseable integer >= 13 (minimum age requirement).
     * @param showError If true, posts error message to [ageError].
     */
    private fun validateAge(showError: Boolean): Boolean {
        val value = age.value?.toIntOrNull()
        if (value == null) {
            if (showError) _ageError.value = "Age is required"
            return false
        }
        // Requirement: "Minimum age of 13"
        if (value < 13) {
            if (showError) _ageError.value = "You must be at least 13 years old."
            return false
        }
        _ageError.value = null
        return true
    }

    /**
     * Validates that the optional target date, if provided, is in the future.
     * @param showError If true, posts error message to [dateError].
     */
    private fun validateTargetDate(showError: Boolean): Boolean {
        val date = targetDate.value
        if (date != null) {
            // Requirement: "Goal date should verify that the entered date is in the future"
            if (!date.isAfter(LocalDate.now())) {
                if (showError) _dateError.value = "Target date must be in the future"
                return false
            }
        }
        _dateError.value = null
        return true
    }

    // --- Action ---

    /**
     * Submits the registration form. Runs strict validation first; if all fields pass,
     * calls [AuthRepository.register] in a coroutine. On failure, attempts to map
     * server error messages (e.g. 409 Conflict) to specific field errors.
     */
    fun register() {
        if (!validateAllFields()) return

        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.register(
                username = username.value!!.trim(),
                email = email.value!!.trim(),
                password = password.value!!.toCharArray(),
                age = age.value!!.toInt(), // Safe because validation passed
                nickname = nickname.value?.trim()?.ifBlank { null },
                height = height.value?.toDoubleOrNull(),
                currentWeight = currentWeight.value?.toDoubleOrNull(),
                goalWeight = goalWeight.value?.toDoubleOrNull(),
                targetDate = targetDate.value?.format(DateTimeFormatter.ISO_LOCAL_DATE) // YYYY-MM-DD
            )

            _isLoading.value = false

            if (result.isFailure) {
                handleRegistrationError(result.exceptionOrNull())
            }

            _registrationResult.value = result
        }
    }

    /**
     * Maps server-side registration errors to specific field error messages.
     * Checks for constraint violations (duplicate username/email) and HTTP 409 Conflict.
     * Unrecognized errors fall through to a generic Toast in the Activity.
     */
    private fun handleRegistrationError(error: Throwable?) {
        val msg = error?.message ?: ""
        // Requirement: "If not successful... return error ie 'username already registered'"
        when {
            msg.contains("ConstraintViolation") && msg.contains("username") ->
                _usernameError.value = "Username already registered"
            msg.contains("ConstraintViolation") && msg.contains("email") ->
                _emailError.value = "Email already registered"
            msg.contains("409") -> // 409 Conflict
                _usernameError.value = "Account with this username or email already exists"
            else ->
                // Generic fallback handled by Activity Toast
                Unit
        }
    }
}