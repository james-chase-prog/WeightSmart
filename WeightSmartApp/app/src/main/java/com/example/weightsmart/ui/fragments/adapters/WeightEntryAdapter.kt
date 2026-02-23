package com.example.weightsmart.ui.fragments.table

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.weightsmart.R

/**
 * Renders rows for the table:
 * - weight (leftmost)
 * - date
 * - time (hh:mma)
 * - delete button (rightmost)
 *
 * Uses stable IDs. Do NOT call setHasStableIds() anywhere else.
 */
class WeightEntryAdapter(
    private val onDelete: (id: Long) -> Unit
) : RecyclerView.Adapter<WeightEntryAdapter.VH>() {

    data class UiRow(
        val id: Long,
        val weightText: String,
        val dateText: String,
        val timeText: String
    )

    private var items: List<UiRow> = emptyList()

    init {
        // Set once; never change while observers are registered.
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weight_entry, parent, false)
        return VH(v, onDelete)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /**
     * Simple pager list swap; small lists so notifyDataSetChanged() is fine.
     * (If you later want diff animations, switch to ListAdapter + DiffUtil.)
     */
    fun submitList(newList: List<UiRow>) {
        items = newList
        notifyDataSetChanged()
    }

    class VH(view: View, private val onDelete: (Long) -> Unit) : RecyclerView.ViewHolder(view) {
        private val weight: TextView = view.findViewById(R.id.weightValue)
        private val date: TextView = view.findViewById(R.id.weightDate)
        private val time: TextView = view.findViewById(R.id.weightTime)
        private val deleteBtn: View = view.findViewById(R.id.deleteButton)

        fun bind(row: UiRow) {
            weight.text = row.weightText
            date.text = row.dateText
            time.text = row.timeText
            deleteBtn.setOnClickListener { onDelete(row.id) }
        }
    }
}
