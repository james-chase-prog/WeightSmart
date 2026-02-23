package com.weightsmart.client.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.weightsmart.client.databinding.ActivityLoginBinding
import com.weightsmart.client.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * LoginActivity
 * The entry point of the application; presents the login form and navigates on success.
 *
 * Architecture Role:
 * Thin "View" layer in the MVVM pattern. Renders the XML-defined UI, handles user
 * clicks, and observes [AuthViewModel] via LiveData for state changes.
 * Contains zero business logic -- all authentication decisions are delegated to the ViewModel.
 *
 * Lifecycle / Navigation:
 * onCreate -> inflate ViewBinding -> setupObservers + setupListeners
 * On successful login -> launches [MainActivity] with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
 *   (clears back stack so "Back" from Home exits the app).
 * On "Create Account" -> launches [RegistrationActivity].
 * On "Forgot Password" -> placeholder Toast (AccountRecoveryActivity planned for future sprint).
 *
 * Key Concepts & Documentation:
 * AndroidEntryPoint: Tells Hilt to inject dependencies into this Activity.
 * <a href="https://dagger.dev/hilt/android-entry-point.html">Reference: @AndroidEntryPoint</a>
 * ViewBinding: Generates a binding class (ActivityLoginBinding) to access XML views type-safely.
 * <a href="https://developer.android.com/topic/libraries/view-binding">Reference: View Binding</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-25
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    //Lazy-injected ViewModel; survives configuration changes (e.g. screen rotation).
    private val viewModel: AuthViewModel by viewModels()

    // Generated binding class for activity_login.xml; provides type-safe view references.
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
    }

    /**
     * Wires click listeners for the login button, create-account button, and forgot-password button.
     * Delegates all logic to the ViewModel or navigation intents.
     */
    private fun setupListeners() {
        binding.loginButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                viewModel.login(username, password)
            } else {
                Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show()
            }
        }

        // Requirement: "Create Account will launch a registration activity"
        binding.createAccountButton.setOnClickListener {
            // Use the class name you actually have in your project
            // If you renamed it to RegistrationActivity, change it here.
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }

        // Requirement: "Forgot username/password will take them to AccountRecoveryActivity"
        binding.forgotPasswordButton.setOnClickListener {
            // Placeholder for future sprint
            Toast.makeText(this, "Account Recovery coming soon", Toast.LENGTH_SHORT).show()

            // Uncomment when created:
            // val intent = Intent(this, AccountRecoveryActivity::class.java)
            // startActivity(intent)
        }
    }

    /**
     * Subscribes to LiveData exposed by [AuthViewModel].
     * Observes two streams: loading state (disables buttons during network calls)
     * and login result (navigates on success, shows Toast on failure).
     *
     * TODO: Add a ProgressBar/spinner for visual loading feedback.
     */
    private fun setupObservers() {
        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            // Disable buttons to prevent double-clicks while loading
            binding.loginButton.isEnabled = !isLoading
            binding.createAccountButton.isEnabled = !isLoading
        }

        // Observe login result
        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                // Requirement: "Brought into the home page of the app"
                val intent = Intent(this, MainActivity::class.java)
                // Clear the back stack so pressing "Back" from Home doesn't return to Login
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish() // Destroy Login Activity so Back button exits app
            }

            result.onFailure { error ->
                // Requirement: Show specific error messages ("Wrong Password" or "Locked")
                // The ViewModel has already formatted the message string.
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}