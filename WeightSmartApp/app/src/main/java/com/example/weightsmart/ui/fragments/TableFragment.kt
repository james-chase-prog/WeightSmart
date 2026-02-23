package com.example.weightsmart.ui.fragments

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weightsmart.R
import com.example.weightsmart.core.session.SessionManager
import com.example.weightsmart.domain.model.Weight
import com.example.weightsmart.domain.usecase.DeleteWeightUseCase
import com.example.weightsmart.domain.usecase.ListWeightsUseCase
import com.example.weightsmart.ui.fragments.table.WeightEntryAdapter
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

@AndroidEntryPoint
class TableFragment : Fragment(R.layout.fragment_table) {

    // ---- Injected use cases / session ----
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var listWeights: ListWeightsUseCase
    @Inject lateinit var deleteWeight: DeleteWeightUseCase

    // ---- Views ----
    private lateinit var recycler: RecyclerView
    private lateinit var pageIndicator: TextView
    private lateinit var prevBtn: View
    private lateinit var nextBtn: View

    // ---- Adapter ----
    private lateinit var rowsAdapter: WeightEntryAdapter

    // ---- Paging / filtering / sorting state ----
    private val pageSize = 10
    private var currentPage = 0
    private var currentSort = Sort.NEWEST
    private var currentFilter = Daypart.ALL

    // ---- Data caches ----
    private val allRows = mutableListOf<Weight>()     // entire dataset from DB
    private var filteredSorted = emptyList<Weight>()  // after filter + sort

    // ---- Formatters ----
    private val dateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mma")
    private val zone: ZoneId by lazy { ZoneId.systemDefault() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Views (match your XML IDs)
        recycler       = view.findViewById(R.id.weightTableRecyclerView)
        pageIndicator  = view.findViewById(R.id.pageIndicator)
        prevBtn        = view.findViewById(R.id.previousPageButton)
        nextBtn        = view.findViewById(R.id.nextPageButton)

        // Recycler setup
        rowsAdapter = WeightEntryAdapter(
            onDelete = { id -> onDeleteRow(id) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.setHasFixedSize(true)
        recycler.adapter = rowsAdapter

        // Menu (sort + daypart filter)
        attachMenu()

        // Pagination buttons
        prevBtn.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }
        nextBtn.setOnClickListener {
            val totalPages = pagesCount()
            if (currentPage < totalPages - 1) {
                currentPage++
                renderPage()
            }
        }

        // Initial load
        refreshFromDb()
    }

    private fun attachMenu() {
        val host: MenuHost = requireActivity()
        host.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_sort, menu)
                menuInflater.inflate(R.menu.menu_filter_ampm, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    // Sort (ensure these IDs match menu_sort.xml)
                    R.id.sort_asc -> {
                        currentSort = Sort.NEWEST
                        applyFilterAndSort()
                        true
                    }
                    R.id.sort_desc -> {
                        currentSort = Sort.OLDEST
                        applyFilterAndSort()
                        true
                    }

                    // Daypart (ensure these IDs match  menu_filter_ampm.xml)
                    R.id.filter_clear_time -> {
                        currentFilter = Daypart.ALL
                        applyFilterAndSort()
                        true
                    }
                    R.id.filter_am -> {
                        currentFilter = Daypart.AM
                        applyFilterAndSort()
                        true
                    }
                    R.id.filter_pm -> {
                        currentFilter = Daypart.PM
                        applyFilterAndSort()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // ---- Data loading / transforms ----

    private fun refreshFromDb(keepPage: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = sessionManager.getSession()?.userId
            if (uid == null) {
                Toast.makeText(requireContext(), "Not logged in.", Toast.LENGTH_LONG).show()
                return@launch
            }

            val rows: List<Weight> = withContext(Dispatchers.IO) { listWeights(uid) }

            allRows.clear()
            allRows.addAll(rows)

            if (!keepPage) currentPage = 0
            applyFilterAndSort()
        }
    }

    private fun applyFilterAndSort() {
        filteredSorted = allRows
            .let { list ->
                when (currentFilter) {
                    Daypart.ALL -> list
                    Daypart.AM  -> list.filter {
                        it.measuredAt.atZone(zone).toLocalTime().isBefore(LocalTime.NOON)
                    }
                    Daypart.PM  -> list.filter {
                        !it.measuredAt.atZone(zone).toLocalTime().isBefore(LocalTime.NOON)
                    }
                }
            }
            .let { list ->
                when (currentSort) {
                    Sort.NEWEST -> list.sortedByDescending { it.measuredAt }
                    Sort.OLDEST -> list.sortedBy { it.measuredAt }
                }
            }

        // Reset to page 0 if we moved beyond the last page
        if (currentPage >= pagesCount()) currentPage = 0

        renderPage()
    }

    private fun renderPage() {
        val totalPages = pagesCount()
        val start = currentPage * pageSize
        val endExclusive = (start + pageSize).coerceAtMost(filteredSorted.size)
        val page = if (start in 0 until endExclusive) {
            filteredSorted.subList(start, endExclusive)
        } else {
            emptyList()
        }

        // Map to UI rows
        val uiRows = page.map { it.toUiRow() }
        rowsAdapter.submitList(uiRows)

        // Update pagination UI
        pageIndicator.text = if (totalPages == 0) "0 / 0" else "${currentPage + 1} / $totalPages"
        prevBtn.isEnabled = currentPage > 0
        nextBtn.isEnabled = currentPage < totalPages - 1
    }

    private fun onDeleteRow(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { deleteWeight(id) } }
            result.onSuccess {
                Toast.makeText(requireContext(), "Entry deleted.", Toast.LENGTH_SHORT).show()
                val previousPage = currentPage
                refreshFromDb(keepPage = true)
                // If we deleted the last item on the last page, adjust page down
                if (previousPage >= pagesCount() && pagesCount() > 0) {
                    currentPage = pagesCount() - 1
                    renderPage()
                }
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Failed to delete entry.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pagesCount(): Int {
        if (filteredSorted.isEmpty()) return 0
        return ceil(filteredSorted.size / pageSize.toDouble()).toInt()
    }

    // ---- Mappers ----

    private fun Weight.toUiRow(): WeightEntryAdapter.UiRow {
        val zdt = measuredAt.atZone(zone)
        return WeightEntryAdapter.UiRow(
            id = id,
            weightText = String.format("%.1f", value),
            dateText = zdt.toLocalDate().format(dateFmt),
            timeText = zdt.toLocalTime().format(timeFmt)
        )
    }

    private enum class Sort { NEWEST, OLDEST }
    private enum class Daypart { ALL, AM, PM }
}
