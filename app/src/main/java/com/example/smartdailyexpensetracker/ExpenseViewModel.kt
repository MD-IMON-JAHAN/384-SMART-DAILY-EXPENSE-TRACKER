package com.example.smartdailyexpensetracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Date

class ExpenseViewModel : ViewModel() {
    private val _expenses = MutableLiveData<MutableList<Expense>>(mutableListOf())
    val expenses: LiveData<MutableList<Expense>> = _expenses

    private var nextId = 1

    fun addExpense(title: String, amount: Double, category: String) {
        val currentList = _expenses.value ?: mutableListOf()
        currentList.add(Expense(nextId++, title, amount, Date(), category))
        _expenses.value = currentList
    }

    fun getTotalExpenses(): Double {
        return _expenses.value?.sumOf { it.amount } ?: 0.0
    }
}