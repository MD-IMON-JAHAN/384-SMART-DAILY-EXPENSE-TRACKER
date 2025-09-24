package com.example.smartdailyexpensetracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class ExpenseViewModel : ViewModel() {
    private val repository = ExpenseRepository()
    private val auth = Firebase.auth

    private val _expenses = MutableLiveData<List<Expense>>(emptyList())
    val expenses: LiveData<List<Expense>> = _expenses

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadExpenses()
    }

    fun loadExpenses() {
        _isLoading.value = true
        viewModelScope.launch {
            val expensesList = repository.getExpenses()
            _expenses.value = expensesList
            _isLoading.value = false
        }
    }

    fun addExpense(title: String, amount: Double, category: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User not logged in, can't save expense
            return
        }

        viewModelScope.launch {
            val expense = Expense(
                title = title,
                amount = amount,
                date = Date(),
                category = category,
                userId = currentUser.uid
            )

            val expenseId = repository.addExpense(expense)
            if (expenseId.isNotEmpty()) {
                // Reload expenses to get the updated list with proper IDs
                loadExpenses()
            }
        }
    }

    fun updateExpense(expense: Expense, title: String, amount: Double, category: String) {
        viewModelScope.launch {
            val updatedExpense = expense.copy(
                title = title,
                amount = amount,
                category = category,
                date = Date() // Update the date when edited
            )

            val success = repository.updateExpense(updatedExpense)
            if (success) {
                loadExpenses() // Reload to reflect changes
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            val success = repository.deleteExpense(expense.id)
            if (success) {
                loadExpenses() // Reload to reflect changes
            }
        }
    }

    fun getTotalExpenses(): Double {
        return _expenses.value?.sumOf { it.amount } ?: 0.0
    }
}