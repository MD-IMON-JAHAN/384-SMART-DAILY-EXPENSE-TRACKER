package com.example.smartdailyexpensetracker

import android.graphics.Color
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
    private lateinit var budgetProgressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

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
        budgetProgressText = findViewById(R.id.budgetProgressText)
    }

    private fun setupObservers() {
        expenseViewModel.expenses.observe(this) { expenses ->
            expenseViewModel.budget.observe(this) { budget ->
                if (expenses.isNotEmpty()) {
                    updateStatistics(expenses, budget)
                    updateCategoryBreakdown(expenses)
                    updateWeeklyBreakdown(expenses)
                    updateBudgetProgress(expenses, budget)
                } else {
                    showEmptyState()
                }
            }
        }

        expenseViewModel.loadExpenses()
        expenseViewModel.loadBudget()
    }

    private fun updateStatistics(expenses: List<Expense>, budget: Budget?) {
        val total = expenses.sumOf { it.amount }
        val days = getNumberOfDays(expenses)
        val averageDaily = if (days > 0) total / days else 0.0

        // Find most spent category
        val categoryTotals = expenses.groupBy { it.category.ifEmpty { "Uncategorized" } }
            .mapValues { it.value.sumOf { expense -> expense.amount } }
        val mostSpentCategory = categoryTotals.maxByOrNull { it.value }?.key ?: "No data"
        val mostSpentAmount = categoryTotals.maxByOrNull { it.value }?.value ?: 0.0

        totalSpentText.text = "Total Spent: $${"%.2f".format(total)}"
        averageDailyText.text = "Daily Average: $${"%.2f".format(averageDaily)}"
        mostSpentCategoryText.text = "Top Category: $mostSpentCategory ($${"%.2f".format(mostSpentAmount)})"
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
            breakdown.append("$bar $category: $${"%.2f".format(total)} ($percentage%)\n\n")
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
            breakdown.append("$bar $date: $${"%.2f".format(total)}\n\n")
        }

        weeklyBreakdownText.text = breakdown.toString()
    }

    private fun updateBudgetProgress(expenses: List<Expense>, budget: Budget?) {
        if (budget == null) {
            budgetProgressText.text = "💰 Budget Progress:\n\nNo budget set yet"
            return
        }

        val totalSpent = expenses.sumOf { it.amount }
        val percentageUsed = (totalSpent / budget.monthlyBudget * 100).toInt()
        val remaining = budget.monthlyBudget - totalSpent

        val progressBar = when {
            percentageUsed >= 100 -> "██████████" // Full bar if exceeded
            else -> "█".repeat(percentageUsed / 10) + "░".repeat(10 - percentageUsed / 10)
        }

        val status = if (totalSpent > budget.monthlyBudget) {
            "⚠️ EXCEEDED by $${"%.2f".format(totalSpent - budget.monthlyBudget)}"
        } else {
            "${100 - percentageUsed}% remaining"
        }

        val progressText = """
            💰 Budget Progress:
            
            $progressBar $percentageUsed%
            
            Spent: $${"%.2f".format(totalSpent)} / $${"%.2f".format(budget.monthlyBudget)}
            Remaining: $${"%.2f".format(remaining)}
            Status: $status
        """.trimIndent()

        budgetProgressText.text = progressText

        // Set color based on budget usage
        when {
            percentageUsed >= 100 -> budgetProgressText.setTextColor(Color.RED)
            percentageUsed >= 75 -> budgetProgressText.setTextColor(Color.parseColor("#FFA500")) // Orange
            else -> budgetProgressText.setTextColor(Color.GREEN)
        }
    }

    private fun showEmptyState() {
        totalSpentText.text = "Total Spent: $0.00"
        averageDailyText.text = "Daily Average: $0.00"
        mostSpentCategoryText.text = "Top Category: No expenses yet"
        categoryBreakdownText.text = "📊 Category Breakdown:\n\nNo expenses to display"
        weeklyBreakdownText.text = "📅 Last 7 Days:\n\nNo expenses to display"
        budgetProgressText.text = "💰 Budget Progress:\n\nNo data available"
        budgetProgressText.setTextColor(Color.GRAY)
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