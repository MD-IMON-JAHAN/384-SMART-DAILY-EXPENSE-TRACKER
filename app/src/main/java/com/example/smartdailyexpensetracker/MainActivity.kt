package com.example.smartdailyexpensetracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase


class MainActivity : AppCompatActivity() {
    private val expenseViewModel: ExpenseViewModel by viewModels()
    private val auth = Firebase.auth

    private lateinit var expenseTitle: EditText
    private lateinit var expenseAmount: EditText
    private lateinit var expenseCategory: EditText
    private lateinit var addExpenseButton: Button
    private lateinit var expensesRecyclerView: RecyclerView
    private lateinit var totalExpensesTextView: TextView
    private lateinit var budgetStatusTextView: TextView
    private lateinit var setBudgetButton: Button
    private lateinit var aiAdviceButton: Button
    private lateinit var loadingProgressBar: ProgressBar

    private lateinit var expenseAdapter: ExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check if user is logged in
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

        // Add user email to menu
        val userItem = menu.findItem(R.id.action_user_email)
        val currentUser = auth.currentUser
        userItem.title = currentUser?.email ?: "User"

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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun initializeViews() {
        expenseTitle = findViewById(R.id.expenseTitle)
        expenseAmount = findViewById(R.id.expenseAmount)
        expenseCategory = findViewById(R.id.expenseCategory)
        addExpenseButton = findViewById(R.id.addExpenseButton)
        expensesRecyclerView = findViewById(R.id.expensesRecyclerView)
        totalExpensesTextView = findViewById(R.id.totalExpenses)
        budgetStatusTextView = findViewById(R.id.budgetStatusText)
        setBudgetButton = findViewById(R.id.setBudgetButton)
        aiAdviceButton = findViewById(R.id.aiAdviceButton)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter(
            emptyList(),
            onItemClick = { expense -> showEditDialog(expense) },
            onItemLongClick = { expense ->
                showDeleteConfirmation(expense)
                true
            }
        )
        expensesRecyclerView.layoutManager = LinearLayoutManager(this)
        expensesRecyclerView.adapter = expenseAdapter
    }

    private fun setupButtonListeners() {
        addExpenseButton.setOnClickListener {
            addNewExpense()
        }

        setBudgetButton.setOnClickListener {
            showBudgetDialog()
        }

        aiAdviceButton.setOnClickListener {
            expenseViewModel.getAIAdvice()
        }
    }

    private fun setupObservers() {
        expenseViewModel.expenses.observe(this) { expenses ->
            expenseAdapter = ExpenseAdapter(
                expenses,
                onItemClick = { expense -> showEditDialog(expense) },
                onItemLongClick = { expense ->
                    showDeleteConfirmation(expense)
                    true
                }
            )
            expensesRecyclerView.adapter = expenseAdapter
            updateTotalExpenses()
        }

        expenseViewModel.budget.observe(this) { budget ->
            updateBudgetStatus(budget)
        }

        expenseViewModel.isLoading.observe(this) { isLoading ->
            loadingProgressBar.visibility = if (isLoading) ProgressBar.VISIBLE else ProgressBar.GONE
        }

        expenseViewModel.aiAdvice.observe(this) { advice ->
            advice?.let {
                showAIAdviceDialog(it)
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
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateBudgetStatus(budget: Budget?) {
        if (budget == null) {
            budgetStatusTextView.text = "Monthly Budget: Not set"
            budgetStatusTextView.setTextColor(Color.GRAY)
        } else {
            val total = expenseViewModel.getTotalExpenses()
            val remaining = budget.monthlyBudget - total
            val percentage = (total / budget.monthlyBudget) * 100

            budgetStatusTextView.text =
                "Budget: $${"%.2f".format(total)} / $${"%.2f".format(budget.monthlyBudget)} | " +
                        "Remaining: $${"%.2f".format(remaining)} (${"%.1f".format(percentage)}%)"

            when {
                percentage > 90 -> budgetStatusTextView.setTextColor(Color.RED)
                percentage > 75 -> budgetStatusTextView.setTextColor(Color.parseColor("#FFA500")) // Orange
                else -> budgetStatusTextView.setTextColor(Color.GREEN)
            }

            // Show warning if budget exceeded
            if (total > budget.monthlyBudget) {
                showBudgetWarning(total, budget.monthlyBudget)
            }
        }
    }

    private fun showBudgetWarning(total: Double, budget: Double) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Budget Exceeded!")
            .setMessage("You've spent $${"%.2f".format(total)} which exceeds your budget of $${"%.2f".format(budget)}. " +
                    "Would you like AI advice?")
            .setPositiveButton("Get AI Advice") { _, _ ->
                expenseViewModel.getAIAdvice()
            }
            .setNegativeButton("Later", null)
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

    private fun showEditDialog(expense: Expense) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_expense, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.editTitle)
        val amountInput = dialogView.findViewById<EditText>(R.id.editAmount)
        val categoryInput = dialogView.findViewById<EditText>(R.id.editCategory)

        // Pre-fill with existing data
        titleInput.setText(expense.title)
        amountInput.setText(expense.amount.toString())
        categoryInput.setText(expense.category)

        AlertDialog.Builder(this)
            .setTitle("Edit Expense")
            .setView(dialogView)
            .setPositiveButton("Update") { dialog, _ ->
                val newTitle = titleInput.text.toString().trim()
                val newAmountText = amountInput.text.toString().trim()
                val newCategory = categoryInput.text.toString().trim()

                if (newTitle.isEmpty() || newAmountText.isEmpty() || newCategory.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newAmount = try {
                    newAmountText.toDouble()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                expenseViewModel.updateExpense(expense, newTitle, newAmount, newCategory)
                Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Delete") { dialog, _ ->
                showDeleteConfirmation(expense)
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteConfirmation(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete '${expense.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                expenseViewModel.deleteExpense(expense)
                Toast.makeText(this, "Expense deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addNewExpense() {
        val title = expenseTitle.text.toString().trim()
        val amountText = expenseAmount.text.toString().trim()
        val category = expenseCategory.text.toString().trim()

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

        expenseViewModel.addExpense(title, amount, category)

        // Clear input fields
        expenseTitle.text.clear()
        expenseAmount.text.clear()
        expenseCategory.text.clear()

        Toast.makeText(this, "Expense added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun updateTotalExpenses() {
        val total = expenseViewModel.getTotalExpenses()
        totalExpensesTextView.text = "Total: $${"%.2f".format(total)}"
    }
}