package com.example.weightsmart.ui.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.weightsmart.R
import com.example.weightsmart.core.session.SessionManager
import com.example.weightsmart.domain.usecase.GetGoalUseCase
import com.example.weightsmart.domain.usecase.GetUserUseCase
import com.example.weightsmart.domain.usecase.SetGoalUseCase
import com.example.weightsmart.domain.usecase.UpdateProfileUseCase
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    @Inject lateinit var session: SessionManager
    @Inject lateinit var getUser: GetUserUseCase
    @Inject lateinit var updateProfile: UpdateProfileUseCase
    @Inject lateinit var getGoal: GetGoalUseCase
    @Inject lateinit var setGoal: SetGoalUseCase

    private lateinit var headerTv: TextView
    private lateinit var nicknameEt: TextInputEditText
    private lateinit var goalWeightEt: TextInputEditText
    private lateinit var goalDateEt: TextInputEditText
    private lateinit var avatarBtn: ImageButton
    private lateinit var smsBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var exitBtn: Button
    private lateinit var lightBtn: Button
    private lateinit var darkBtn: Button

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private var pendingAvatarUri: String? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingAvatarUri = uri.toString()
            avatarBtn.setImageURI(uri) // preview only; storage pipeline will come later
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        headerTv     = findViewById(R.id.profile_header)
        nicknameEt   = findViewById(R.id.nicknameField)
        goalWeightEt = findViewById(R.id.goalWeightField)
        goalDateEt   = findViewById(R.id.goalDateField)
        avatarBtn    = findViewById(R.id.avatar_button)
        smsBtn       = findViewById(R.id.opt_in_sms_button)
        saveBtn      = findViewById(R.id.save_btn)
        exitBtn      = findViewById(R.id.exit_btn)
        lightBtn     = findViewById(R.id.light_mode_btn)
        darkBtn      = findViewById(R.id.dark_mode_btn)

        // Load current data
        lifecycleScope.launch {
            val s = session.getSession()
            if (s == null) {
                Toast.makeText(this@ProfileActivity, "Not logged in.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val user = getUser(s.userId)
            if (user == null) {
                Toast.makeText(this@ProfileActivity, "User not found.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            // Header shows nickname or username
            setHeader(user.nickname ?: user.username)

            // Prefill nickname
            user.nickname?.let { nicknameEt.setText(it) }

            // Prefill avatar preview if you store URIs in avatar
            if (!user.avatar.isNullOrBlank() && user.avatar != "avatar_default") {
                runCatching { avatarBtn.setImageURI(Uri.parse(user.avatar)) }
            }

            // Prefill goal
            val goal = getGoal(s.userId)
            if (goal != null) {
                goalWeightEt.setText(goal.valueLb.toString())
                goal.goalDate?.let { gd ->
                    val ld = LocalDate.ofInstant(gd, ZoneId.systemDefault())
                    goalDateEt.setText(ld.format(dateFmt))
                }
            }
        }

        // Update header live as user types nickname
        nicknameEt.addTextChangedListener { editable ->
            val name = editable?.toString()?.takeIf { it.isNotBlank() }
            setHeader(name ?: headerTv.tag?.toString().orEmpty())
        }

        // Date picker
        goalDateEt.setOnClickListener { showDatePicker() }

        // Avatar choose skeleton (no storage/validation yet)
        avatarBtn.setOnClickListener { pickImage.launch("image/*") }

        // Theme toggle skeleton (store preference; full theming in later release)
        lightBtn.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            saveThemePref(false)
            Toast.makeText(this, "Light mode selected (preview).", Toast.LENGTH_SHORT).show()
        }
        darkBtn.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            saveThemePref(true)
            Toast.makeText(this, "Dark mode selected (preview).", Toast.LENGTH_SHORT).show()
        }

        // Launch NotificationActivity; return to Profile on completion
        smsBtn.setOnClickListener {
            lifecycleScope.launch {
                val uid = session.getSession()?.userId ?: return@launch
                val i = Intent(this@ProfileActivity, NotificationActivity::class.java).apply {
                    putExtra(NotificationActivity.EXTRA_USER_ID, uid)
                    putExtra(NotificationActivity.EXTRA_RETURN_TO, NotificationActivity.RETURN_TO_PROFILE)
                }
                startActivity(i)
            }
        }

        // Save commits updates then exits
        saveBtn.setOnClickListener { doSaveAndExit() }

        // Exit discards
        exitBtn.setOnClickListener { finish() }
    }

    private fun setHeader(displayName: String) {
        // Keep username in tag for fallback
        if (headerTv.tag == null) headerTv.tag = displayName
        headerTv.text = "Hello ${displayName.ifBlank { "User" }}!"
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

    private fun parseGoalDateOrNull(): Instant? {
        val txt = goalDateEt.text?.toString()?.trim().orEmpty()
        if (txt.isEmpty()) return null
        return try {
            val ld = LocalDate.parse(txt, dateFmt)
            ld.atStartOfDay(ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {
            null
        }
    }

    private fun doSaveAndExit() {
        val nickname = nicknameEt.text?.toString()?.trim()?.ifBlank { null }
        val goalStr  = goalWeightEt.text?.toString()?.trim().orEmpty()
        val goalLb   = goalStr.toDoubleOrNull()
        val goalDate = parseGoalDateOrNull()

        if (goalStr.isNotEmpty() && goalLb == null) {
            Toast.makeText(this, "Goal weight must be a number (e.g., 175.0).", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val s = session.getSession()
            if (s == null) {
                Toast.makeText(this@ProfileActivity, "Not logged in.", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 1) Update profile lightweight fields (nickname + avatar placeholder)
            runCatching {
                updateProfile(
                    userId = s.userId,
                    nickname = nickname,
                    avatar = pendingAvatarUri // null means keep existing
                )
            }.onFailure {
                Toast.makeText(this@ProfileActivity, "Failed to update profile: ${it.message}", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 2) Update goal if provided
            if (goalLb != null) {
                runCatching {
                    setGoal(
                        userId = s.userId,
                        goalLb = goalLb,
                        goalDate = goalDate
                    )
                }.onFailure {
                    Toast.makeText(this@ProfileActivity, "Profile saved, goal update failed: ${it.message}", Toast.LENGTH_LONG).show()
                    // still finish, since other fields saved
                }
            }

            Toast.makeText(this@ProfileActivity, "Profile updated.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveThemePref(dark: Boolean) {
        val prefs = getSharedPreferences("ws_settings", MODE_PRIVATE)
        prefs.edit().putBoolean("dark_mode", dark).apply()
    }
}
