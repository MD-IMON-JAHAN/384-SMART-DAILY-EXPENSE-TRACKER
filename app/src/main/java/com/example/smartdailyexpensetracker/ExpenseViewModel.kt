package com.example.smartdailyexpensetracker

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartdailyexpensetracker.GeminiAIService
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

    private val _entries = MutableLiveData<List<Entry>>(emptyList())
    val entries: LiveData<List<Entry>> = _entries

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
            loadEntries()
            loadBudget()
            loadChatHistory()
        }
    }

    // Explicit Unit return type avoids type-inference recursion problems
    suspend fun loadEntries(): Unit = executeWithLoading {
        val list = withContext(Dispatchers.IO) {
            repository.getEntries() // repository returns non-null List<Entry>
        }
        // Removed isActive check - coroutine cancellation is handled automatically
        _entries.postValue(list)
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
                    val recentEntries = _entries.value?.take(5) ?: emptyList()
                    val advice = withContext(Dispatchers.IO) {
                        geminiService.getBudgetAdvice(
                            currentBudget.monthlyBudget,
                            currentBudget.currentSpending,
                            recentEntries
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
                        loadChatHistory()
                    }
                } else {
                    Toast.makeText(getApplication(), "Failed to set budget", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun addEntry(title: String, amount: Double, category: String, type: String) {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            executeWithLoading {
                val entry = Entry(
                    title = title,
                    amount = amount,
                    date = Date(),
                    category = category,
                    type = type,
                    userId = currentUser.uid
                )

                val entryId = withContext(Dispatchers.IO) { repository.addEntry(entry) }
                // Removed isActive check
                if (entryId.isNotEmpty()) {
                    loadEntries()
                    loadBudget()
                } else {
                    Toast.makeText(getApplication(), "Failed to add entry", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateEntry(entry: Entry, title: String, amount: Double, category: String, type: String) {
        viewModelScope.launch {
            executeWithLoading {
                val oldAmount = entry.amount
                val updatedEntry = entry.copy(
                    title = title,
                    amount = amount,
                    category = category,
                    type = type,
                    date = Date()
                )

                val success = withContext(Dispatchers.IO) { repository.updateEntry(updatedEntry, oldAmount) }
                // Removed isActive check
                if (success) {
                    loadEntries()
                    loadBudget()
                    checkBudgetWarning()
                } else {
                    Toast.makeText(getApplication(), "Failed to update entry", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch {
            executeWithLoading {
                val success = withContext(Dispatchers.IO) { repository.deleteEntry(entry) }
                // Removed isActive check
                if (success) {
                    loadEntries()
                    loadBudget()
                    checkBudgetWarning()
                } else {
                    Toast.makeText(getApplication(), "Failed to delete entry", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getTotalExpenses(): Double {
        return _entries.value?.filter { it.type == "expense" }?.sumOf { it.amount } ?: 0.0
    }

    fun getTotalIncome(): Double {
        return _entries.value?.filter { it.type == "income" }?.sumOf { it.amount } ?: 0.0
    }

    fun getTotalBalance(): Double {
        return getTotalIncome() - getTotalExpenses()
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