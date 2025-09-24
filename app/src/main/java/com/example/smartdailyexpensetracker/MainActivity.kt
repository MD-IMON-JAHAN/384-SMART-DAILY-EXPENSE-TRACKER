package com.example.smartdailyexpensetracker

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
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
        userItem?.title = currentUser?.email ?: "User"

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                signOut()
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

        expenseViewModel.isLoading.observe(this) { isLoading ->
            loadingProgressBar.visibility = if (isLoading) ProgressBar.VISIBLE else ProgressBar.GONE
        }
    }

    private fun showEditDialog(expense: Expense) {
        val dialog = EditExpenseDialog(
            expense,
            onUpdate = { expenseToUpdate, title, amount, category ->
                expenseViewModel.updateExpense(expenseToUpdate, title, amount, category)
                Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()
            },
            onDelete = { expenseToDelete ->
                showDeleteConfirmation(expenseToDelete)
            }
        )
        dialog.show(supportFragmentManager, "EditExpenseDialog")
    }

    private fun showDeleteConfirmation(expense: Expense) {
        android.app.AlertDialog.Builder(this)
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
        totalExpensesTextView.text = "Total: $${String.format("%.2f", total)}"
    }
}