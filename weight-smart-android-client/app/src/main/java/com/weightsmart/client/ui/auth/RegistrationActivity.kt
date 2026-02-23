package com.weightsmart.client.ui.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.weightsmart.client.databinding.ActivityRegistrationBinding
import com.weightsmart.client.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * RegistrationActivity
 * The UI for creating a new user account with real-time field validation.
 *
 * Architecture Role:
 * Thin "View" layer in the MVVM pattern. Bridges raw Android inputs (EditText, DatePicker)
 * and the [RegistrationViewModel]'s LiveData. Listens for text changes via doAfterTextChanged
 * to trigger validation in real-time, and observes LiveData for error/loading/result state.
 *
 * Lifecycle / Navigation:
 * onCreate -> inflate ViewBinding -> setupInputs + setupObservers + setupListeners
 * On successful registration -> launches [MainActivity] with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
 *   (clears back stack so "Back" from Home does not return here).
 * On "Already have an account?" -> finish() returns to LoginActivity.
 *
 * Key Concepts & Documentation:
 * DatePickerDialog: Standard Android dialog for picking dates.
 * <a href="https://developer.android.com/reference/android/app/DatePickerDialog">Reference: DatePickerDialog</a>
 * TextWatcher (doAfterTextChanged): Kotlin extension that pushes UI updates to the ViewModel immediately.
 * <a href="https://developer.android.com/reference/kotlin/androidx/core/widget/package-summary#(android.widget.TextView).doAfterTextChanged(kotlin.Function1)">Reference: doAfterTextChanged</a>
 * AutoCompleteTextView: Shows Trie-based username suggestions from the server.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-25
 */
@AndroidEntryPoint
class RegistrationActivity : AppCompatActivity() {

    //Lazy-injected ViewModel; holds form state, validation logic, and Trie search results.
    private val viewModel: RegistrationViewModel by viewModels()

    //Generated binding class for activity_registration.xml. */
    private lateinit var binding: ActivityRegistrationBinding

    // Sate formatter for display purposes (MM/dd/yyyy); server receives ISO_LOCAL_DATE format.
    private val displayDateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure you create 'activity_registration.xml' matching these IDs
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInputs()
        setupObservers()
        setupListeners()
    }

    /**
     * Wires two-way data flow between EditText fields and the ViewModel.
     * Required fields call [RegistrationViewModel.onDataChanged] on every keystroke
     * to enable/disable the Register button in real-time.
     * Optional fields only push values without triggering form-level validation.
     */
    private fun setupInputs() {
        // --- Required Fields Data Binding ---
        // Listen to text changes and update ViewModel immediately
        // This allows the "Register" button to enable/disable in real-time.

        binding.usernameInput.doAfterTextChanged {
            viewModel.username.value = it.toString()
            viewModel.onDataChanged()
            viewModel.triggerUsernameSearch()
        }
        binding.emailInput.doAfterTextChanged {
            viewModel.email.value = it.toString()
            viewModel.onDataChanged()
        }
        binding.passwordInput.doAfterTextChanged {
            viewModel.password.value = it.toString()
            viewModel.onDataChanged()
        }
        binding.ageInput.doAfterTextChanged {
            viewModel.age.value = it.toString()
            viewModel.onDataChanged()
        }

        // --- Optional Fields ---
        binding.nicknameInput.doAfterTextChanged { viewModel.nickname.value = it.toString() }
        binding.heightInput.doAfterTextChanged { viewModel.height.value = it.toString() }
        binding.currentWeightInput.doAfterTextChanged { viewModel.currentWeight.value = it.toString() }
        binding.goalWeightInput.doAfterTextChanged { viewModel.goalWeight.value = it.toString() }

        // --- Date Picker Logic ---
        binding.goalDateInput.setOnClickListener {
            val today = LocalDate.now()
            DatePickerDialog(this, { _, year, month, day ->
                val selectedDate = LocalDate.of(year, month + 1, day)
                viewModel.targetDate.value = selectedDate
                binding.goalDateInput.setText(selectedDate.format(displayDateFmt))
            }, today.year, today.monthValue - 1, today.dayOfMonth).show()
        }
    }

    /**
     * Subscribes to LiveData streams exposed by [RegistrationViewModel].
     * Observes five categories: field errors, form validity, loading state,
     * Trie username suggestions, and the final registration result.
     */
    private fun setupObservers() {
        // --- Error Display ---
        viewModel.usernameError.observe(this) { error -> binding.usernameLayout.error = error }
        viewModel.emailError.observe(this) { error -> binding.emailLayout.error = error }
        viewModel.passwordError.observe(this) { error -> binding.passwordLayout.error = error }
        viewModel.ageError.observe(this) { error -> binding.ageLayout.error = error }
        viewModel.dateError.observe(this) { error -> binding.goalDateLayout.error = error }

        // --- Button State ---
        viewModel.isFormValid.observe(this) { isValid ->
            binding.registerButton.isEnabled = isValid
        }

        // --- Loading State ---
        viewModel.isLoading.observe(this) { isLoading ->
            binding.registerButton.text = if (isLoading) "Creating Account..." else "Register"
            binding.registerButton.isEnabled = !isLoading
            if (!isLoading && viewModel.isFormValid.value == true) {
                binding.registerButton.isEnabled = true
            }
        }

        // --- Trie Search Helper Text ---
        viewModel.usernameHelper.observe(this) { helper ->
            binding.usernameLayout.helperText = helper
        }

        // --- Registration Result ---
        viewModel.registrationResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Welcome, ${it.username}!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            result.onFailure { error ->
                if (viewModel.usernameError.value == null && viewModel.emailError.value == null) {
                    Toast.makeText(this, "Registration Failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Wires tap handlers for the Register button and the "Already have an account?" link.
     * Register delegates to [RegistrationViewModel.register]; the link finishes this Activity
     * to return to LoginActivity (which is still on the back stack).
     */
    private fun setupListeners() {
        binding.registerButton.setOnClickListener {
            viewModel.register()
        }

        binding.loginLink.setOnClickListener {
            // Return to login if they already have an account
            finish()
        }
    }
}
