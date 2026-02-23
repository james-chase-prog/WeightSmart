package com.weightsmart.client.ui.table

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.weightsmart.client.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

/**
 * TableFragment
 * Displays a paginated, sortable, filterable table of weight log entries with delete and CSV export.
 *
 * Architecture Role:
 * Thin view layer in the MVVM pattern. Observes [TableViewModel] via the
 * repeatOnLifecycle + StateFlow/Channel pattern. All data operations (sort, filter, paginate,
 * delete, CSV generation) are delegated to the ViewModel.
 *
 * Observation Pattern:
 * - [observeState]: Collects [TableViewModel.uiState] (StateFlow) to update the RecyclerView list,
 *   page indicator, sort arrows, and button enabled states.
 * - [observeEvents]: Collects [TableViewModel.events] (Channel as Flow) for one-shot effects:
 *   undo Snackbar, CSV share intent, and error Snackbar.
 *
 * Features:
 * - Column sort toggle (Weight / Date) with directional arrow indicators.
 * - AM/PM daypart filter via options menu.
 * - Swipe-to-delete with 5-second undo Snackbar (via [TableViewModel] pending-delete pattern).
 * - CSV export via Android share sheet (FileProvider URI).
 *
 * Key Concepts & Documentation:
 * MenuProvider: Fragment-owned menu that is lifecycle-aware (added in MenuHost API).
 * <a href="https://developer.android.com/jetpack/androidx/releases/activity#1.4.0-alpha01">Reference: MenuProvider</a>
 * FileProvider: Generates content:// URIs for sharing files from app-internal cache.
 * <a href="https://developer.android.com/reference/androidx/core/content/FileProvider">Reference: FileProvider</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
@AndroidEntryPoint
class TableFragment : Fragment(R.layout.fragment_table) {

    //Lazy-injected ViewModel; scoped to this Fragment's lifecycle.
    private val viewModel: TableViewModel by viewModels()

    // --- View References (bound manually via findViewById in onViewCreated) ---
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var titleView: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var pageIndicator: TextView
    private lateinit var prevBtn: View
    private lateinit var nextBtn: View
    private lateinit var headerWeight: TextView
    private lateinit var headerDate: TextView
    private lateinit var shareBtn: View
    private lateinit var rowsAdapter: WeightEntryAdapter

    /**
     * Binds view references, configures the RecyclerView + adapter, wires click listeners
     * for sort headers / pagination / export, attaches the options menu, and starts observation.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coordinatorLayout = view.findViewById(R.id.tableCoordinatorLayout)
        titleView = view.findViewById(R.id.tableTitle)
        recycler = view.findViewById(R.id.weightTableRecyclerView)
        pageIndicator = view.findViewById(R.id.pageIndicator)
        prevBtn = view.findViewById(R.id.previousPageButton)
        nextBtn = view.findViewById(R.id.nextPageButton)
        headerWeight = view.findViewById(R.id.header_weight)
        headerDate = view.findViewById(R.id.header_date)
        shareBtn = view.findViewById(R.id.exportToCsvButton)

        rowsAdapter = WeightEntryAdapter(
            onDelete = { id -> viewModel.onDeleteRow(id) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.setHasFixedSize(true)
        recycler.adapter = rowsAdapter

        headerWeight.setOnClickListener { viewModel.onSortColumn(SortColumn.WEIGHT) }
        headerDate.setOnClickListener { viewModel.onSortColumn(SortColumn.DATE) }
        prevBtn.setOnClickListener { viewModel.onPreviousPage() }
        nextBtn.setOnClickListener { viewModel.onNextPage() }
        shareBtn.setOnClickListener { viewModel.onShareWeightLogs() }

        attachMenu()
        observeState()
        observeEvents()
    }

    /**
     * Registers a lifecycle-aware MenuProvider for the AM/PM daypart filter.
     * Menu items map to [DaypartFilter] enum values and delegate to the ViewModel.
     */
    private fun attachMenu() {
        val host: MenuHost = requireActivity()
        host.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_filter_ampm, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.filter_clear_time -> {
                        viewModel.onDaypartFilter(DaypartFilter.ALL)
                        true
                    }
                    R.id.filter_am -> {
                        viewModel.onDaypartFilter(DaypartFilter.AM)
                        true
                    }
                    R.id.filter_pm -> {
                        viewModel.onDaypartFilter(DaypartFilter.PM)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /**
     * Collects [TableViewModel.uiState] StateFlow to update the table UI.
     * Updates include: row data (via ListAdapter.submitList), page indicator text,
     * prev/next button enabled state, and sort-column arrow indicators.
     */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    titleView.text = state.title
                    rowsAdapter.submitList(state.rows)
                    pageIndicator.text = state.pageIndicator
                    prevBtn.isEnabled = state.hasPrevious
                    nextBtn.isEnabled = state.hasNext

                    // Sort arrow indicators
                    val upArrow = "\u25B2"
                    val downArrow = "\u25BC"
                    val arrow = if (state.sortDirection == SortDirection.ASC) upArrow else downArrow

                    headerWeight.text = if (state.sortColumn == SortColumn.WEIGHT) "Weight $arrow" else "Weight"
                    headerDate.text = if (state.sortColumn == SortColumn.DATE) "Date $arrow" else "Date"
                }
            }
        }
    }

    /**
     * Collects [TableViewModel.events] Channel for one-shot UI effects.
     * ShowUndoSnackbar -> Snackbar with undo action, ShareCsv -> Android share intent,
     * Error -> error Snackbar.
     */
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is TableEvent.ShowUndoSnackbar -> showUndoSnackbar(event.message)
                        is TableEvent.ShareCsv -> shareCsv()
                        is TableEvent.Error -> showErrorSnackbar(event.message)
                    }
                }
            }
        }
    }

    /**
     * Displays a Snackbar with an "Undo" action for soft-deleted entries.
     * If the user taps Undo, [TableViewModel.onUndoDelete] restores the row.
     * If the Snackbar is dismissed naturally, [TableViewModel.onSnackbarDismissed] commits the hard delete.
     */
    private fun showUndoSnackbar(message: String) {
        Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.undo)) { viewModel.onUndoDelete() }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        viewModel.onSnackbarDismissed()
                    }
                }
            })
            .show()
    }

    /**
     * Generates a CSV file from [TableViewModel.generateCsvContent], writes it to the app cache,
     * and launches an Android share sheet via ACTION_SEND with a FileProvider content:// URI.
     */
    private fun shareCsv() {
        val csvContent = viewModel.generateCsvContent()
        val cacheDir = File(requireContext().cacheDir, "shared_csv")
        cacheDir.mkdirs()
        val csvFile = File(cacheDir, "weight_logs.csv")
        csvFile.writeText(csvContent)

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            csvFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_weight_logs)))
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_SHORT).show()
    }
}
