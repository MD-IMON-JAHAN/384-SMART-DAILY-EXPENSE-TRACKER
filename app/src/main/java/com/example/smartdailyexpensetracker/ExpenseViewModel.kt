package com.example.smartdailyexpensetracker

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            loadEntries()
            loadBudget()
            loadChatHistory()
        }
    }

    suspend fun loadEntries() {
        executeWithLoading {
            try {
                val list = withContext(Dispatchers.IO) {
                    repository.getEntries()
                }
                _entries.postValue(list)
                checkBudgetWarning()
            } catch (e: CancellationException) {
                // Re-throw cancellation exceptions to respect coroutine cancellation
                throw e
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error loading entries: ${e.message}", e)
                _entries.postValue(emptyList())
            }
        }
    }

    suspend fun loadBudget() {
        executeWithLoading {
            try {
                val b = withContext(Dispatchers.IO) {
                    repository.getCurrentBudget()
                }
                _budget.postValue(b)
                checkBudgetWarning()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error loading budget: ${e.message}", e)
                _budget.postValue(null)
            }
        }
    }

    suspend fun loadChatHistory() {
        executeWithLoading {
            try {
                val messages = withContext(Dispatchers.IO) {
                    repository.getChatMessages()
                }
                _chatMessages.postValue(messages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error loading chat history: ${e.message}", e)
                _chatMessages.postValue(emptyList())
            }
        }
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
                val updatedEntry = entry.copy(
                    title = title,
                    amount = amount,
                    category = category,
                    type = type
                )

                val success = withContext(Dispatchers.IO) { repository.updateEntry(updatedEntry) }
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
                loadChatHistory()

                // Get AI response
                val aiResponse = try {
                    withContext(Dispatchers.IO) { geminiService.chatWithAI(message) }
                } catch (e: Exception) {
                    "Sorry, I couldn't generate a response right now. (${e.message})"
                }

                // Save AI message
                val aiMessage = ChatMessage(
                    userId = currentUser.uid,
                    message = aiResponse,
                    isUser = false,
                    timestamp = Timestamp.now()
                )
                withContext(Dispatchers.IO) { repository.saveChatMessage(aiMessage) }
                loadChatHistory()
            }
        }
    }

    private suspend fun executeWithLoading(block: suspend () -> Unit) {
        _isLoading.postValue(true)
        try {
            block()
        } catch (e: CancellationException) {
            // Re-throw cancellation to properly handle coroutine lifecycle
            throw e
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                // Treat wrapped cancellations as normal (no logging/error UI)
                throw CancellationException("Operation cancelled")
            } else {
                Log.e("ExpenseViewModel", "Error executing operation: ${e.message}", e)
                Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            _isLoading.postValue(false)
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