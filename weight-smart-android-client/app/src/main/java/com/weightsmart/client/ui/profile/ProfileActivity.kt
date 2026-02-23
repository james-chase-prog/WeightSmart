package com.weightsmart.client.ui.profile

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.weightsmart.client.R
import com.weightsmart.client.ui.auth.LoginActivity
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ProfileActivity
 * User profile editing screen for nickname, goal weight, goal date, and avatar.
 *
 * Architecture Role:
 * Thin view layer following the MVVM pattern established in [HomeFragment] and [TableFragment].
 * All business logic lives in [ProfileViewModel]. This Activity only:
 * - Binds views and wires click listeners
 * - Observes [ProfileViewModel.uiState] for display updates
 * - Observes [ProfileViewModel.events] for one-shot UI effects (Toast, finish)
 *
 * @author James Chase
 * @version 2.0
 * @since 2026-01-20
 */
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    // --- View References ---
    private lateinit var headerTv: TextView
    private lateinit var nicknameEt: TextInputEditText
    private lateinit var ageEt: TextInputEditText
    private lateinit var heightEt: TextInputEditText
    private lateinit var goalWeightEt: TextInputEditText
    private lateinit var goalDateEt: TextInputEditText
    private lateinit var avatarBtn: ImageButton
    private lateinit var saveBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var exitBtn: Button

    // Guards against overwriting user edits when userFlow re-emits
    private var fieldsPopulated = false

    /**
     * Registered Activity Result callback for the image picker.
     * On selection, shows a preview on the avatar button (storage pipeline deferred to P6).
     */
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            avatarBtn.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        headerTv     = findViewById(R.id.profile_header)
        nicknameEt   = findViewById(R.id.nicknameField)
        ageEt        = findViewById(R.id.ageField)
        heightEt     = findViewById(R.id.heightField)
        goalWeightEt = findViewById(R.id.goalWeightField)
        goalDateEt   = findViewById(R.id.goalDateField)
        avatarBtn    = findViewById(R.id.avatar_button)
        saveBtn      = findViewById(R.id.save_btn)
        logoutBtn    = findViewById(R.id.logout_btn)
        exitBtn      = findViewById(R.id.exit_btn)

        // Update header live as user types nickname
        nicknameEt.addTextChangedListener { editable ->
            val name = editable?.toString()?.takeIf { it.isNotBlank() }
            setHeader(name ?: headerTv.tag?.toString().orEmpty())
        }

        // Date picker
        goalDateEt.setOnClickListener { showDatePicker() }

        // Avatar choose skeleton (no storage/validation yet)
        avatarBtn.setOnClickListener { pickImage.launch("image/*") }

        // Save commits updates then exits via event
        saveBtn.setOnClickListener {
            viewModel.saveProfile(
                nickname = nicknameEt.text?.toString() ?: "",
                ageStr = ageEt.text?.toString() ?: "",
                heightStr = heightEt.text?.toString() ?: "",
                goalWeightStr = goalWeightEt.text?.toString() ?: "",
                goalDateStr = goalDateEt.text?.toString() ?: ""
            )
        }

        // Logout ends the session and returns to login
        logoutBtn.setOnClickListener { viewModel.logout() }

        // Exit discards
        exitBtn.setOnClickListener { finish() }

        observeState()
        observeEvents()
    }

    /**
     * Observes [ProfileViewModel.uiState] and updates the UI.
     * Only prefills EditText fields on the first emission to avoid overwriting user edits.
     */
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    setHeader(state.headerName)
                    if (!fieldsPopulated && state.headerName.isNotEmpty()) {
                        nicknameEt.setText(state.nickname)
                        ageEt.setText(state.age)
                        heightEt.setText(state.height)
                        goalWeightEt.setText(state.goalWeight)
                        goalDateEt.setText(state.goalDate)
                        fieldsPopulated = true
                    }
                    saveBtn.isEnabled = !state.isSaving
                }
            }
        }
    }

    /**
     * Observes [ProfileViewModel.events] for one-shot effects.
     * [ProfileEvent.SaveSuccess] shows a Toast and finishes the Activity.
     * [ProfileEvent.SaveError] shows an error Toast.
     */
    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ProfileEvent.SaveSuccess -> {
                            Toast.makeText(this@ProfileActivity, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        is ProfileEvent.SaveError -> {
                            Toast.makeText(this@ProfileActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                        is ProfileEvent.LoggedOut -> {
                            Toast.makeText(this@ProfileActivity, getString(R.string.logout_confirm), Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the greeting header text. Stores the original display name in the View's tag
     * so it can be used as a fallback when the nickname field is cleared.
     */
    private fun setHeader(displayName: String) {
        if (headerTv.tag == null) headerTv.tag = displayName
        headerTv.text = "Hello ${displayName.ifBlank { "User" }}!"
    }

    /** Opens a DatePickerDialog pre-filled with today's date for goal date selection. */
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
}
