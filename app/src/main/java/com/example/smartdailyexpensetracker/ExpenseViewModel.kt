package com.example.smartdailyexpensetracker

import androidx.lifecycle.*
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
                checkBudgetWarning()
            } catch (e: Exception) {
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
                checkBudgetWarning()
            } catch (e: Exception) {
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

    private fun checkBudgetWarning() {
        val currentBudget = _budget.value
        val currentSpending = getTotalExpenses()

        if (currentBudget != null && currentSpending > currentBudget.monthlyBudget) {
            getAIAdvice()
        }
    }

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

                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        val aiMessage = ChatMessage(
                            userId = currentUser.uid,
                            message = "💰 Budget Alert Advice:\n\n$advice",
                            isUser = false
                        )
                        repository.saveChatMessage(aiMessage)
                        loadChatHistory()
                    }
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

    /** Public helper to let Activities/Fragments clear the AI advice after it was handled */
    fun clearAIAdvice() {
        _aiAdvice.value = null
    }

    fun setMonthlyBudget(amount: Double) {
        viewModelScope.launch {
            val success = repository.setMonthlyBudget(amount)
            if (success) {
                loadBudget()
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val message = ChatMessage(
                        userId = currentUser.uid,
                        message = "✅ Monthly budget set to $${amount}",
                        isUser = false
                    )
                    repository.saveChatMessage(message)
                    loadChatHistory()
                }
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
                checkBudgetWarning()
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            val success = repository.deleteExpense(expense)
            if (success) {
                loadExpenses()
                loadBudget()
                checkBudgetWarning()
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
                val userMessage = ChatMessage(
                    userId = currentUser.uid,
                    message = message,
                    isUser = true
                )

                repository.saveChatMessage(userMessage)
                loadChatHistory()

                val aiResponse = geminiService.chatWithAI(message)

                val aiMessage = ChatMessage(
                    userId = currentUser.uid,
                    message = aiResponse,
                    isUser = false
                )

                repository.saveChatMessage(aiMessage)
                loadChatHistory()

            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    userId = currentUser.uid,
                    message = "Sorry, I encountered an error: ${e.message}",
                    isUser = false
                )
                repository.saveChatMessage(errorMessage)
                loadChatHistory()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveWelcomeMessage(chatMessage: ChatMessage) {
        viewModelScope.launch {
            repository.saveChatMessage(chatMessage)
            loadChatHistory()
        }
    }
}
