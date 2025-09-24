package com.example.smartdailyexpensetracker

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private val expenseViewModel: ExpenseViewModel by viewModels()
    private val auth = Firebase.auth
    private var budgetWarningShown = false

    private lateinit var entryTitle: EditText
    private lateinit var entryAmount: EditText
    private lateinit var entryCategory: EditText
    private lateinit var addEntryButton: Button
    private lateinit var entriesRecyclerView: RecyclerView
    private lateinit var totalBalanceTextView: TextView
    private lateinit var budgetStatusTextView: TextView
    private lateinit var setBudgetButton: Button
    private lateinit var aiAdviceButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var entryTypeExpense: RadioButton
    private lateinit var entryTypeIncome: RadioButton

    private lateinit var entryAdapter: EntryAdapter

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "AppPrefs"
        private const val KEY_DARK_MODE = "darkMode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))
        applySavedTheme()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        setupButtonListeners()
        setupObservers()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_user_email).title = auth.currentUser?.email ?: "User"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                signOut()
                true
            }
            R.id.action_view_charts -> {
                startActivity(Intent(this, ChartsActivity::class.java))
                true
            }
            R.id.action_ai_chat -> {
                startActivity(Intent(this, ChatActivity::class.java))
                true
            }
            R.id.action_theme_toggle -> {
                toggleTheme(!isDarkModeEnabled())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun initializeViews() {
        entryTitle = findViewById(R.id.entryTitle)
        entryAmount = findViewById(R.id.entryAmount)
        entryCategory = findViewById(R.id.entryCategory)
        addEntryButton = findViewById(R.id.addEntryButton)
        entriesRecyclerView = findViewById(R.id.entriesRecyclerView)
        totalBalanceTextView = findViewById(R.id.totalBalance)
        budgetStatusTextView = findViewById(R.id.budgetStatusText)
        setBudgetButton = findViewById(R.id.setBudgetButton)
        aiAdviceButton = findViewById(R.id.aiAdviceButton)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        entryTypeExpense = findViewById(R.id.entryTypeExpense)
        entryTypeIncome = findViewById(R.id.entryTypeIncome)
    }

    private fun setupRecyclerView() {
        entryAdapter = EntryAdapter(
            emptyList(),
            onItemClick = { entry -> EditEntryDialog(this, entry, expenseViewModel).show() },
            onItemLongClick = { entry -> showDeleteConfirmation(entry); true }
        )
        entriesRecyclerView.layoutManager = LinearLayoutManager(this)
        entriesRecyclerView.adapter = entryAdapter
    }

    private fun setupButtonListeners() {
        addEntryButton.setOnClickListener { addNewEntry() }
        setBudgetButton.setOnClickListener { showBudgetDialog() }
        aiAdviceButton.setOnClickListener { expenseViewModel.getAIAdvice() }
    }

    private fun setupObservers() {
        expenseViewModel.entries.observe(this) { entries ->
            entryAdapter = EntryAdapter(
                entries,
                onItemClick = { entry -> EditEntryDialog(this, entry, expenseViewModel).show() },
                onItemLongClick = { entry -> showDeleteConfirmation(entry); true }
            )
            entriesRecyclerView.adapter = entryAdapter
            updateTotalBalance()
            updateBudgetStatus()
        }

        expenseViewModel.budget.observe(this) {
            updateBudgetStatus()
            checkForBudgetWarnings()
        }

        expenseViewModel.isLoading.observe(this) { isLoading ->
            loadingProgressBar.visibility = if (isLoading) ProgressBar.VISIBLE else ProgressBar.GONE
            addEntryButton.isEnabled = !isLoading
            setBudgetButton.isEnabled = !isLoading
            aiAdviceButton.isEnabled = !isLoading
        }

        expenseViewModel.aiAdvice.observe(this) { advice ->
            advice?.let {
                showAIAdviceDialog(it)
                expenseViewModel.clearAIAdvice()
            }
        }
    }

    private fun showBudgetDialog() {
        val currentBudget = expenseViewModel.budget.value
        val dialogView = layoutInflater.inflate(R.layout.dialog_budget, null)
        val budgetInput = dialogView.findViewById<EditText>(R.id.budgetInput)

        currentBudget?.monthlyBudget?.let {
            budgetInput.setText(it.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (currentBudget != null) "Update Monthly Budget" else "Set Monthly Budget")
            .setView(dialogView)
            .setPositiveButton("Set Budget") { dialog, _ ->
                val budgetText = budgetInput.text.toString().trim()
                if (budgetText.isNotEmpty()) {
                    val budgetAmount = budgetText.toDoubleOrNull()
                    if (budgetAmount != null && budgetAmount > 0) {
                        expenseViewModel.setMonthlyBudget(budgetAmount)
                        Toast.makeText(this, "Budget set successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please enter a budget amount", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateBudgetStatus() {
        val budget = expenseViewModel.budget.value
        val totalExpenses = expenseViewModel.getTotalExpenses()

        if (budget == null) {
            budgetStatusTextView.text = "Monthly Budget: Not set"
            budgetStatusTextView.setTextColor(Color.GRAY)
        } else {
            val remaining = budget.monthlyBudget - totalExpenses
            val percentage = if (budget.monthlyBudget > 0)
                (totalExpenses / budget.monthlyBudget) * 100 else 0.0

            val statusText = if (remaining >= 0) {
                "Budget: $${"%.2f".format(totalExpenses)} / $${"%.2f".format(budget.monthlyBudget)} | " +
                        "Remaining: $${"%.2f".format(remaining)} (${"%.1f".format(percentage)}%)"
            } else {
                "Budget: $${"%.2f".format(totalExpenses)} / $${"%.2f".format(budget.monthlyBudget)} | " +
                        "Overspent: $${"%.2f".format(-remaining)} (${"%.1f".format(percentage)}%)"
            }

            budgetStatusTextView.text = statusText

            when {
                percentage >= 100 -> budgetStatusTextView.setTextColor(Color.RED)
                percentage > 75 -> budgetStatusTextView.setTextColor(Color.parseColor("#FFA500"))
                else -> budgetStatusTextView.setTextColor(Color.GREEN)
            }
        }
    }

    private fun checkForBudgetWarnings() {
        val budget = expenseViewModel.budget.value
        val total = expenseViewModel.getTotalExpenses()

        if (budget != null && total > budget.monthlyBudget && !budgetWarningShown) {
            budgetWarningShown = true
            showBudgetWarning(total, budget.monthlyBudget)
        } else if (budget != null && total <= budget.monthlyBudget) {
            budgetWarningShown = false
        }
    }

    private fun showBudgetWarning(total: Double, budget: Double) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Budget Exceeded!")
            .setMessage("You've spent $${"%.2f".format(total)} which exceeds your budget of $${"%.2f".format(budget)}. Would you like AI advice?")
            .setPositiveButton("Get AI Advice") { _, _ ->
                expenseViewModel.getAIAdvice()
            }
            .setNegativeButton("Later", null)
            .setOnDismissListener { budgetWarningShown = false }
            .show()
    }

    private fun showAIAdviceDialog(advice: String) {
        AlertDialog.Builder(this)
            .setTitle("🤖 AI Financial Advice")
            .setMessage(advice)
            .setPositiveButton("OK", null)
            .setNeutralButton("Chat with AI") { _, _ ->
                startActivity(Intent(this, ChatActivity::class.java))
            }
            .show()
    }

    private fun showDeleteConfirmation(entry: Entry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete '${entry.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                expenseViewModel.deleteEntry(entry)
                Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ✅ Updated and improved version of addNewEntry()
    private fun addNewEntry() {
        val title = entryTitle.text.toString().trim()
        val amountText = entryAmount.text.toString().trim()
        val category = entryCategory.text.toString().trim()
        val type = if (entryTypeIncome.isChecked) "income" else "expense"

        if (title.isEmpty() || amountText.isEmpty() || category.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = try {
            amountText.toDouble()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount <= 0) {
            Toast.makeText(this, "Please enter a positive amount", Toast.LENGTH_SHORT).show()
            return
        }

        expenseViewModel.addEntry(title, amount, category, type)

        // Clear inputs after successful entry
        entryTitle.text.clear()
        entryAmount.text.clear()
        entryCategory.text.clear()
        entryTypeExpense.isChecked = true

        Toast.makeText(this, "Entry added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun updateTotalBalance() {
        val totalBalance = expenseViewModel.getTotalBalance()
        totalBalanceTextView.text = "Total Balance: $${"%.2f".format(totalBalance)}"
    }

    private fun applySavedTheme() {
        val isDarkMode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun isDarkModeEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)
    }

    private fun toggleTheme(isDarkMode: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, isDarkMode)
            .apply()
        recreate()
    }
}
