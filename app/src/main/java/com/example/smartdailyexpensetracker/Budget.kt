package com.example.smartdailyexpensetracker

data class Budget(
    val id: String = "",
    val userId: String = "",
    val monthlyBudget: Double = 0.0,
    val currentSpending: Double = 0.0,
    val monthYear: String = ""
)