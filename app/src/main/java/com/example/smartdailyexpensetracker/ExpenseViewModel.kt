package com.example.smartdailyexpensetracker

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartdailyexpensetracker.ai.GeminiAIService
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Date

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository()
    private val geminiService = GeminiAIService()
    private val auth = Firebase.auth

    private val _expenses = MutableLiveData<List<Expense>>(emptyList())
    val expenses: LiveData<List<Expense>> = _expenses

    private val _budget = MutableLiveData<Budget?>(null)
    val budget: LiveData<Budget?> = _budget

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _aiAdvice = MutableLiveData<String?>(null)
    val aiAdvice: LiveData<String?> = _aiAdvice

    private val _chatMessages = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatMessages: LiveData<List<ChatMessage>> = _chatMessages

    init {
        // Kick off initial loading
        loadData()
        // If your repository defines enableNetwork(), keep it; otherwise remove this line
        try {
            repository.enableNetwork()
        } catch (ignored: Exception) { /* optional */ }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            repository.clear()
        } catch (ignored: Exception) { /* optional */ }
    }

    private fun loadData() {
        viewModelScope.launch {
            loadExpenses()
            loadBudget()
            loadChatHistory()
        }
    }

    // Explicit Unit return type avoids type-inference recursion problems
    suspend fun loadExpenses(): Unit = executeWithLoading {
        val list = withContext(Dispatchers.IO) {
            repository.getExpenses() // repository returns non-null List<Expense>
        }
        // Removed isActive check - coroutine cancellation is handled automatically
        _expenses.postValue(list)
        checkBudgetWarning()
    }

    suspend fun loadBudget(): Unit = executeWithLoading {
        try {
            val b = withContext(Dispatchers.IO) {
                repository.getCurrentBudget()
            }
            // Removed isActive check
            _budget.postValue(b)
            checkBudgetWarning()
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Error loading budget: ${e.message}", e)
            // Show user-friendly message
            _budget.postValue(null)
            Toast.makeText(getApplication(), "Error loading budget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun loadChatHistory(): Unit = executeWithLoading {
        val currentUser = auth.currentUser
        val messages = withContext(Dispatchers.IO) {
            if (currentUser != null) {
                repository.getChatMessages(currentUser.uid)
            } else {
                repository.getChatMessages("system")
            }
        }
        // Removed isActive check
        _chatMessages.postValue(messages)
    }

    private fun checkBudgetWarning() {
        val currentBudget = _budget.value
        val currentSpending = getTotalExpenses()

        if (currentBudget != null && currentSpending > currentBudget.monthlyBudget) {
            getAIAdvice()
        }
    }

    fun getAIAdvice() {
        viewModelScope.launch {
            executeWithLoading {
                val currentBudget = _budget.value
                if (currentBudget != null) {
                    val recentExpenses = _expenses.value?.take(5) ?: emptyList()
                    val advice = withContext(Dispatchers.IO) {
                        geminiService.getBudgetAdvice(
                            currentBudget.monthlyBudget,
                            currentBudget.currentSpending,
                            recentExpenses
                        )
                    }
                    // Removed isActive check
                    _aiAdvice.postValue(advice)

                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        val aiMessage = ChatMessage(
                            userId = currentUser.uid,
                            message = "💰 Budget Alert Advice:\n\n$advice",
                            isUser = false,
                            timestamp = Timestamp.now()
                        )
                        withContext(Dispatchers.IO) { repository.saveChatMessage(aiMessage) }
                        // Removed isActive check
                        loadChatHistory()
                    }
                } else {
                    _aiAdvice.postValue("Please set a monthly budget first to get AI advice!")
                }
            }
        }
    }

    fun clearAIAdvice() {
        _aiAdvice.postValue(null)
    }

    fun setMonthlyBudget(amount: Double) {
        viewModelScope.launch {
            executeWithLoading {
                val success = withContext(Dispatchers.IO) { repository.setMonthlyBudget(amount) }
                if (success) {
                    loadBudget()
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        val message = ChatMessage(
                            userId = currentUser.uid,
                            message = "✅ Monthly budget set to $${"%.2f".format(amount)}",
                            isUser = false,
                            timestamp = Timestamp.now()
                        )
                        withContext(Dispatchers.IO) { repository.saveChatMessage(message) }
                        // Removed isActive check
                        loadChatHistory()
                    }
                } else {
                    Toast.makeText(getApplication(), "Failed to set budget", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun addExpense(title: String, amount: Double, category: String) {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            executeWithLoading {
                val expense = Expense(
                    title = title,
                    amount = amount,
                    date = Date(), // java.util.Date
                    category = category,
                    userId = currentUser.uid
                )

                val expenseId = withContext(Dispatchers.IO) { repository.addExpense(expense) }
                // Removed isActive check
                if (expenseId.isNotEmpty()) {
                    loadExpenses()
                    loadBudget()
                } else {
                    Toast.makeText(getApplication(), "Failed to add expense", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateExpense(expense: Expense, title: String, amount: Double, category: String) {
        viewModelScope.launch {
            executeWithLoading {
                val oldAmount = expense.amount
                val updatedExpense = expense.copy(
                    title = title,
                    amount = amount,
                    category = category,
                    date = Date()
                )

                val success = withContext(Dispatchers.IO) { repository.updateExpense(updatedExpense, oldAmount) }
                // Removed isActive check
                if (success) {
                    loadExpenses()
                    loadBudget()
                    checkBudgetWarning()
                } else {
                    Toast.makeText(getApplication(), "Failed to update expense", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            executeWithLoading {
                val success = withContext(Dispatchers.IO) { repository.deleteExpense(expense) }
                // Removed isActive check
                if (success) {
                    loadExpenses()
                    loadBudget()
                    checkBudgetWarning()
                } else {
                    Toast.makeText(getApplication(), "Failed to delete expense", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getTotalExpenses(): Double {
        return _expenses.value?.sumOf { it.amount } ?: 0.0
    }

    fun sendChatMessage(message: String) {
        val currentUser = auth.currentUser ?: return
        // Prevent concurrent sends
        if (_isLoading.value == true) return

        viewModelScope.launch {
            executeWithLoading {
                // Save user's message
                val userMessage = ChatMessage(
                    userId = currentUser.uid,
                    message = message,
                    isUser = true,
                    timestamp = Timestamp.now()
                )
                withContext(Dispatchers.IO) { repository.saveChatMessage(userMessage) }
                // Removed isActive check
                loadChatHistory()

                // Get AI response with timeout
                val aiResponse = try {
                    withTimeout(30_000) {
                        withContext(Dispatchers.IO) { geminiService.chatWithAI(message) }
                    }.takeIf { it.isNotBlank() } ?: "Sorry, I couldn't generate a response right now."
                } catch (e: Exception) {
                    "Sorry, the request timed out or failed. (${e.message})"
                }

                // Removed isActive check

                // Save AI message
                val aiMessage = ChatMessage(
                    userId = "ai",
                    message = aiResponse,
                    isUser = false,
                    timestamp = Timestamp.now()
                )
                withContext(Dispatchers.IO) { repository.saveChatMessage(aiMessage) }
                // Removed isActive check
                loadChatHistory()
            }
        }
    }

    /**
     * Runs the given suspend block while toggling loading state and catching errors.
     * Explicit Unit return type avoids type recursion errors Kotlin can show.
     */
    private suspend fun executeWithLoading(block: suspend () -> Unit): Unit {
        _isLoading.postValue(true)
        try {
            block()
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Error executing operation: ${e.message}", e)
            Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            _isLoading.postValue(false)
        }
    }
}