package com.example.smartdailyexpensetracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class EntryAdapter(
    private val entries: List<Entry>,
    private val onItemClick: (Entry) -> Unit,
    private val onItemLongClick: (Entry) -> Boolean
) : RecyclerView.Adapter<EntryAdapter.EntryViewHolder>() {

    class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.entryTitle)
        val amountTextView: TextView = itemView.findViewById(R.id.entryAmount)
        val dateTextView: TextView = itemView.findViewById(R.id.entryDate)
        val categoryTextView: TextView = itemView.findViewById(R.id.entryCategory)
        val typeIndicator: TextView = itemView.findViewById(R.id.typeIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.entry_item, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val entry = entries[position]
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        holder.titleTextView.text = entry.title
        holder.amountTextView.text = "$${"%.2f".format(entry.amount)}"
        holder.dateTextView.text = dateFormat.format(entry.date)
        holder.categoryTextView.text = entry.category

        // Set color and indicator based on type
        if (entry.type == "income") {
            holder.amountTextView.setTextColor(Color.GREEN)
            holder.typeIndicator.text = "💰"
            holder.typeIndicator.setTextColor(Color.GREEN)
        } else {
            holder.amountTextView.setTextColor(Color.RED)
            holder.typeIndicator.text = "💸"
            holder.typeIndicator.setTextColor(Color.RED)
        }

        // Click listener for editing
        holder.itemView.setOnClickListener {
            onItemClick(entry)
        }

        // Long click listener for deletion confirmation
        holder.itemView.setOnLongClickListener {
            onItemLongClick(entry)
        }
    }

    override fun getItemCount(): Int {
        return entries.size
    }
}