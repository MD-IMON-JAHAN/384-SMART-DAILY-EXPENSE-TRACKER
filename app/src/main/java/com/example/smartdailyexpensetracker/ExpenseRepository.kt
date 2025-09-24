package com.example.smartdailyexpensetracker

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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

    fun clear() {
        // Clear any cached data if needed
    }
    fun enableNetwork() {
        db.enableNetwork()
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
            Log.e(TAG, "Error getting entries", e)
            emptyList()
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
            updateBudgetSpending(currentUser.uid, entry.amount, entry.type)
            result.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding entry", e)
            ""
        }
    }

    suspend fun updateEntry(entry: Entry, oldAmount: Double? = null): Boolean {
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

            if (oldAmount != null && oldAmount != entry.amount) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val difference = entry.amount - oldAmount
                    updateBudgetSpending(currentUser.uid, difference, entry.type)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating entry", e)
            false
        }
    }

    suspend fun deleteEntry(entry: Entry): Boolean {
        return try {
            entriesCollection.document(entry.id).delete().await()

            val currentUser = auth.currentUser
            if (currentUser != null) {
                updateBudgetSpending(currentUser.uid, -entry.amount, entry.type)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting entry", e)
            false
        }
    }

    suspend fun setMonthlyBudget(amount: Double): Boolean {
        return try {
            val currentUser = auth.currentUser ?: return false

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
        } catch (e: Exception) {
            Log.e(TAG, "Error setting budget", e)
            false
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
                    currentSpending = document.getDouble("currentSpending") ?: 0.0,
                    monthYear = document.getString("monthYear") ?: monthYear
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting budget", e)
            null
        }
    }

    private suspend fun updateBudgetSpending(userId: String, amount: Double, type: String) {
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
            Log.e(TAG, "Error updating budget spending", e)
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
            Log.e(TAG, "Error getting entries for current month", e)
            emptyList()
        }
    }

    suspend fun saveChatMessage(message: ChatMessage): Boolean {
        return try {
            val chatData = hashMapOf(
                "userId" to message.userId,
                "message" to message.message,
                "isUser" to message.isUser,
                "timestamp" to Timestamp.now()  // Client-side for immediate consistency
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

    suspend fun getChatMessages(userId: String): List<ChatMessage> {
        return try {
            val result = chatCollection.document(userId)
                .collection("messages")
                .orderBy("timestamp")
                .get()
                .await()

            result.documents.map { document ->
                // FIXED: Handle multiple possible types for 'timestamp' to avoid cast exceptions
                val rawTimestamp = document.get("timestamp")
                val timestamp: Timestamp = when (rawTimestamp) {
                    is Timestamp -> rawTimestamp
                    is Date -> Timestamp(rawTimestamp)
                    is Long -> Timestamp(rawTimestamp / 1000, ((rawTimestamp % 1000) * 1000000).toInt())
                    is String -> try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        val date = dateFormat.parse(rawTimestamp) ?: Date()
                        Timestamp(date)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse string timestamp: $rawTimestamp", e)
                        Timestamp.now()
                    }
                    null -> Timestamp.now()
                    else -> {
                        Log.w(TAG, "Unexpected timestamp type: ${rawTimestamp.javaClass.simpleName}")
                        Timestamp.now()
                    }
                }

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