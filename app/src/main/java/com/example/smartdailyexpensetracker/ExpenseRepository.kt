package com.example.smartdailyexpensetracker

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val expensesCollection = db.collection("expenses")
    private val budgetsCollection = db.collection("budgets")
    private val chatCollection = db.collection("chats")

    companion object {
        private const val TAG = "ExpenseRepository"
    }

    private fun getCurrentMonthYear(): String {
        val format = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return format.format(Date())
    }

    // Expense methods
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
                updateBudgetSpending(currentUser.uid, expense.amount)
                result.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding expense", e)
            ""
        }
    }

    suspend fun updateExpense(expense: Expense, oldAmount: Double? = null): Boolean {
        return try {
            val expenseData = hashMapOf(
                "title" to expense.title,
                "amount" to expense.amount,
                "date" to expense.date,
                "category" to expense.category,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            expensesCollection.document(expense.id).update(expenseData.toMap()).await()

            if (oldAmount != null && oldAmount != expense.amount) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val difference = expense.amount - oldAmount
                    updateBudgetSpending(currentUser.uid, difference)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating expense", e)
            false
        }
    }

    suspend fun deleteExpense(expense: Expense): Boolean {
        return try {
            expensesCollection.document(expense.id).delete().await()

            val currentUser = auth.currentUser
            if (currentUser != null) {
                updateBudgetSpending(currentUser.uid, -expense.amount)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting expense", e)
            false
        }
    }

    // Budget methods
    suspend fun setMonthlyBudget(amount: Double): Boolean {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                false
            } else {
                val monthYear = getCurrentMonthYear()
                val budgetData = hashMapOf(
                    "userId" to currentUser.uid,
                    "monthlyBudget" to amount,
                    "currentSpending" to 0.0,
                    "monthYear" to monthYear,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                budgetsCollection.document("${currentUser.uid}_$monthYear")
                    .set(budgetData)
                    .await()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting budget", e)
            false
        }
    }

    suspend fun getCurrentBudget(): Budget? {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                null
            } else {
                val monthYear = getCurrentMonthYear()
                val document = budgetsCollection.document("${currentUser.uid}_$monthYear").get().await()

                if (document.exists()) {
                    Budget(
                        id = document.id,
                        userId = document.getString("userId") ?: "",
                        monthlyBudget = document.getDouble("monthlyBudget") ?: 0.0,
                        currentSpending = document.getDouble("currentSpending") ?: 0.0,
                        monthYear = document.getString("monthYear") ?: monthYear
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting budget", e)
            null
        }
    }

    private suspend fun updateBudgetSpending(userId: String, amount: Double) {
        try {
            val monthYear = getCurrentMonthYear()
            val budgetDoc = budgetsCollection.document("${userId}_$monthYear")
            val document = budgetDoc.get().await()

            if (document.exists()) {
                // Instead of relying on currentSpending, we'll recalculate from expenses
                val expenses = getExpensesForCurrentMonth(userId, monthYear)
                val currentSpending = expenses.sumOf { it.amount }

                budgetDoc.update("currentSpending", currentSpending)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating budget spending", e)
        }
    }
    // Add this helper function:
    private suspend fun getExpensesForCurrentMonth(userId: String, monthYear: String): List<Expense> {
        return try {
            val result = expensesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            result.documents.mapNotNull { document ->
                val expenseDate = document.getDate("date") ?: return@mapNotNull null
                val expenseMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(expenseDate)

                if (expenseMonthYear == monthYear) {
                    Expense(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        amount = document.getDouble("amount") ?: 0.0,
                        date = expenseDate,
                        category = document.getString("category") ?: "",
                        userId = document.getString("userId") ?: ""
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting expenses for current month", e)
            emptyList()
        }
    }

    // Chat methods
    // In the saveChatMessage function, fix the timestamp issue:
    suspend fun saveChatMessage(message: ChatMessage): Boolean {
        return try {
            val chatData = hashMapOf(
                "userId" to message.userId,
                "message" to message.message,
                "isUser" to message.isUser,
                "timestamp" to FieldValue.serverTimestamp() // Use server timestamp instead of local
            )

            chatCollection.document(message.userId)
                .collection("messages")
                .add(chatData)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat message", e)
            false
        }
    }

    // Fix the getChatMessages function to handle server timestamps:
    suspend fun getChatMessages(userId: String): List<ChatMessage> {
        return try {
            val result = chatCollection.document(userId)
                .collection("messages")
                .orderBy("timestamp")
                .get()
                .await()

            result.documents.map { document ->
                // Handle server timestamp conversion
                val timestamp = document.getDate("timestamp") ?: Date()

                ChatMessage(
                    userId = document.getString("userId") ?: "",
                    message = document.getString("message") ?: "",
                    isUser = document.getBoolean("isUser") ?: true,
                    timestamp = timestamp
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat messages", e)
            emptyList()
        }
    }
}