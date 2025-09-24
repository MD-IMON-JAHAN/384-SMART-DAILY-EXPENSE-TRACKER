package com.example.smartdailyexpensetracker.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAIService {

    private val apiKey = "AIzaSyDdGudeZ3-KgjDGjMetuxaaBjt0V6_IcKY"

    private val generativeModel: GenerativeModel

    init {
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
            }
        )
    }

    suspend fun sendMessageToGemini(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("GeminiAIService", "Sending to Gemini: ${userMessage.take(200)}")
                val response = generativeModel.generateContent(userMessage)

                val text = response.text?.trim()
                if (!text.isNullOrEmpty()) {
                    Log.d("GeminiAIService", "Gemini replied (len=${text.length})")
                    text
                } else {
                    Log.w("GeminiAIService", "Gemini returned empty text")
                    "I apologize, I didn't get a response. Please try again."
                }
            } catch (e: Exception) {
                Log.e("GeminiAIService", "Error sending message: ${e.message}", e)
                "I'm experiencing technical difficulties. Please try again later. (${e.message})"
            }
        }
    }

    suspend fun chatWithAI(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val contextPrompt = """
                    You are a helpful assistant in an expense-tracker app.
                    The user asks: "$userMessage"
                    Be concise (<= 300 words), helpful and friendly. If they ask finance-related questions, tailor the advice.
                """.trimIndent()

                Log.d("GeminiAIService", "Context prompt length: ${contextPrompt.length}")
                val response = generativeModel.generateContent(contextPrompt)
                val text = response.text?.trim()
                if (!text.isNullOrEmpty()) {
                    text
                } else {
                    Log.w("GeminiAIService", "chatWithAI produced empty response; falling back")
                    generateFallbackResponse(userMessage)
                }
            } catch (e: com.google.ai.client.generativeai.type.QuotaExceededException) {
                // FIXED: Specific handling for quota errors
                Log.e("GeminiAIService", "Quota exceeded: ${e.message}", e)
                "Sorry, the AI service quota has been exceeded for today. Please try again tomorrow or check your API plan."
            } catch (e: Exception) {
                Log.e("GeminiAIService", "chatWithAI error: ${e.message}", e)
                generateFallbackResponse(userMessage)
            }
        }
    }

    suspend fun getBudgetAdvice(monthlyBudget: Double, currentSpending: Double, recentExpenses: List<com.example.smartdailyexpensetracker.Expense>): String {
        return withContext(Dispatchers.IO) {
            try {
                val expensesText = if (recentExpenses.isNotEmpty()) {
                    "Recent expenses:\n" + recentExpenses.take(5).joinToString("\n") { expense ->
                        "- ${expense.title}: $${"%.2f".format(expense.amount)} (${expense.category})"
                    }
                } else {
                    "No recent expenses recorded."
                }

                val budgetPrompt = """
                    Act as a friendly financial advisor. Monthly budget: $${"%.2f".format(monthlyBudget)}.
                    Current spending: $${"%.2f".format(currentSpending)}.
                    $expensesText
                    Provide practical steps the user can take to improve spending.
                    Keep it short and actionable.
                """.trimIndent()

                val response = generativeModel.generateContent(budgetPrompt)
                val text = response.text?.trim()
                if (!text.isNullOrEmpty()) {
                    text
                } else {
                    generateBudgetFallbackAdvice(monthlyBudget, currentSpending)
                }
            } catch (e: com.google.ai.client.generativeai.type.QuotaExceededException) {
                // FIXED: Specific handling for quota errors
                Log.e("GeminiAIService", "Quota exceeded in budget advice: ${e.message}", e)
                "Sorry, the AI service quota has been exceeded. Falling back to basic advice: " + generateBudgetFallbackAdvice(monthlyBudget, currentSpending)
            } catch (e: Exception) {
                Log.e("GeminiAIService", "getBudgetAdvice error: ${e.message}", e)
                generateBudgetFallbackAdvice(monthlyBudget, currentSpending)
            }
        }
    }

    private fun generateFallbackResponse(userMessage: String): String {
        return "Sorry — I'm having trouble reaching the AI service right now. Please try again in a moment."
    }

    private fun generateBudgetFallbackAdvice(monthlyBudget: Double, currentSpending: Double): String {
        val remaining = monthlyBudget - currentSpending
        val percentage = if (monthlyBudget > 0) (currentSpending / monthlyBudget * 100).toInt() else 0
        return "Budget: $${"%.2f".format(monthlyBudget)}, Spent: $${"%.2f".format(currentSpending)}, Remaining: $${"%.2f".format(remaining)} ($percentage%)."
    }
}