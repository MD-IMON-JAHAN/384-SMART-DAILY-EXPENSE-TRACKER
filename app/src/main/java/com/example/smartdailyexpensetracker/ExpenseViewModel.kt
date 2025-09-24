package com.example.smartdailyexpensetracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdailyexpensetracker.ai.GeminiAIService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.util.Date

class ExpenseViewModel : ViewModel() {
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
        loadExpenses()
        loadBudget()
        loadChatHistory()
    }

    fun loadExpenses() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val expensesList = repository.getExpenses()
                _expenses.value = expensesList
            } catch (e: Exception) {
                // Handle error
                _expenses.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadBudget() {
        viewModelScope.launch {
            try {
                _budget.value = repository.getCurrentBudget()
            } catch (e: Exception) {
                // Handle error
                _budget.value = null
            }
        }
    }

    fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                val messages = if (currentUser != null) {
                    repository.getChatMessages(currentUser.uid)
                } else {
                    repository.getChatMessages("system")
                }
                _chatMessages.value = messages
            } catch (e: Exception) {
                _chatMessages.value = emptyList()
            }
        }
    }

    // ADD THIS MISSING FUNCTION
    fun getAIAdvice() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentBudget = _budget.value
                if (currentBudget != null) {
                    val recentExpenses = _expenses.value?.take(5) ?: emptyList()
                    val advice = geminiService.getBudgetAdvice(
                        currentBudget.monthlyBudget,
                        currentBudget.currentSpending,
                        recentExpenses
                    )
                    _aiAdvice.value = advice
                } else {
                    _aiAdvice.value = "Please set a monthly budget first to get AI advice!"
                }
            } catch (e: Exception) {
                _aiAdvice.value = "Error getting AI advice: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ... rest of your existing functions (setMonthlyBudget, addExpense, sendChatMessage, etc.) ...

    fun setMonthlyBudget(amount: Double) {
        viewModelScope.launch {
            val success = repository.setMonthlyBudget(amount)
            if (success) {
                loadBudget()
            }
        }
    }

    fun addExpense(title: String, amount: Double, category: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        viewModelScope.launch {
            val expense = Expense(
                title = title,
                amount = amount,
                date = Date(),
                category = category,
                userId = currentUser.uid
            )

            val expenseId = repository.addExpense(expense)
            if (expenseId.isNotEmpty()) {
                loadExpenses()
                loadBudget()
            }
        }
    }

    fun updateExpense(expense: Expense, title: String, amount: Double, category: String) {
        viewModelScope.launch {
            val oldAmount = expense.amount
            val updatedExpense = expense.copy(
                title = title,
                amount = amount,
                category = category,
                date = Date()
            )

            val success = repository.updateExpense(updatedExpense, oldAmount)
            if (success) {
                loadExpenses()
                loadBudget()
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            val success = repository.deleteExpense(expense)
            if (success) {
                loadExpenses()
                loadBudget()
            }
        }
    }

    fun getTotalExpenses(): Double {
        return _expenses.value?.sumOf { it.amount } ?: 0.0
    }

    fun sendChatMessage(message: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Save user message first
                val userMessage = ChatMessage(
                    userId = currentUser.uid,
                    message = message,
                    isUser = true,
                    timestamp = Date()
                )

                val saved = repository.saveChatMessage(userMessage)
                if (saved) {
                    // Immediately add user message to UI without waiting for Firestore
                    val currentMessages = _chatMessages.value ?: emptyList()
                    _chatMessages.value = currentMessages + userMessage
                }

                // Show typing indicator
                val typingMessage = ChatMessage(
                    userId = "system",
                    message = "AI is thinking...",
                    isUser = false,
                    timestamp = Date()
                )

                val messagesWithTyping = (_chatMessages.value ?: emptyList()) + typingMessage
                _chatMessages.value = messagesWithTyping

                // Get AI response
                val aiResponse = geminiService.chatWithAI(message)

                // Remove typing indicator and add AI response
                val messagesWithoutTyping = (_chatMessages.value ?: emptyList())
                    .filter { it.message != "AI is thinking..." }

                val aiMessage = ChatMessage(
                    userId = currentUser.uid,
                    message = aiResponse,
                    isUser = false,
                    timestamp = Date()
                )

                val aiSaved = repository.saveChatMessage(aiMessage)
                if (aiSaved) {
                    _chatMessages.value = messagesWithoutTyping + aiMessage
                }

            } catch (e: Exception) {
                // Handle error - show error message
                val errorMessage = ChatMessage(
                    userId = "system",
                    message = "Sorry, I encountered an error. Please check your internet connection and try again.",
                    isUser = false,
                    timestamp = Date()
                )

                // Remove typing indicator and show error
                val messagesWithoutTyping = (_chatMessages.value ?: emptyList())
                    .filter { it.message != "AI is thinking..." }
                _chatMessages.value = messagesWithoutTyping + errorMessage

                // Also save error message to Firestore
                repository.saveChatMessage(errorMessage)

            } finally {
                _isLoading.value = false
            }
        }
    }

    // Add this public method to save welcome messages
    fun saveWelcomeMessage(chatMessage: ChatMessage) {
        viewModelScope.launch {
            repository.saveChatMessage(chatMessage)
            loadChatHistory()
        }
    }
}