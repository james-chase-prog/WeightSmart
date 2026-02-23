package com.example.weightsmart.ui.fragments.table

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView

/**
 * Lightweight header row (Weight | Date | Time | Delete) rendered programmatically.
 * No XML needed; keeps things resilient.
 */
class HeaderAdapter(
    private val weightTitle: String = "Weight",
    private val dateTitle: String = "Date",
    private val timeTitle: String = "Time",
    private val actionTitle: String = "Delete"
) : RecyclerView.Adapter<HeaderAdapter.HV>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HV {
        val ctx = parent.context
        val row = LinearLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding((8 * resources.displayMetrics.density).toInt())
        }

        fun buildHeaderCell(text: String, weight: Float): TextView =
            TextView(ctx).apply {
                this.text = text
                this.setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
            }

        // Rudimentary weights to share space
        val weightCell = buildHeaderCell(weightTitle, 1.2f)
        val dateCell   = buildHeaderCell(dateTitle,   1.0f)
        val timeCell   = buildHeaderCell(timeTitle,   1.0f)
        val actCell    = buildHeaderCell(actionTitle, 0.8f)

        row.addView(weightCell)
        row.addView(dateCell)
        row.addView(timeCell)
        row.addView(actCell)

        return HV(row, weightCell, dateCell, timeCell, actCell)
    }

    override fun onBindViewHolder(holder: HV, position: Int) {
        // static header — nothing to bind dynamically
    }

    override fun getItemCount(): Int = 1

    class HV(
        view: View,
        val weight: TextView,
        val date: TextView,
        val time: TextView,
        val action: TextView
    ) : RecyclerView.ViewHolder(view)
}
