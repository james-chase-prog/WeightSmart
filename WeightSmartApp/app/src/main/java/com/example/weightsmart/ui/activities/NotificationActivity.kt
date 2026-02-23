package com.example.weightsmart.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weightsmart.R
import com.example.weightsmart.core.session.SessionManager
import com.example.weightsmart.domain.usecase.UpdateSmsPreferenceUseCase
import com.example.weightsmart.ui.activities.login.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationActivity : AppCompatActivity() {

    @Inject lateinit var updateSms: UpdateSmsPreferenceUseCase
    @Inject lateinit var session: SessionManager

    private lateinit var phoneEt: TextInputEditText
    private lateinit var acceptBtn: MaterialButton
    private lateinit var declineBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sms_opt_in)

        phoneEt = findViewById(R.id.phoneNumberEditText)
        acceptBtn = findViewById(R.id.buttonAcceptSms)
        declineBtn = findViewById(R.id.buttonDeclineSms)

        val returnTo = intent.getStringExtra(EXTRA_RETURN_TO) ?: RETURN_TO_LOGIN
        lifecycleScope.launch {
            val userId = resolveUserIdFromIntentOrSession()
            if (userId == null) {
                Toast.makeText(this@NotificationActivity, "No user available for SMS setup.", Toast.LENGTH_LONG).show()
                routeNext(returnTo)
                return@launch
            }

            acceptBtn.setOnClickListener {
                val phone = phoneEt.text?.toString()?.trim().orEmpty()
                if (!isValidPhone(phone)) {
                    Toast.makeText(this@NotificationActivity, "Enter a valid phone number.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    updateSms(userId, phone, true)
                    Toast.makeText(this@NotificationActivity, "SMS notifications enabled.", Toast.LENGTH_SHORT).show()
                    routeNext(returnTo)
                }
            }

            declineBtn.setOnClickListener {
                lifecycleScope.launch {
                    updateSms(userId, null, false)
                    Toast.makeText(this@NotificationActivity, "SMS notifications disabled.", Toast.LENGTH_SHORT).show()
                    routeNext(returnTo)
                }
            }
        }
    }

    private suspend fun resolveUserIdFromIntentOrSession(): Long? {
        val extraId = intent.getLongExtra(EXTRA_USER_ID, -1L)
        if (extraId > 0) return extraId
        return session.getSession()?.userId
    }

    private fun routeNext(returnTo: String) {
        when (returnTo) {
            RETURN_TO_PROFILE -> {
                setResult(RESULT_OK)
                finish() // reveals ProfileActivity underneath
            }
            else -> {
                // Default: return to Login (clear stack so CreateAccount isn't left behind)
                val i = Intent(this, LoginActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(i)
                finish()
            }
        }
    }

    private fun isValidPhone(p: String): Boolean =
        p.matches(Regex("^\\+?[0-9 .\\-()]{7,20}\$"))

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_RETURN_TO = "extra_return_to"
        const val RETURN_TO_LOGIN = "login"
        const val RETURN_TO_PROFILE = "profile"
    }
}
