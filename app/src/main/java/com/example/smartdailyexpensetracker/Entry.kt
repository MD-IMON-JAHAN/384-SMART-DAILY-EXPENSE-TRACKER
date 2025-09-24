package com.example.smartdailyexpensetracker

import java.util.Date

data class Entry(
    val id: String = "",
    val title: String,
    val amount: Double,
    val date: Date,
    val category: String,
    val type: String = "expense", // "expense" or "income"
    val userId: String = ""
)