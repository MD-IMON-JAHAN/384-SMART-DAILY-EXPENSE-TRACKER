package com.example.smartdailyexpensetracker

import java.util.Date

data class Expense(
    val id: Int,
    val title: String,
    val amount: Double,
    val date: Date,
    val category: String
)