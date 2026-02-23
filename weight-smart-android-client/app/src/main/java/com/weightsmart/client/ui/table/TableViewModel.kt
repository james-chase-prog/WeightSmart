package com.weightsmart.client.ui.table

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weightsmart.client.data.SessionManager
import com.weightsmart.client.data.local.entity.WeightEntryEntity
import com.weightsmart.client.data.repository.WeightRepository
import com.weightsmart.client.domain.usecase.DeleteWeightUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.ceil

//Column used as the primary sort key in the weight log table.
enum class SortColumn { DATE, WEIGHT }

//Sort direction for table column ordering.
enum class SortDirection { ASC, DESC }

//Time-of-day filter for weight entries: all entries, morning only, or afternoon/evening only.
enum class DaypartFilter { ALL, AM, PM }

/**
 * TableUiState
 * Immutable snapshot of the paginated weight log table display state.
 *
 * @property title Personalized header (e.g. "James's Weight Log").
 * @property rows Current page of formatted weight entries for the RecyclerView.
 * @property currentPage Zero-based page index.
 * @property totalPages Total number of pages given current filter/sort.
 * @property hasPrevious Whether a previous page exists (enables Prev button).
 * @property hasNext Whether a next page exists (enables Next button).
 * @property pageIndicator Human-readable page indicator (e.g. "2 / 5").
 * @property sortColumn Which column is currently sorted.
 * @property sortDirection ASC or DESC for the sorted column.
 * @property daypartFilter Active AM/PM filter (ALL shows everything).
 */
data class TableUiState(
    val title: String = "Weight Log",
    val rows: List<UiRow> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val pageIndicator: String = "0 / 0",
    val sortColumn: SortColumn = SortColumn.DATE,
    val sortDirection: SortDirection = SortDirection.DESC,
    val daypartFilter: DaypartFilter = DaypartFilter.ALL
) {
    /**
     * UiRow
     * Pre-formatted display data for a single weight entry row.
     *
     * @property id Unique entry ID (used for stable RecyclerView IDs and delete operations).
     * @property weightText Formatted weight string (e.g. "184.6").
     * @property dateText Formatted date string (e.g. "01/15/2026").
     * @property timeText Formatted time string (e.g. "08:30AM").
     */
    data class UiRow(
        val id: String,
        val weightText: String,
        val dateText: String,
        val timeText: String
    )
}

/**
 * TableEvent
 * One-shot UI events consumed exactly once by [TableFragment].
 * Sent via a Channel to avoid replaying stale events on resubscription.
 */
sealed class TableEvent {
    /** Triggers an undo Snackbar after a soft-delete. */
    data class ShowUndoSnackbar(val message: String) : TableEvent()
    /** Triggers the Android share sheet with a CSV file. */
    data class ShareCsv(val uri: Uri) : TableEvent()
    /** Displays an error Snackbar. */
    data class Error(val message: String) : TableEvent()
}

/**
 * TableViewModel
 * Manages the paginated, sortable, filterable weight log table with soft-delete and CSV export.
 *
 * Architecture Role:
 * Hilt-injected ViewModel bridging [TableFragment] (View) and [WeightRepository] / [DeleteWeightUseCase] (Data).
 * Uses the StateFlow + Channel pattern identical to [HomeViewModel].
 *
 * State Management:
 * - [uiState] (StateFlow): Continuous state holding sorted/filtered/paginated rows, page controls, sort indicators.
 * - [events] (Channel -> Flow): One-shot effects (undo Snackbar, share intent, error Snackbar).
 *
 * Data Flow Pipeline:
 * 1. init -> observe SessionManager.userFlow (title) + WeightRepository.getAllWeights() (Room Flow)
 * 2. Any Room change triggers [recompute]: filter -> sort -> paginate -> emit new TableUiState
 * 3. User actions (onSortColumn, onDaypartFilter, onPreviousPage, onNextPage) update state + recompute
 * 4. Delete uses a pending-delete pattern: soft-remove from UI -> 5s undo window -> hard-delete on dismiss
 *
 * Key Concepts & Documentation:
 * Pending-Delete Pattern: Entry is hidden from UI immediately but not deleted from Room until the
 *   Snackbar undo window expires. This provides instant visual feedback with safe undo capability.
 * <a href="https://material.io/components/snackbars#behavior">Reference: Material Snackbar Undo</a>
 *
 * @author James Chase
 * @version 1.1 (P5: adapted to suspend SessionManager API from DataStore migration)
 * @since 2026-01-20
 */
@HiltViewModel
class TableViewModel @Inject constructor(
    private val session: SessionManager,
    private val weightRepository: WeightRepository,
    private val deleteWeight: DeleteWeightUseCase
) : ViewModel() {

    // Continuous UI state; Fragment collects this to update the table display.
    private val _uiState = MutableStateFlow(TableUiState())
    val uiState: StateFlow<TableUiState> = _uiState

    //One-shot event channel; Fragment consumes each event exactly once.
    private val _events = Channel<TableEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    //Number of rows displayed per page.
    private val pageSize = 10

    // --- Formatters & Zone ---
    private val dateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mma")
    private val zone: ZoneId = ZoneId.systemDefault()

    //Full in-memory copy of all weight entries from Room; updated reactively via Flow.
    private var allEntries = emptyList<WeightEntryEntity>()

    //Entry currently in the soft-delete undo window (hidden from UI but not yet deleted from Room).
    private var pendingDeleteEntry: WeightEntryEntity? = null

    //Coroutine job for the 5-second undo timer; cancelled if user taps Undo.
    private var pendingDeleteJob: Job? = null

    init {
        viewModelScope.launch {
            // Ensure the flow is hydrated from disk if this is a cold start
            session.getUser()
        }
        observeUserName()
        observeWeights()
    }

    /**
     * Observes SessionManager.userFlow to build the personalized table title
     * (e.g. "James's Weight Log" with correct possessive apostrophe).
     */
    private fun observeUserName() {
        viewModelScope.launch {
            session.userFlow.filterNotNull().collect { user ->
                val name = user.nickname?.takeIf { it.isNotBlank() } ?: user.username
                val possessive = if (name.endsWith('s', ignoreCase = true)) {
                    "$name'"
                } else {
                    "$name's"
                }
                _uiState.update { it.copy(title = "$possessive Weight Log") }
            }
        }
    }

    /**
     * Observes the full weight entry list from Room via [WeightRepository.getAllWeights].
     * Any insert, update, or delete in the weight_entries table triggers a new emission,
     * which refreshes [allEntries] and calls [recompute].
     */
    private fun observeWeights() {
        viewModelScope.launch {
            weightRepository.getAllWeights().collect { entities ->
                allEntries = entities
                recompute()
            }
        }
    }

    /**
     * Central pipeline that transforms raw [allEntries] into a displayable page of [TableUiState.UiRow].
     * Steps: exclude pending-delete -> apply daypart filter -> sort -> paginate -> format -> emit state.
     * Called after any data change, sort/filter change, or page navigation.
     */
    private fun recompute() {
        val state = _uiState.value
        val pendingId = pendingDeleteEntry?.id

        val filtered = allEntries
            .filter { it.id != pendingId }
            .let { list ->
                when (state.daypartFilter) {
                    DaypartFilter.ALL -> list
                    DaypartFilter.AM -> list.filter {
                        it.date.atZone(zone).toLocalTime().isBefore(LocalTime.NOON)
                    }
                    DaypartFilter.PM -> list.filter {
                        !it.date.atZone(zone).toLocalTime().isBefore(LocalTime.NOON)
                    }
                }
            }

        val sorted = when (state.sortColumn) {
            SortColumn.DATE -> when (state.sortDirection) {
                SortDirection.ASC -> filtered.sortedBy { it.date }
                SortDirection.DESC -> filtered.sortedByDescending { it.date }
            }
            SortColumn.WEIGHT -> when (state.sortDirection) {
                SortDirection.ASC -> filtered.sortedBy { it.weight }
                SortDirection.DESC -> filtered.sortedByDescending { it.weight }
            }
        }

        val totalPages = if (sorted.isEmpty()) 0
            else ceil(sorted.size / pageSize.toDouble()).toInt()
        val page = state.currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

        val start = page * pageSize
        val endExclusive = (start + pageSize).coerceAtMost(sorted.size)
        val pageItems = if (start < sorted.size) sorted.subList(start, endExclusive) else emptyList()

        val uiRows = pageItems.map { entry ->
            val zdt = entry.date.atZone(zone)
            TableUiState.UiRow(
                id = entry.id,
                weightText = String.format("%.1f", entry.weight),
                dateText = zdt.toLocalDate().format(dateFmt),
                timeText = zdt.toLocalTime().format(timeFmt)
            )
        }

        val indicator = if (totalPages == 0) "0 / 0" else "${page + 1} / $totalPages"

        _uiState.update {
            it.copy(
                rows = uiRows,
                currentPage = page,
                totalPages = totalPages,
                hasPrevious = page > 0,
                hasNext = page < totalPages - 1,
                pageIndicator = indicator
            )
        }
    }

    /**
     * Toggles sort direction if the same column is tapped; switches column + resets to ASC if different.
     * Resets to page 0 after any sort change.
     * @param column The column header the user tapped.
     */
    fun onSortColumn(column: SortColumn) {
        _uiState.update { current ->
            if (current.sortColumn == column) {
                current.copy(
                    sortDirection = if (current.sortDirection == SortDirection.ASC)
                        SortDirection.DESC else SortDirection.ASC,
                    currentPage = 0
                )
            } else {
                current.copy(
                    sortColumn = column,
                    sortDirection = SortDirection.ASC,
                    currentPage = 0
                )
            }
        }
        recompute()
    }

    /**
     * Applies a daypart (AM/PM/ALL) filter and resets to page 0.
     * @param filter The filter selected from the options menu.
     */
    fun onDaypartFilter(filter: DaypartFilter) {
        _uiState.update { it.copy(daypartFilter = filter, currentPage = 0) }
        recompute()
    }

    //Navigates to the previous page if one exists.
    fun onPreviousPage() {
        val current = _uiState.value
        if (current.hasPrevious) {
            _uiState.update { it.copy(currentPage = it.currentPage - 1) }
            recompute()
        }
    }

    // Navigates to the next page if one exists.
    fun onNextPage() {
        val current = _uiState.value
        if (current.hasNext) {
            _uiState.update { it.copy(currentPage = it.currentPage + 1) }
            recompute()
        }
    }

    /**
     * Initiates a soft-delete: hides the entry from the UI immediately and starts a 5-second
     * undo timer. If the user taps Undo, the entry reappears. If the timer expires or the
     * Snackbar is dismissed, [commitPendingDelete] performs the hard delete.
     * @param id The unique ID of the weight entry to delete.
     */
    fun onDeleteRow(id: String) {
        // Commit any previous pending delete first
        commitPendingDelete()

        val entry = allEntries.find { it.id == id } ?: return
        pendingDeleteEntry = entry
        recompute()

        viewModelScope.launch {
            _events.send(TableEvent.ShowUndoSnackbar("Entry deleted"))
        }

        pendingDeleteJob = viewModelScope.launch {
            delay(5000)
            commitPendingDelete()
        }
    }

    //Cancels the pending soft-delete and restores the entry in the UI.
    fun onUndoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        pendingDeleteEntry = null
        recompute()
    }

    // Called when the undo Snackbar is dismissed without tapping Undo; commits the hard delete.
    fun onSnackbarDismissed() {
        commitPendingDelete()
    }

    /**
     * Performs the actual hard-delete via [DeleteWeightUseCase].
     * Clears the pending state first so subsequent operations are not blocked.
     */
    private fun commitPendingDelete() {
        val entry = pendingDeleteEntry ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        pendingDeleteEntry = null

        viewModelScope.launch {
            runCatching { deleteWeight(entry.id) }
                .onFailure {
                    _events.send(TableEvent.Error(it.message ?: "Failed to delete entry."))
                }
        }
    }

    // Triggers a CSV share event if there is data; emits an error event if the log is empty.
    fun onShareWeightLogs() {
        if (allEntries.isEmpty()) {
            viewModelScope.launch {
                _events.send(TableEvent.Error("No data to share."))
            }
            return
        }

        viewModelScope.launch {
            _events.send(TableEvent.ShareCsv(Uri.EMPTY))
        }
    }

    /**
     * Generates a CSV string of all weight entries sorted by date (newest first).
     * Format: "Weight (lbs),Date,Time" header followed by one row per entry.
     * Called by [TableFragment.shareCsv] after the ShareCsv event is received.
     * @return Complete CSV content as a String.
     */
    fun generateCsvContent(): String {
        val sb = StringBuilder()
        sb.appendLine("Weight (lbs),Date,Time")
        allEntries
            .sortedByDescending { it.date }
            .forEach { entry ->
                val zdt = entry.date.atZone(zone)
                val weight = String.format("%.1f", entry.weight)
                val date = zdt.toLocalDate().format(dateFmt)
                val time = zdt.toLocalTime().format(timeFmt)
                sb.appendLine("$weight,$date,$time")
            }
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel pending delete — entry persists safely
        pendingDeleteJob?.cancel()
    }
}
