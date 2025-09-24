package com.example.smartdailyexpensetracker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private val expenseViewModel: ExpenseViewModel by viewModels()

    private lateinit var expenseTitle: EditText
    private lateinit var expenseAmount: EditText
    private lateinit var expenseCategory: EditText
    private lateinit var addExpenseButton: Button
    private lateinit var expensesRecyclerView: RecyclerView
    private lateinit var totalExpensesTextView: TextView

    private lateinit var expenseAdapter: ExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupButtonListeners()
        setupObservers()
    }

    private fun initializeViews() {
        expenseTitle = findViewById(R.id.expenseTitle)
        expenseAmount = findViewById(R.id.expenseAmount)
        expenseCategory = findViewById(R.id.expenseCategory)
        addExpenseButton = findViewById(R.id.addExpenseButton)
        expensesRecyclerView = findViewById(R.id.expensesRecyclerView)
        totalExpensesTextView = findViewById(R.id.totalExpenses)
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter(emptyList())
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
            expenseAdapter = ExpenseAdapter(expenses)
            expensesRecyclerView.adapter = expenseAdapter
            updateTotalExpenses()
        }
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
        totalExpensesTextView.text = "Total: $$total"
    }
}