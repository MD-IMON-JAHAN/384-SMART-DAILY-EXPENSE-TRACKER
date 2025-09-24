package com.example.smartdailyexpensetracker

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Date

class ExpenseRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val expensesCollection = db.collection("expenses")

    companion object {
        private const val TAG = "ExpenseRepository"
    }

    // Get all expenses for the current user
    suspend fun getExpenses(): List<Expense> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                emptyList()
            } else {
                val result = expensesCollection
                    .whereEqualTo("userId", currentUser.uid)
                    .get()
                    .await()

                result.documents.map { document ->
                    Expense(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        amount = document.getDouble("amount") ?: 0.0,
                        date = document.getDate("date") ?: Date(),
                        category = document.getString("category") ?: "",
                        userId = document.getString("userId") ?: ""
                    )
                }.sortedByDescending { it.date }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting expenses", e)
            emptyList()
        }
    }

    // Add a new expense
    suspend fun addExpense(expense: Expense): String {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                ""
            } else {
                val expenseData = hashMapOf(
                    "title" to expense.title,
                    "amount" to expense.amount,
                    "date" to expense.date,
                    "category" to expense.category,
                    "userId" to currentUser.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                val result = expensesCollection.add(expenseData).await()
                result.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding expense", e)
            ""
        }
    }

    // Update an existing expense
    suspend fun updateExpense(expense: Expense): Boolean {
        return try {
            val expenseData = hashMapOf(
                "title" to expense.title,
                "amount" to expense.amount,
                "date" to expense.date,
                "category" to expense.category,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            expensesCollection.document(expense.id).update(expenseData.toMap()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating expense", e)
            false
        }
    }

    // Delete an expense
    suspend fun deleteExpense(expenseId: String): Boolean {
        return try {
            expensesCollection.document(expenseId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting expense", e)
            false
        }
    }
}