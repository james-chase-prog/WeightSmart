package com.example.weightsmart.ui.activities.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weightsmart.R
import com.example.weightsmart.domain.usecase.LoginUseCase
import com.example.weightsmart.domain.usecase.RegisterUserUseCase
import com.example.weightsmart.domain.usecase.SetGoalUseCase
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    @Inject lateinit var registerUser: RegisterUserUseCase
    @Inject lateinit var setGoal: SetGoalUseCase
    @Inject lateinit var login: LoginUseCase

    private var usernameEt: TextInputEditText? = null
    private var emailEt: TextInputEditText? = null
    private var passwordEt: TextInputEditText? = null
    private var goalWeightEt: TextInputEditText? = null
    private var createBtn: MaterialButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Your layout name (you already have this file)
        setContentView(R.layout.activity_create_account)

        // Expected IDs inside activity_create_account.xml:
        // usernameInput, emailInput, passwordInput, inputGoalWeight, createAccountButton
        usernameEt = findViewById(R.id.usernameInput)
        emailEt = findViewById(R.id.emailInput)
        passwordEt = findViewById(R.id.passwordInput)
        goalWeightEt = findViewById(R.id.inputGoalWeight)
        createBtn = findViewById(R.id.createAccountButton)

        // If any are missing, fail fast with a helpful message
        if (usernameEt == null || emailEt == null || passwordEt == null || goalWeightEt == null || createBtn == null) {
            Toast.makeText(
                this,
                "Registration layout is missing required views. Ensure IDs: usernameInput, emailInput, passwordInput, goalWeightInput, createAccountButton",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        createBtn!!.setOnClickListener { doRegister() }
    }

    private fun doRegister() {
        val username = usernameEt!!.text?.toString()?.trim().orEmpty()
        val email = emailEt!!.text?.toString()?.trim().orEmpty()
        val pwChars = passwordEt!!.text?.toString()?.toCharArray() ?: CharArray(0)
        val goalText = goalWeightEt!!.text?.toString()?.trim().orEmpty()

        if (username.isEmpty() || email.isEmpty() || pwChars.isEmpty() || goalText.isEmpty()) {
            Toast.makeText(this, "Please fill username, email, password, and goal weight.", Toast.LENGTH_SHORT).show()
            return
        }

        val goalLb = goalText.toDoubleOrNull()
        if (goalLb == null) {
            Toast.makeText(this, "Goal weight must be a number (e.g., 175.0).", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // 1) Create user
            val reg = registerUser(username = username, email = email, password = pwChars)
            reg.onFailure { e ->
                Toast.makeText(this@RegisterActivity, e.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                return@launch
            }
            val userId = reg.getOrNull()!!

            // 2) Set initial goal (single-record-per-user; replaces if exists)
            try {
                setGoal(userId = userId, goalLb = goalLb, goalDate = null, notes = null)
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Account created, but setting goal failed: ${e.message}", Toast.LENGTH_LONG).show()
                // continue to login anyway
            }

            // 3) Auto-login and go to Main
            val loginRes = login(username, /* re-use same pw */ pwChars)
            loginRes.onSuccess {
                startActivity(Intent(this@RegisterActivity, com.example.weightsmart.MainActivity::class.java))
                finish()
            }.onFailure { e ->
                Toast.makeText(this@RegisterActivity, "Account created. Please log in: ${e.message ?: ""}", Toast.LENGTH_LONG).show()
                // Fall back to Login screen
                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}
