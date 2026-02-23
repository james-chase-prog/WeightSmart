package com.example.weightsmart.ui.activities

import android.content.Intent
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weightsmart.R
import com.example.weightsmart.domain.usecase.RegisterUserUseCase
import com.example.weightsmart.domain.usecase.SetGoalUseCase
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class CreateAccountActivity : AppCompatActivity() {

    @Inject lateinit var registerUser: RegisterUserUseCase
    @Inject lateinit var setGoal: SetGoalUseCase

    private lateinit var usernameEt: TextInputEditText
    private lateinit var passwordEt: TextInputEditText
    private lateinit var nicknameEt: TextInputEditText
    private lateinit var emailEt: TextInputEditText
    private lateinit var goalWeightEt: TextInputEditText
    private lateinit var goalDateEt: TextInputEditText
    private lateinit var createBtn: MaterialButton

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        usernameEt   = findViewById(R.id.usernameInput)
        passwordEt   = findViewById(R.id.passwordInput)
        nicknameEt   = findViewById(R.id.nicknameInput)
        emailEt      = findViewById(R.id.emailInput)
        goalWeightEt = findViewById(R.id.inputGoalWeight)
        goalDateEt   = findViewById(R.id.goalDateInput)
        createBtn    = findViewById(R.id.createAccountButton)

        goalDateEt.setOnClickListener { showDatePicker() }
        createBtn.setOnClickListener { doRegister() }
    }

    private fun showDatePicker() {
        val today = LocalDate.now()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val mm = (m + 1).toString().padStart(2, '0')
                val dd = d.toString().padStart(2, '0')
                goalDateEt.setText("$mm/$dd/$y")
            },
            today.year, today.monthValue - 1, today.dayOfMonth
        ).show()
    }

    private fun parseGoalDateEpochSecOrNull(): Long? {
        val txt = goalDateEt.text?.toString()?.trim().orEmpty()
        if (txt.isEmpty()) return null
        return try {
            val ld = LocalDate.parse(txt, dateFmt)
            ld.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        } catch (_: Exception) {
            null
        }
    }

    private fun doRegister() {
        val username = usernameEt.text?.toString()?.trim().orEmpty()
        val email    = emailEt.text?.toString()?.trim().orEmpty()
        val nickname = nicknameEt.text?.toString()?.trim()?.ifBlank { null }
        val pwChars  = passwordEt.text?.toString()?.toCharArray() ?: CharArray(0)
        val goalStr  = goalWeightEt.text?.toString()?.trim().orEmpty()

        if (username.isEmpty() || email.isEmpty() || pwChars.isEmpty() || goalStr.isEmpty()) {
            Toast.makeText(this, "Fill username, email, password, and goal weight.", Toast.LENGTH_SHORT).show()
            return
        }

        val goalLb = goalStr.toDoubleOrNull()
        if (goalLb == null) {
            Toast.makeText(this, "Goal weight must be a number (e.g., 175.0).", Toast.LENGTH_SHORT).show()
            return
        }

        val goalDateEpoch = parseGoalDateEpochSecOrNull()

        lifecycleScope.launch {
            // 1) Create the user
            val reg = registerUser(username = username, email = email, password = pwChars, nickname = nickname)
            reg.onFailure { e ->
                Toast.makeText(this@CreateAccountActivity, e.message ?: "Registration failed.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val userId = reg.getOrNull()!!

            // 2) Set the initial goal (single-record-per-user)
            try {
                setGoal(
                    userId = userId,
                    goalLb = goalLb,
                    goalDate = goalDateEpoch?.let { java.time.Instant.ofEpochSecond(it) }
                )
            } catch (e: Exception) {
                Toast.makeText(this@CreateAccountActivity, "Account created, but setting goal failed: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // 3) Go to NotificationActivity (SMS opt-in). After that, user returns to Login.
            val i = Intent(this@CreateAccountActivity, NotificationActivity::class.java).apply {
                putExtra(NotificationActivity.EXTRA_USER_ID, userId)
                putExtra(NotificationActivity.EXTRA_RETURN_TO, NotificationActivity.RETURN_TO_LOGIN)
            }
            startActivity(i)
            finish() // remove CreateAccount from back stack
        }
    }
}
