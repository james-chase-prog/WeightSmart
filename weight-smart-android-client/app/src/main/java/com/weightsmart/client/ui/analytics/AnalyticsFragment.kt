package com.weightsmart.client.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButtonToggleGroup
import com.weightsmart.client.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * AnalyticsFragment
 * The weight analytics / graph screen showing trend chart, time period filters, and stats cards.
 *
 * Architecture Role:
 * Thin view layer in the MVVM pattern. Observes [AnalyticsViewModel] for reactive state updates.
 * Contains zero business logic -- all data operations (API calls, BMI calculation, moving average)
 * are delegated to the ViewModel.
 *
 * Observation Pattern:
 * - [observeState]: Collects [AnalyticsViewModel.uiState] (StateFlow) to update chart, stats, loading.
 * - [observeEvents]: Collects [AnalyticsViewModel.events] (Channel as Flow) for one-shot error Toasts.
 *
 * Chart Configuration:
 * - Weight data: solid line, colorPrimary, small circle markers.
 * - Moving average: dashed line, colorTertiary, no circle markers.
 * - Goal weight: horizontal LimitLine on the left Y-axis.
 * - Goal star: ImageView positioned at goal date/weight intersection.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-18
 */
@AndroidEntryPoint
class AnalyticsFragment : Fragment(R.layout.fragment_graph) {

    private val viewModel: AnalyticsViewModel by viewModels()

    // --- View References ---
    private lateinit var chart: LineChart
    private lateinit var goalStar: ImageView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var bmiValue: TextView
    private lateinit var totalChangeValue: TextView
    private lateinit var weeklyChangeValue: TextView

    // Chart colors resolved from Material 3 theme (colors.xml)
    private var colorPrimary = Color.BLUE
    private var colorTertiary = Color.GRAY

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chart = view.findViewById(R.id.weight_line_chart)
        goalStar = view.findViewById(R.id.goal_star)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        toggleGroup = view.findViewById(R.id.period_toggle_group)
        bmiValue = view.findViewById(R.id.stat_bmi_value)
        totalChangeValue = view.findViewById(R.id.stat_total_change_value)
        weeklyChangeValue = view.findViewById(R.id.stat_weekly_change_value)

        colorPrimary = ContextCompat.getColor(requireContext(), R.color.md_theme_primary)
        colorTertiary = ContextCompat.getColor(requireContext(), R.color.md_theme_tertiary)

        setupChart()
        setupButtons()
        observeState()
        observeEvents()

        // Default selection: MONTH
        toggleGroup.check(R.id.btn_month)
    }

    // --- Chart Setup ---

    /**
     * Configures the MPAndroidChart LineChart appearance.
     * Disables description, configures axes, enables touch interactions.
     */
    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setDrawGridBackground(false)
            setPinchZoom(true)
            isDragEnabled = true
            setScaleEnabled(true)

            // X-axis: dates at the bottom
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                valueFormatter = EpochDayFormatter()
            }

            // Left Y-axis: weight values
            axisLeft.apply {
                setDrawGridLines(true)
                granularity = 1f
            }

            // Right Y-axis: disabled
            axisRight.isEnabled = false

            // Animation on first load
            animateX(500)
        }
    }

    // --- Button Setup ---

    /**
     * Wires the MaterialButtonToggleGroup to the ViewModel's selectPeriod() method.
     * Maps each button ID to the corresponding TimePeriod enum value.
     */
    private fun setupButtons() {
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val period = when (checkedId) {
                R.id.btn_week -> TimePeriod.WEEK
                R.id.btn_month -> TimePeriod.MONTH
                R.id.btn_year -> TimePeriod.YEAR
                R.id.btn_five_year -> TimePeriod.FIVE_YEAR
                R.id.btn_lifetime -> TimePeriod.LIFETIME
                else -> return@addOnButtonCheckedListener
            }
            viewModel.selectPeriod(period)
        }
    }

    // --- State Observation ---

    /**
     * Collects [AnalyticsViewModel.uiState] StateFlow to reactively update:
     * - Chart data sets (weight line + moving average trend)
     * - Goal weight LimitLine
     * - Goal star positioning
     * - Stats cards (BMI, total change, change/week)
     * - Loading indicator visibility
     */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Loading indicator
                    loadingIndicator.isVisible = state.isLoading

                    // Stats cards
                    bmiValue.text = state.bmi?.let { String.format("%.1f", it) }
                        ?: getString(R.string.bmi_unavailable)
                    totalChangeValue.text = state.totalChange?.let { String.format("%+.1f lbs", it) }
                        ?: getString(R.string.bmi_unavailable)
                    weeklyChangeValue.text = state.weeklyChange?.let { String.format("%+.1f lbs/wk", it) }
                        ?: getString(R.string.bmi_unavailable)

                    // Update chart data
                    updateChart(state)

                    // Goal weight LimitLine
                    updateGoalLine(state.goalWeight)

                    // Goal star positioning
                    updateGoalStar(state)
                }
            }
        }
    }

    /**
     * Collects one-shot error events from the ViewModel.
     */
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AnalyticsEvent.Error ->
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- Chart Rendering ---

    /**
     * Creates and applies two LineDataSets to the chart:
     * 1. Weight data: solid colorPrimary line with small circle markers.
     * 2. Moving average: dashed colorTertiary line with no circles.
     */
    private fun updateChart(state: AnalyticsUiState) {
        if (state.chartEntries.isEmpty()) {
            chart.clear()
            return
        }

        val dataSets = mutableListOf<LineDataSet>()

        // Weight data line
        val weightDataSet = LineDataSet(state.chartEntries, "Weight").apply {
            color = colorPrimary
            setCircleColor(colorPrimary)
            circleRadius = 3f
            lineWidth = 2f
            setDrawValues(false)
            setDrawCircleHole(false)
        }
        dataSets.add(weightDataSet)

        // Moving average trend line
        if (state.movingAverageEntries.isNotEmpty()) {
            val maDataSet = LineDataSet(state.movingAverageEntries, "Trend").apply {
                color = colorTertiary
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                enableDashedLine(10f, 5f, 0f)
            }
            dataSets.add(maDataSet)
        }

        chart.data = LineData(dataSets.toList())
        chart.invalidate()
    }

    /**
     * Adds or removes a horizontal LimitLine on the left Y-axis at the goal weight.
     */
    private fun updateGoalLine(goalWeight: Float?) {
        chart.axisLeft.removeAllLimitLines()

        if (goalWeight != null) {
            val limitLine = LimitLine(goalWeight, "Goal").apply {
                lineWidth = 1f
                lineColor = colorTertiary
                enableDashedLine(10f, 10f, 0f)
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                textSize = 10f
                textColor = colorTertiary
            }
            chart.axisLeft.addLimitLine(limitLine)
        }
    }

    /**
     * Positions the gold star ImageView at the intersection of goal date and goal weight.
     * The star is only visible when the goal date falls within the chart's x-axis range.
     */
    private fun updateGoalStar(state: AnalyticsUiState) {
        val goalWeight = state.goalWeight
        val goalEpochDay = state.goalDateEpochDay

        if (goalWeight == null || goalEpochDay == null || state.chartEntries.isEmpty()) {
            goalStar.isVisible = false
            return
        }

        // Check if goal date is within chart range
        if (goalEpochDay < state.xAxisMinEpochDay || goalEpochDay > state.xAxisMaxEpochDay) {
            goalStar.isVisible = false
            return
        }

        // Use MPAndroidChart's transformer to convert data coordinates to pixel coordinates
        chart.post {
            val transformer = chart.getTransformer(chart.axisLeft.axisDependency)
            val pts = floatArrayOf(goalEpochDay.toFloat(), goalWeight)
            transformer.pointValuesToPixel(pts)

            // Offset by half the star size to center it on the point
            val starHalf = goalStar.width / 2f
            goalStar.translationX = pts[0] - starHalf + chart.left
            goalStar.translationY = pts[1] - starHalf + chart.top
            goalStar.isVisible = true
        }
    }

    // --- Value Formatter ---

    /**
     * Converts epoch day float values back to human-readable date labels for the X-axis.
     * Format adapts to period: short month + day for most periods.
     */
    private class EpochDayFormatter : ValueFormatter() {
        private val formatter = DateTimeFormatter.ofPattern("MMM d")

        override fun getFormattedValue(value: Float): String {
            return try {
                LocalDate.ofEpochDay(value.toLong()).format(formatter)
            } catch (_: Exception) {
                ""
            }
        }
    }
}
