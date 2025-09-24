package com.example.smartdailyexpensetracker

import java.util.Date

data class Expense(
    val id: String = "",  // Change to String for Firestore document ID
    val title: String,
    val amount: Double,
    val date: Date,
    val category: String,
    val userId: String = ""  // To associate expenses with users
) {
    // Add a secondary constructor for convenience
    constructor(title: String, amount: Double, date: Date, category: String, userId: String) :
            this("", title, amount, date, category, userId)
}