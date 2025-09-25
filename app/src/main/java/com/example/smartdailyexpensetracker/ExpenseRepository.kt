package com.example.smartdailyexpensetracker

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseRepository {
    private val db by lazy { Firebase.firestore }
    private val auth by lazy { Firebase.auth }
    private val entriesCollection get() = db.collection("entries")
    private val budgetsCollection get() = db.collection("budgets")
    private val chatCollection get() = db.collection("chats")

    companion object {
        private const val TAG = "ExpenseRepository"
    }

    private fun getCurrentMonthYear(): String {
        val format = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return format.format(Date())
    }

    suspend fun getEntries(): List<Entry> {
        return try {
            val currentUser = auth.currentUser ?: return emptyList()

            val result = entriesCollection
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()

            result.documents.map { document ->
                Entry(
                    id = document.id,
                    title = document.getString("title") ?: "",
                    amount = document.getDouble("amount") ?: 0.0,
                    date = document.getDate("date") ?: Date(),
                    category = document.getString("category") ?: "",
                    type = document.getString("type") ?: "expense",
                    userId = document.getString("userId") ?: ""
                )
            }.sortedByDescending { it.date }
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Get entries cancelled")
            } else {
                Log.e(TAG, "Error getting entries", e)
                emptyList()
            }
        }
    }

    suspend fun addEntry(entry: Entry): String {
        return try {
            val currentUser = auth.currentUser ?: return ""

            val entryData = hashMapOf(
                "title" to entry.title,
                "amount" to entry.amount,
                "date" to entry.date,
                "category" to entry.category,
                "type" to entry.type,
                "userId" to currentUser.uid,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val result = entriesCollection.add(entryData).await()
            updateBudgetSpending(currentUser.uid)
            result.id
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Add entry cancelled")
            } else {
                Log.e(TAG, "Error adding entry", e)
                ""
            }
        }
    }

    suspend fun updateEntry(entry: Entry): Boolean {
        return try {
            val entryData = hashMapOf(
                "title" to entry.title,
                "amount" to entry.amount,
                "date" to entry.date,
                "category" to entry.category,
                "type" to entry.type,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            entriesCollection.document(entry.id).update(entryData.toMap()).await()
            updateBudgetSpending(entry.userId)
            true
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Update entry cancelled")
            } else {
                Log.e(TAG, "Error updating entry", e)
                false
            }
        }
    }

    suspend fun deleteEntry(entry: Entry): Boolean {
        return try {
            entriesCollection.document(entry.id).delete().await()
            updateBudgetSpending(entry.userId)
            true
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Delete entry cancelled")
            } else {
                Log.e(TAG, "Error deleting entry", e)
                false
            }
        }
    }

    suspend fun setMonthlyBudget(amount: Double): Boolean {
        return try {
            val currentUser = auth.currentUser ?: return false

            val monthYear = getCurrentMonthYear()
            val budgetData = hashMapOf(
                "userId" to currentUser.uid,
                "monthlyBudget" to amount,
                "monthYear" to monthYear,
                "createdAt" to FieldValue.serverTimestamp()
            )

            budgetsCollection.document("${currentUser.uid}_$monthYear")
                .set(budgetData)
                .await()
            true
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Set monthly budget cancelled")
            } else {
                Log.e(TAG, "Error setting budget", e)
                false
            }
        }
    }

    suspend fun getCurrentBudget(): Budget? {
        return try {
            val currentUser = auth.currentUser ?: return null

            val monthYear = getCurrentMonthYear()
            val document = budgetsCollection.document("${currentUser.uid}_$monthYear").get().await()

            if (document.exists()) {
                Budget(
                    id = document.id,
                    userId = document.getString("userId") ?: "",
                    monthlyBudget = document.getDouble("monthlyBudget") ?: 0.0,
                    currentSpending = 0.0, // We'll calculate this separately
                    monthYear = document.getString("monthYear") ?: monthYear
                )
            } else {
                null
            }
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Get current budget cancelled")
            } else {
                Log.e(TAG, "Error getting budget", e)
                null
            }
        }
    }

    private suspend fun updateBudgetSpending(userId: String) {
        try {
            val monthYear = getCurrentMonthYear()
            val budgetDoc = budgetsCollection.document("${userId}_$monthYear")
            val document = budgetDoc.get().await()

            if (document.exists()) {
                val entries = getEntriesForCurrentMonth(userId, monthYear)
                val currentSpending = entries.filter { it.type == "expense" }.sumOf { it.amount }

                budgetDoc.update("currentSpending", currentSpending)
            }
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Update budget spending cancelled")
            } else {
                Log.e(TAG, "Error updating budget spending", e)
            }
        }
    }

    private suspend fun getEntriesForCurrentMonth(userId: String, monthYear: String): List<Entry> {
        return try {
            val result = entriesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            result.documents.mapNotNull { document ->
                val entryDate = document.getDate("date") ?: return@mapNotNull null
                val entryMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(entryDate)

                if (entryMonthYear == monthYear) {
                    Entry(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        amount = document.getDouble("amount") ?: 0.0,
                        date = entryDate,
                        category = document.getString("category") ?: "",
                        type = document.getString("type") ?: "expense",
                        userId = document.getString("userId") ?: ""
                    )
                } else null
            }
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Get entries for current month cancelled")
            } else {
                Log.e(TAG, "Error getting entries for current month", e)
                emptyList()
            }
        }
    }

    suspend fun saveChatMessage(message: ChatMessage): Boolean {
        return try {
            val currentUser = auth.currentUser ?: return false

            val chatData = hashMapOf(
                "userId" to currentUser.uid,
                "message" to message.message,
                "isUser" to message.isUser,
                "timestamp" to Timestamp.now()
            )

            chatCollection.document(currentUser.uid)
                .collection("messages")
                .add(chatData)
                .await()
            true
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Save chat message cancelled")
            } else {
                Log.e(TAG, "Error saving chat message", e)
                false
            }
        }
    }

    suspend fun getChatMessages(): List<ChatMessage> {
        return try {
            val currentUser = auth.currentUser ?: return emptyList()

            val result = chatCollection.document(currentUser.uid)
                .collection("messages")
                .orderBy("timestamp")
                .get()
                .await()

            result.documents.map { document ->
                ChatMessage(
                    userId = document.getString("userId") ?: "",
                    message = document.getString("message") ?: "",
                    isUser = document.getBoolean("isUser") ?: true,
                    timestamp = document.getTimestamp("timestamp") ?: Timestamp.now()
                )
            }
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("Get chat messages cancelled")
            } else {
                Log.e(TAG, "Error getting chat messages", e)
                emptyList()
            }
        }
    }

    private fun isCancellationException(throwable: Throwable?): Boolean {
        var t = throwable
        while (t != null) {
            if (t is CancellationException) {
                return true
            }
            t = t.cause
        }
        return false
    }
}