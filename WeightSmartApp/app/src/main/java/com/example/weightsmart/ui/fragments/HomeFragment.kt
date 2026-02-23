package com.example.weightsmart.ui.fragments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.weightsmart.R
import com.example.weightsmart.core.session.SessionManager
import com.example.weightsmart.domain.usecase.AddWeightUseCase
import com.example.weightsmart.domain.usecase.GetGoalUseCase
import com.example.weightsmart.domain.usecase.GetUserUseCase
import com.example.weightsmart.notification.NotificationScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    @Inject lateinit var session: SessionManager
    @Inject lateinit var getUser: GetUserUseCase
    @Inject lateinit var getGoal: GetGoalUseCase
    @Inject lateinit var addWeight: AddWeightUseCase
    @Inject lateinit var scheduler: NotificationScheduler

    private lateinit var userNameDisplay: TextView
    private lateinit var currentWeightText: TextView
    private lateinit var goalWeightText: TextView
    private lateinit var inputWeight: TextView
    private lateinit var inputDate: TextView
    private lateinit var inputTime: TextView
    private lateinit var updateBtn: MaterialButton

    private val dateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userNameDisplay   = view.findViewById(R.id.userNameDisplay)
        currentWeightText = view.findViewById(R.id.currentWeightText)
        goalWeightText    = view.findViewById(R.id.goalWeightText)
        inputWeight       = view.findViewById(R.id.inputNewWeight)
        inputDate         = view.findViewById(R.id.inputDate)
        inputTime         = view.findViewById(R.id.inputTime)
        updateBtn         = view.findViewById(R.id.updateWeightButton)

        // 3) Prefill date/time with "now" (user can edit)
        val now = Instant.now().atZone(ZoneId.systemDefault())
        inputDate.text = now.toLocalDate().format(dateFmt)
        inputTime.text = now.toLocalTime().format(timeFmt)
        inputWeight.text = "000.0" // placeholder until user taps to enter

        inputWeight.setOnClickListener { promptForWeight() }
        inputDate.setOnClickListener { showDatePicker() }
        inputTime.setOnClickListener { showTimePicker() }
        updateBtn.setOnClickListener { commitWeight() }

        refreshOverview()
    }

    // ---- UI helpers ----

    private fun promptForWeight() {
        val ctx = requireContext()
        val et = TextInputEditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(inputWeight.text?.toString() ?: "000.0")
            setSelection(text?.length ?: 0)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Enter weight (lb)")
            .setView(et)
            .setPositiveButton("OK") { d, _ ->
                val v = et.text?.toString()?.trim().orEmpty()
                inputWeight.text = v.ifBlank { "000.0" }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePicker() {
        val cur = runCatching { LocalDate.parse(inputDate.text.toString(), dateFmt) }
            .getOrDefault(LocalDate.now())
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val mm = (m + 1).toString().padStart(2, '0')
                val dd = d.toString().padStart(2, '0')
                inputDate.text = "$mm/$dd/$y"
            },
            cur.year, cur.monthValue - 1, cur.dayOfMonth
        ).show()
    }

    private fun showTimePicker() {
        val cur = runCatching {
            LocalTime.parse(inputTime.text.toString(), timeFmt)
        }.getOrDefault(LocalTime.now())
        TimePickerDialog(
            requireContext(),
            { _, h, min ->
                inputTime.text = LocalTime.of(h, min).format(timeFmt)
            },
            cur.hour, cur.minute, false
        ).show()
    }

    // ---- Data flows ----

    private fun refreshOverview() {
        viewLifecycleOwner.lifecycleScope.launch {
            val s = session.getSession() ?: return@launch

            // 1) Welcome text: nickname → username
            val u = getUser(s.userId)
            val name = u?.nickname?.takeIf { it.isNotBlank() } ?: u?.username ?: "User"
            userNameDisplay.text = name

            // 2) Current & Goal weights from storage
            currentWeightText.text =
                u?.currentWeight?.let { String.format("%.1f", it) } ?: "000.0"

            val g = getGoal(s.userId)
            goalWeightText.text =
                g?.valueLb?.let { String.format("%.1f", it) } ?: "000.0"
        }
    }

    private fun commitWeight() {
        val weightVal = inputWeight.text?.toString()?.trim()?.toDoubleOrNull()
        if (weightVal == null) {
            Toast.makeText(requireContext(), "Enter a valid weight (e.g., 184.6)", Toast.LENGTH_SHORT).show()
            return
        }

        val date = runCatching { LocalDate.parse(inputDate.text.toString(), dateFmt) }.getOrNull()
        val time = runCatching { LocalTime.parse(inputTime.text.toString(), timeFmt) }.getOrNull()
        if (date == null || time == null) {
            Toast.makeText(requireContext(), "Pick a valid date and time", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val uid = session.getSession()?.userId
            if (uid == null) {
                Toast.makeText(requireContext(), "Not logged in.", Toast.LENGTH_LONG).show()
                return@launch
            }

            val measuredAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()

            // 4) Insert into weight table; repo updates user's currentWeight snapshot atomically
            runCatching { addWeight(uid, weightVal, measuredAt) }
                .onSuccess {
                    // optional: trigger celebration if goal hit/passed
                    //scheduler.onWeightRecorded(uid)

                    // refresh the overview (reads from user snapshot + goal table)
                    refreshOverview()

                    Toast.makeText(requireContext(), "Weight updated.", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(requireContext(), it.message ?: "Failed to add weight.", Toast.LENGTH_LONG).show()
                }
        }
    }
}
