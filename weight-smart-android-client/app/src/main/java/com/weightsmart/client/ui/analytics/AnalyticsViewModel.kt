package com.weightsmart.client.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.remote.WeightSmartApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * AnalyticsUiState
 * Immutable snapshot of the analytics screen display data.
 *
 * @property chartEntries MPAndroidChart Entry list (x = epochDay, y = weight).
 * @property movingAverageEntries Smoothed trend line data points.
 * @property selectedPeriod Currently active time period filter.
 * @property resolution Server-reported resolution: "RAW", "WEEKLY", or "MONTHLY".
 * @property goalWeight User's goal weight for LimitLine rendering. Null if not set.
 * @property goalDateEpochDay Goal target date as epoch day for star x-position. Null if not set.
 * @property bmi Body Mass Index calculated from current weight and height. Null if height unavailable.
 * @property totalChange Net weight change for the selected period. Null if < 2 records.
 * @property weeklyChange Average change per week. Null if < 2 records.
 * @property isLoading True while an API call is in flight.
 * @property xAxisMinEpochDay Earliest epoch day in the dataset (for chart x-axis bounds).
 * @property xAxisMaxEpochDay Latest epoch day in the dataset (for chart x-axis bounds).
 */
data class AnalyticsUiState(
    val chartEntries: List<Entry> = emptyList(),
    val movingAverageEntries: List<Entry> = emptyList(),
    val selectedPeriod: TimePeriod = TimePeriod.MONTH,
    val resolution: String = "RAW",
    val goalWeight: Float? = null,
    val goalDateEpochDay: Long? = null,
    val bmi: Double? = null,
    val totalChange: Double? = null,
    val weeklyChange: Double? = null,
    val isLoading: Boolean = false,
    val xAxisMinEpochDay: Long = 0,
    val xAxisMaxEpochDay: Long = 0
)

/** Time period options for the filter bar. Maps to server query parameter values. */
enum class TimePeriod { WEEK, MONTH, YEAR, FIVE_YEAR, LIFETIME }

/** One-shot events consumed by [AnalyticsFragment]. */
sealed class AnalyticsEvent {
    data class Error(val message: String) : AnalyticsEvent()
}

/**
 * AnalyticsViewModel
 * Manages the analytics/graph screen state: chart data, time period selection,
 * moving average computation, BMI calculation, and summary statistics.
 *
 * Architecture Role:
 * Hilt-injected ViewModel between [AnalyticsFragment] (View) and the data layer
 * ([WeightSmartApi], [SessionManager]). Uses the StateFlow + Channel pattern
 * consistent with HomeViewModel and TableViewModel.
 *
 * Data Flow Pipeline:
 * 1. init -> hydrate SessionManager -> observe user profile -> load default period (MONTH)
 * 2. selectPeriod() -> loadGraphData() -> API call -> map to Entry list -> compute moving average
 * 3. BMI auto-updates from SessionManager.userFlow (weight change, height update)
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-02-18
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val api: WeightSmartApi,
    private val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState

    private val _events = Channel<AnalyticsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { session.getUser() }
        observeProfile()
        loadGraphData(TimePeriod.MONTH)
    }

    /**
     * Observes user profile changes to update BMI, goalWeight, and goalDate.
     * Triggers on login, profile save, or sync-driven weight changes.
     */
    private fun observeProfile() {
        viewModelScope.launch {
            session.userFlow.filterNotNull().collect { user ->
                val bmi = calculateBmi(user.currentWeight, user.height)
                _uiState.update { current ->
                    current.copy(
                        bmi = bmi,
                        goalWeight = user.goalWeight?.toFloat(),
                        goalDateEpochDay = user.targetDate?.toEpochDay()
                    )
                }
            }
        }
    }

    /**
     * Called by the Fragment when a time period button is tapped.
     * Updates the selected period and triggers a fresh API call.
     *
     * @param period The new time period to display.
     */
    fun selectPeriod(period: TimePeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        loadGraphData(period)
    }

    /**
     * Fetches graph data from the server and maps it to MPAndroidChart entries.
     * Computes a moving average trend line with an adaptive window based on resolution.
     *
     * @param period The time period to fetch data for.
     */
    private fun loadGraphData(period: TimePeriod) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val userId = session.getUserId()
            val token = session.getAuthToken()

            if (userId == -1L || token == null) {
                _events.send(AnalyticsEvent.Error("Not logged in."))
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            runCatching {
                api.getGraphData(userId, "Bearer $token", period.name)
            }.onSuccess { response ->
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!

                    // Map server data points to MPAndroidChart Entry(x=epochDay, y=weight)
                    val entries = body.dataPoints.mapNotNull { point ->
                        val epochDay = parseToEpochDay(point.date) ?: return@mapNotNull null
                        Entry(epochDay.toFloat(), point.weight.toFloat())
                    }.sortedBy { it.x }

                    // Adaptive moving average window based on server resolution
                    val window = when (body.resolution) {
                        "WEEKLY" -> 4
                        "MONTHLY" -> 3
                        else -> 7  // RAW
                    }
                    val maEntries = computeMovingAverage(entries, window)

                    // Chart x-axis bounds
                    val minEpoch = entries.firstOrNull()?.x?.toLong() ?: 0L
                    val maxEpoch = entries.lastOrNull()?.x?.toLong() ?: 0L

                    _uiState.update { current ->
                        current.copy(
                            chartEntries = entries,
                            movingAverageEntries = maEntries,
                            resolution = body.resolution,
                            totalChange = body.totalChange,
                            weeklyChange = body.weeklyChangeRate,
                            isLoading = false,
                            xAxisMinEpochDay = minEpoch,
                            xAxisMaxEpochDay = maxEpoch
                        )
                    }
                } else {
                    _events.send(AnalyticsEvent.Error("Unable to load graph data"))
                    _uiState.update { it.copy(isLoading = false) }
                }
            }.onFailure {
                _events.send(AnalyticsEvent.Error(it.message ?: "Unable to load graph data"))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Computes a simple moving average over the data points.
     * Uses a sliding window that adapts to the data resolution:
     * - RAW: window=7 (weekly smoothing of daily data)
     * - WEEKLY: window=4 (monthly smoothing of weekly averages)
     * - MONTHLY: window=3 (quarterly smoothing of monthly averages)
     *
     * @param entries Sorted chart entries.
     * @param window Number of points to average.
     * @return Moving average entries aligned with the input x-coordinates.
     */
    private fun computeMovingAverage(entries: List<Entry>, window: Int): List<Entry> {
        if (entries.size < window) return emptyList()

        return entries.mapIndexedNotNull { index, entry ->
            if (index < window - 1) return@mapIndexedNotNull null
            val sum = (0 until window).sumOf { entries[index - it].y.toDouble() }
            Entry(entry.x, (sum / window).toFloat())
        }
    }

    /**
     * Calculates BMI using the imperial formula.
     * BMI = (weight_lbs / (height_inches^2)) * 703
     *
     * @param weight Weight in pounds.
     * @param height Height in inches.
     * @return BMI value, or null if either input is null or height is zero.
     */
    private fun calculateBmi(weight: Double?, height: Double?): Double? {
        if (weight == null || height == null || height <= 0.0) return null
        return (weight / (height * height)) * 703.0
    }

    /**
     * Parses an ISO-8601 date/time string to epoch day.
     * Handles both "2026-02-18T10:00:00" (LocalDateTime) and "2026-02-18" (LocalDate) formats.
     *
     * @param isoString ISO-8601 date string from server.
     * @return Epoch day value, or null if parsing fails.
     */
    private fun parseToEpochDay(isoString: String): Long? {
        return try {
            LocalDateTime.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toLocalDate().toEpochDay()
        } catch (_: Exception) {
            try {
                LocalDate.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE).toEpochDay()
            } catch (_: Exception) {
                null
            }
        }
    }
}
