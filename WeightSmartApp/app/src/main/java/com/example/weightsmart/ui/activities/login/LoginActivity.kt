package com.example.weightsmart.ui.activities.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weightsmart.R
import com.example.weightsmart.domain.usecase.LoginUseCase
import com.example.weightsmart.ui.activities.CreateAccountActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject lateinit var loginUseCase: LoginUseCase

    private lateinit var usernameEt: TextInputEditText
    private lateinit var passwordEt: TextInputEditText
    private lateinit var loginBtn: MaterialButton
    private lateinit var createBtn: MaterialButton
    private lateinit var forgotBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // activity layout

        usernameEt = findViewById(R.id.usernameInput)
        passwordEt = findViewById(R.id.passwordInput)
        loginBtn   = findViewById(R.id.loginButton)
        createBtn  = findViewById(R.id.createAccountButton)
        forgotBtn  = findViewById(R.id.forgotPasswordButton)

        loginBtn.setOnClickListener { doLogin() }

        // Launch CreateAccountActivity in ui.activities
        createBtn.setOnClickListener {
            startActivity(Intent(this, CreateAccountActivity::class.java))
        }

        forgotBtn.setOnClickListener {
            Toast.makeText(this, "Forgot password flow not implemented yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun doLogin() {
        val username = usernameEt.text?.toString()?.trim().orEmpty()
        val pwChars  = passwordEt.text?.toString()?.toCharArray() ?: CharArray(0)

        if (username.isEmpty() || pwChars.isEmpty()) {
            Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val res = loginUseCase(username, pwChars) // clears pwChars inside the use case
            res.onSuccess {
                // Login is ephemeral: proceed to Main and finish
                startActivity(Intent(this@LoginActivity, com.example.weightsmart.MainActivity::class.java))
                finish()
            }.onFailure { e ->
                Toast.makeText(
                    this@LoginActivity,
                    e.message ?: "Login failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

