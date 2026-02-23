package com.weightsmart.client.ui.table

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weightsmart.client.R

/**
 * WeightEntryAdapter
 * RecyclerView adapter for rendering paginated weight log rows in [TableFragment].
 *
 * Architecture Role:
 * Binds [TableUiState.UiRow] data objects to item_weight_entry.xml views. Extends [ListAdapter]
 * for efficient diff-based updates via [DiffCallback]. Delegates delete actions to the
 * [TableViewModel] through the [onDelete] callback lambda.
 *
 * Binding Contract:
 * - Each [TableUiState.UiRow] maps to one inflated item_weight_entry layout.
 * - Stable IDs are enabled (hashCode of entry ID) for optimized RecyclerView animations.
 * - The delete button click forwards the entry ID to the ViewModel's soft-delete pipeline.
 *
 * Key Concepts & Documentation:
 * ListAdapter: RecyclerView.Adapter subclass that computes list diffs on a background thread.
 * <a href="https://developer.android.com/reference/androidx/recyclerview/widget/ListAdapter">Reference: ListAdapter</a>
 * DiffUtil.ItemCallback: Defines how to compare items for identity and content equality.
 * <a href="https://developer.android.com/reference/androidx/recyclerview/widget/DiffUtil.ItemCallback">Reference: DiffUtil.ItemCallback</a>
 *
 * @param onDelete Callback invoked with the entry ID when the user taps the delete button.
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
class WeightEntryAdapter(
    private val onDelete: (id: String) -> Unit
) : ListAdapter<TableUiState.UiRow, WeightEntryAdapter.VH>(DiffCallback) {

    init {
        // Enables stable IDs for optimized RecyclerView item animations (add/remove/move)
        setHasStableIds(true)
    }

    // Returns a stable ID derived from the entry's unique string ID.
    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    // Inflates item_weight_entry.xml and creates a ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weight_entry, parent, false)
        return VH(v, onDelete)
    }

    //  Binds the TableUiState.UiRow at the given position to the ViewHolder
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * VH (ViewHolder)
     * Holds references to the weight, date, time TextViews and the delete button
     * within a single item_weight_entry row.
     */
    class VH(view: View, private val onDelete: (String) -> Unit) : RecyclerView.ViewHolder(view) {
        private val weight: TextView = view.findViewById(R.id.weightValue)
        private val date: TextView = view.findViewById(R.id.weightDate)
        private val time: TextView = view.findViewById(R.id.weightTime)
        private val deleteBtn: View = view.findViewById(R.id.deleteButton)

        // Populates view fields from a [TableUiState.UiRow] and wires the delete button.
        fun bind(row: TableUiState.UiRow) {
            weight.text = row.weightText
            date.text = row.dateText
            time.text = row.timeText
            deleteBtn.setOnClickListener { onDelete(row.id) }
        }
    }

    /**
     * DiffCallback
     * Determines item identity (by ID) and content equality (by data class equals)
     * for efficient RecyclerView list updates without full rebinds.
     */
    private object DiffCallback : DiffUtil.ItemCallback<TableUiState.UiRow>() {
        override fun areItemsTheSame(oldItem: TableUiState.UiRow, newItem: TableUiState.UiRow) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TableUiState.UiRow, newItem: TableUiState.UiRow) =
            oldItem == newItem
    }
}
