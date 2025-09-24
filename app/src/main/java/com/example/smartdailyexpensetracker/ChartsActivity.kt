package com.example.smartdailyexpensetracker

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChartsActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var totalSpentText: TextView
    private lateinit var averageDailyText: TextView
    private lateinit var mostSpentCategoryText: TextView
    private lateinit var categoryBreakdownText: TextView
    private lateinit var weeklyBreakdownText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts_simple)

        // Initialize ViewModel
        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        initializeViews()
        setupObservers()

        // Set up toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Expense Analytics"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeViews() {
        totalSpentText = findViewById(R.id.totalSpentText)
        averageDailyText = findViewById(R.id.averageDailyText)
        mostSpentCategoryText = findViewById(R.id.mostSpentCategoryText)
        categoryBreakdownText = findViewById(R.id.categoryBreakdownText)
        weeklyBreakdownText = findViewById(R.id.weeklyBreakdownText)
    }

    private fun setupObservers() {
        expenseViewModel.expenses.observe(this) { expenses ->
            if (expenses.isNotEmpty()) {
                updateStatistics(expenses)
                updateCategoryBreakdown(expenses)
                updateWeeklyBreakdown(expenses)
            } else {
                showEmptyState()
            }
        }

        expenseViewModel.loadExpenses()
    }

    private fun updateStatistics(expenses: List<Expense>) {
        val total = expenses.sumOf { it.amount }
        val days = getNumberOfDays(expenses)
        val averageDaily = if (days > 0) total / days else 0.0

        // Find most spent category
        val categoryTotals = expenses.groupBy { it.category.ifEmpty { "Uncategorized" } }
            .mapValues { it.value.sumOf { expense -> expense.amount } }
        val mostSpentCategory = categoryTotals.maxByOrNull { it.value }?.key ?: "No data"
        val mostSpentAmount = categoryTotals.maxByOrNull { it.value }?.value ?: 0.0

        totalSpentText.text = "Total Spent: $${String.format("%.2f", total)}"
        averageDailyText.text = "Daily Average: $${String.format("%.2f", averageDaily)}"
        mostSpentCategoryText.text = "Top Category: $mostSpentCategory ($${String.format("%.2f", mostSpentAmount)})"
    }

    private fun updateCategoryBreakdown(expenses: List<Expense>) {
        val categoryTotals = expenses.groupBy { it.category.ifEmpty { "Uncategorized" } }
            .mapValues { it.value.sumOf { expense -> expense.amount } }
            .toList()
            .sortedByDescending { it.second }

        val breakdown = StringBuilder("📊 Category Breakdown:\n\n")
        categoryTotals.forEach { (category, total) ->
            val percentage = (total / expenses.sumOf { it.amount } * 100).toInt()
            val bar = "█".repeat((percentage / 5).coerceAtLeast(1))
            breakdown.append("$bar $category: $${String.format("%.2f", total)} ($percentage%)\n\n")
        }

        categoryBreakdownText.text = breakdown.toString()
    }

    private fun updateWeeklyBreakdown(expenses: List<Expense>) {
        val last7Days = getLast7Days()
        val dailyTotals = mutableMapOf<String, Double>()

        last7Days.forEach { date ->
            dailyTotals[date] = 0.0
        }

        expenses.forEach { expense ->
            val expenseDate = SimpleDateFormat("MMM dd", Locale.getDefault()).format(expense.date)
            if (dailyTotals.containsKey(expenseDate)) {
                dailyTotals[expenseDate] = dailyTotals[expenseDate]!! + expense.amount
            }
        }

        val breakdown = StringBuilder("📅 Last 7 Days:\n\n")
        last7Days.forEach { date ->
            val total = dailyTotals[date] ?: 0.0
            val barLength = ((total / (dailyTotals.values.maxOrNull() ?: 1.0)) * 10).toInt().coerceAtLeast(1)
            val bar = "█".repeat(barLength)
            breakdown.append("$bar $date: $${String.format("%.2f", total)}\n\n")
        }

        weeklyBreakdownText.text = breakdown.toString()
    }

    private fun showEmptyState() {
        totalSpentText.text = "Total Spent: $0.00"
        averageDailyText.text = "Daily Average: $0.00"
        mostSpentCategoryText.text = "Top Category: No expenses yet"
        categoryBreakdownText.text = "📊 Category Breakdown:\n\nNo expenses to display"
        weeklyBreakdownText.text = "📅 Last 7 Days:\n\nNo expenses to display"
    }

    private fun getLast7Days(): List<String> {
        val format = SimpleDateFormat("MMM dd", Locale.getDefault())
        val dates = mutableListOf<String>()

        for (i in 6 downTo 0) {
            val date = Date(System.currentTimeMillis() - i * 24 * 60 * 60 * 1000L)
            dates.add(format.format(date))
        }

        return dates
    }

    private fun getNumberOfDays(expenses: List<Expense>): Int {
        if (expenses.isEmpty()) return 0

        val dates = expenses.map { it.date.time }
        val minDate = dates.minOrNull() ?: return 0
        val maxDate = dates.maxOrNull() ?: return 0

        return ((maxDate - minDate) / (24 * 60 * 60 * 1000)).toInt() + 1
    }
}