package com.example.smartdailyexpensetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ExpenseAdapter(
    private val expenses: List<Expense>,
    private val onItemClick: (Expense) -> Unit,
    private val onItemLongClick: (Expense) -> Boolean
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.expenseTitle)
        val amountTextView: TextView = itemView.findViewById(R.id.expenseAmount)
        val dateTextView: TextView = itemView.findViewById(R.id.expenseDate)
        val categoryTextView: TextView = itemView.findViewById(R.id.expenseCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.expense_item, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        holder.titleTextView.text = expense.title
        holder.amountTextView.text = "$${expense.amount}"
        holder.dateTextView.text = dateFormat.format(expense.date)
        holder.categoryTextView.text = expense.category

        // Set click listeners
        holder.itemView.setOnClickListener {
            onItemClick(expense)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(expense)
        }
    }

    override fun getItemCount(): Int {
        return expenses.size
    }
}