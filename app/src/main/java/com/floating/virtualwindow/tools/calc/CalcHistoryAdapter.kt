package com.floating.virtualwindow.tools.calc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floating.virtualwindow.R

class CalcHistoryAdapter(
    private val onItemClick: (CalcHistoryItem) -> Unit
) : RecyclerView.Adapter<CalcHistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<CalcHistoryItem>()

    fun submitList(newItems: List<CalcHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calc_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        holder.tvExpression.text = item.expression
        holder.tvResult.text = "= ${item.result}"
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvExpression: TextView = view.findViewById(R.id.tvHistoryExpression)
        val tvResult: TextView = view.findViewById(R.id.tvHistoryResult)
    }
}
