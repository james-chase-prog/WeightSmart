package com.weightsmart.client.ui.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.weightsmart.client.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * HomeFragment
 * The main dashboard screen showing current weight, goal weight, and a weight entry form.
 *
 * Architecture Role:
 * Thin view layer in the MVVM pattern. Observes [HomeViewModel] for reactive state updates
 * using the repeatOnLifecycle + StateFlow/Channel pattern. Contains zero business logic --
 * all data operations (weight submission, goal checking) are delegated to the ViewModel.
 *
 * Observation Pattern:
 * - [observeState]: Collects [HomeViewModel.uiState] (StateFlow) to update userName, currentWeight, goalWeight.
 * - [observeEvents]: Collects [HomeViewModel.events] (Channel as Flow) for one-shot UI effects
 *   (Toast on WeightAdded, GoalReached celebration, Error messages).
 *
 * Key Concepts & Documentation:
 * repeatOnLifecycle: Safely collects Flows only when the Fragment is in the STARTED state;
 *   automatically cancels collection when the Fragment stops.
 * <a href="https://developer.android.com/topic/libraries/architecture/coroutines#repeatonlifecycle">Reference: repeatOnLifecycle</a>
 * Channel events: One-shot events that are consumed exactly once (unlike StateFlow which replays).
 * <a href="https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/-channel/">Reference: Channel</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    //Lazy-injected ViewModel; scoped to this Fragment's lifecycle.
    private val viewModel: HomeViewModel by viewModels()

    // --- View References (bound manually via findViewById in onViewCreated) ---
    private lateinit var userNameDisplay: TextView
    private lateinit var currentWeightText: TextView
    private lateinit var goalWeightText: TextView
    private lateinit var inputWeight: TextView
    private lateinit var inputDate: TextView
    private lateinit var inputTime: TextView
    private lateinit var updateBtn: MaterialButton

    // Display format for date fields (MM/dd/yyyy).
    private val dateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    //Display format for time fields (hh:mm a, 12-hour with AM/PM).
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

        // Prefill date/time with "now"
        val now = java.time.Instant.now().atZone(ZoneId.systemDefault())
        inputDate.text = now.toLocalDate().format(dateFmt)
        inputTime.text = now.toLocalTime().format(timeFmt)
        inputWeight.text = "000.0"

        inputWeight.setOnClickListener { promptForWeight() }
        inputDate.setOnClickListener { showDatePicker() }
        inputTime.setOnClickListener { showTimePicker() }
        updateBtn.setOnClickListener { submitWeight() }

        observeState()
        observeEvents()
    }

    // --- State Observation ---

    /**
     * Collects [HomeViewModel.uiState] StateFlow to reactively update display fields.
     * Uses repeatOnLifecycle(STARTED) so collection pauses when the Fragment is stopped.
     */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    userNameDisplay.text = state.userName
                    currentWeightText.text = state.currentWeight
                    goalWeightText.text = state.goalWeight
                }
            }
        }
    }

    /**
     * Collects [HomeViewModel.events] Channel for one-shot UI effects.
     * Each event is consumed exactly once: WeightAdded -> Toast, GoalReached -> celebration Toast,
     * Error -> error Toast.
     */
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is HomeEvent.WeightAdded ->
                            Toast.makeText(requireContext(), "Weight updated.", Toast.LENGTH_SHORT).show()
                        is HomeEvent.GoalReached ->
                            Toast.makeText(requireContext(), "Congratulations! Goal reached!", Toast.LENGTH_LONG).show()
                        is HomeEvent.Error ->
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- Click Delegation ---

    /**
     * Reads weight, date, and time from the input fields, validates them,
     * and delegates to [HomeViewModel.commitWeight] with a combined Instant timestamp.
     */
    private fun submitWeight() {
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

        val measuredAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
        viewModel.commitWeight(weightVal, measuredAt)
    }

    // --- UI Helpers (view-only, no data logic) ---

    /**
     * Shows an AlertDialog with a decimal number input for entering weight in pounds.
     * Result is written to the [inputWeight] TextView for later submission.
     */
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

    //Opens a DatePickerDialog pre-filled with the currently displayed date. */
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

    // Opens a TimePickerDialog pre-filled with the currently displayed time (12-hour format).
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
}
