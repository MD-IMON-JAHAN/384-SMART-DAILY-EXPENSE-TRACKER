package com.example.smartdailyexpensetracker

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChartsActivity : AppCompatActivity() {

    private val expenseViewModel: ExpenseViewModel by viewModels { ExpenseViewModelFactory(application) }

    private var totalIncomeText: TextView? = null
    private var totalExpenseText: TextView? = null
    private var netBalanceText: TextView? = null
    private var averageDailyText: TextView? = null
    private var mostSpentCategoryText: TextView? = null
    private var categoryBreakdownText: TextView? = null
    private var weeklyBreakdownText: TextView? = null
    private var budgetProgressText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        // Set up Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Enable back icon
        supportActionBar?.title = "Charts" // Set title to "Charts"

        initializeViews()
        setupObservers()

        lifecycleScope.launch {
            expenseViewModel.loadEntries()
            expenseViewModel.loadBudget()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeViews() {
        totalIncomeText = findViewById(R.id.totalIncomeText)
        totalExpenseText = findViewById(R.id.totalExpenseText)
        netBalanceText = findViewById(R.id.netBalanceText)
        averageDailyText = findViewById(R.id.averageDailyText)
        mostSpentCategoryText = findViewById(R.id.mostSpentCategoryText)
        categoryBreakdownText = findViewById(R.id.categoryBreakdownText)
        weeklyBreakdownText = findViewById(R.id.weeklyBreakdownText)
        budgetProgressText = findViewById(R.id.budgetProgressText)
    }

    private fun setupObservers() {
        expenseViewModel.entries.observe(this) { entries ->
            expenseViewModel.budget.observe(this) { budget ->
                if (entries.isNotEmpty()) {
                    updateStatistics(entries, budget)
                    updateCategoryBreakdown(entries)
                    updateWeeklyBreakdown(entries)
                    updateBudgetProgress(entries, budget)
                } else {
                    showEmptyState()
                }
            }
        }
    }

    private fun updateStatistics(entries: List<Entry>, budget: Budget?) {
        val totalIncome = entries.filter { it.type == "income" }.sumOf { it.amount }
        val totalExpense = entries.filter { it.type == "expense" }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense
        val days = getNumberOfDays(entries)
        val averageDaily = if (days > 0) netBalance / days else 0.0

        val categoryTotals = entries.groupBy { it.category.ifEmpty { "Uncategorized" } }
            .mapValues { it.value.sumOf { entry -> entry.amount } }
        val mostSpentCategory = categoryTotals.maxByOrNull { it.value }?.key ?: "No data"
        val mostSpentAmount = categoryTotals.maxByOrNull { it.value }?.value ?: 0.0

        totalIncomeText?.text = "Total Income: $${"%.2f".format(totalIncome)}"
        totalExpenseText?.text = "Total Expense: $${"%.2f".format(totalExpense)}"
        netBalanceText?.text = "Net Balance: $${"%.2f".format(netBalance)}"
        averageDailyText?.text = "Daily Average: $${"%.2f".format(averageDaily)}"
        mostSpentCategoryText?.text = "Top Category: $mostSpentCategory ($${"%.2f".format(mostSpentAmount)})"
    }

    private fun updateCategoryBreakdown(entries: List<Entry>) {
        val categoryTotals = entries.groupBy { it.category.ifEmpty { "Uncategorized" } }
            .mapValues { it.value.sumOf { entry -> entry.amount } }
            .toList()
            .sortedByDescending { it.second }

        val breakdown = StringBuilder("📊 Category Breakdown:\n\n")
        categoryTotals.forEach { (category, total) ->
            val percentage = (total / entries.sumOf { it.amount } * 100).toInt()
            val bar = "█".repeat((percentage / 5).coerceAtLeast(1))
            breakdown.append("$bar $category: $${"%.2f".format(total)} ($percentage%)\n\n")
        }

        categoryBreakdownText?.text = breakdown.toString()
    }

    private fun updateWeeklyBreakdown(entries: List<Entry>) {
        val last7Days = getLast7Days()
        val dailyTotals = mutableMapOf<String, Double>()

        last7Days.forEach { date ->
            dailyTotals[date] = 0.0
        }

        entries.forEach { entry ->
            val entryDate = SimpleDateFormat("MMM dd", Locale.getDefault()).format(entry.date)
            if (dailyTotals.containsKey(entryDate)) {
                dailyTotals[entryDate] = dailyTotals[entryDate]!! + entry.amount
            }
        }

        val breakdown = StringBuilder("📅 Last 7 Days:\n\n")
        last7Days.forEach { date ->
            val total = dailyTotals[date] ?: 0.0
            val barLength = ((total / (dailyTotals.values.maxOrNull() ?: 1.0)) * 10).toInt().coerceAtLeast(1)
            val bar = "█".repeat(barLength)
            breakdown.append("$bar $date: $${"%.2f".format(total)}\n\n")
        }

        weeklyBreakdownText?.text = breakdown.toString()
    }

    private fun updateBudgetProgress(entries: List<Entry>, budget: Budget?) {
        if (budget == null) {
            budgetProgressText?.text = "💰 Budget Progress:\n\nNo budget set yet"
            return
        }

        val totalExpense = entries.filter { it.type == "expense" }.sumOf { it.amount }
        val percentageUsed = (totalExpense / budget.monthlyBudget * 100).toInt()
        val remaining = budget.monthlyBudget - totalExpense

        val progressBar = when {
            percentageUsed >= 100 -> "██████████"
            else -> "█".repeat(percentageUsed / 10) + "░".repeat(10 - percentageUsed / 10)
        }

        val status = if (totalExpense > budget.monthlyBudget) {
            "⚠️ EXCEEDED by $${"%.2f".format(totalExpense - budget.monthlyBudget)}"
        } else {
            "${100 - percentageUsed}% remaining"
        }

        val progressText = """
            💰 Budget Progress:
            
            $progressBar $percentageUsed%
            
            Expense: $${"%.2f".format(totalExpense)} / $${"%.2f".format(budget.monthlyBudget)}
            Remaining: $${"%.2f".format(remaining)}
            Status: $status
        """.trimIndent()

        budgetProgressText?.text = progressText

        budgetProgressText?.let {
            when {
                percentageUsed >= 100 -> it.setTextColor(Color.RED)
                percentageUsed >= 75 -> it.setTextColor(Color.parseColor("#FFA500"))
                else -> it.setTextColor(Color.GREEN)
            }
        }
    }

    private fun showEmptyState() {
        totalIncomeText?.text = "Total Income: $0.00"
        totalExpenseText?.text = "Total Expense: $0.00"
        netBalanceText?.text = "Net Balance: $0.00"
        averageDailyText?.text = "Daily Average: $0.00"
        mostSpentCategoryText?.text = "Top Category: No entries yet"
        categoryBreakdownText?.text = "📊 Category Breakdown:\n\nNo entries to display"
        weeklyBreakdownText?.text = "📅 Last 7 Days:\n\nNo entries to display"
        budgetProgressText?.text = "💰 Budget Progress:\n\nNo data available"
        budgetProgressText?.setTextColor(Color.GRAY)
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

    private fun getNumberOfDays(entries: List<Entry>): Int {
        if (entries.isEmpty()) return 0

        val dates = entries.map { it.date.time }
        val minDate = dates.minOrNull() ?: return 0
        val maxDate = dates.maxOrNull() ?: return 0

        return ((maxDate - minDate) / (24 * 60 * 60 * 1000)).toInt() + 1
    }
}