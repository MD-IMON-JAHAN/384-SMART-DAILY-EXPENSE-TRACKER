package com.example.smartdailyexpensetracker.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

class GeminiAIService {

    // Your API key
    private val apiKey = "AIzaSyDdGudeZ3-KgjDGjMetuxaaBjt0V6_IcKY"

    // Configure Gemini model
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
        }
    )

    /**
     * Sends user input to Gemini and gets the AI-generated response.
     */
    suspend fun sendMessageToGemini(userMessage: String): String {
        return try {
            val response = generativeModel.generateContent(userMessage)

            response.text?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "I apologize, but I didn't receive a response. Please try again."
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error sending message: ${e.message}", e)
            "I'm experiencing technical difficulties. Please check your internet connection and try again. Error: ${e.message}"
        }
    }

    /**
     * Universal chat method that handles any type of question
     */
    suspend fun chatWithAI(userMessage: String): String {
        return try {
            // Create a context-aware prompt for better responses
            val contextPrompt = """
                You are a helpful, knowledgeable AI assistant that can answer questions on any topic. 
                The user is asking: "$userMessage"
                
                Please provide a thoughtful, accurate, and engaging response. 
                If the question is about finance or budgeting, you can provide specialized advice since you're integrated in an expense tracker app.
                For other topics, be informative and helpful.
                
                Keep your response under 300 words and maintain a conversational tone.
            """.trimIndent()

            val response = generativeModel.generateContent(contextPrompt)

            response.text?.trim().takeUnless { it.isNullOrEmpty() }
                ?: generateFallbackResponse(userMessage)
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error in chatWithAI: ${e.message}", e)
            generateFallbackResponse(userMessage)
        }
    }

    /**
     * Budget-specific advice method
     */
    suspend fun getBudgetAdvice(monthlyBudget: Double, currentSpending: Double, recentExpenses: List<com.example.smartdailyexpensetracker.Expense>): String {
        return try {
            val expensesText = if (recentExpenses.isNotEmpty()) {
                "Recent expenses:\n" + recentExpenses.take(5).joinToString("\n") { expense ->
                    "- ${expense.title}: $${expense.amount} (${expense.category})"
                }
            } else {
                "No recent expenses recorded."
            }

            val budgetPrompt = """
                Act as a financial advisor. The user has a monthly budget of $${monthlyBudget} 
                and has currently spent $${currentSpending}. 
                ${if (currentSpending > monthlyBudget) "WARNING: They have exceeded their budget!" else ""}
                
                $expensesText
                
                Provide specific, actionable advice in a friendly tone. Focus on practical steps they can take.
            """.trimIndent()

            val response = generativeModel.generateContent(budgetPrompt)

            response.text?.trim().takeUnless { it.isNullOrEmpty() }
                ?: generateBudgetFallbackAdvice(monthlyBudget, currentSpending)
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error in getBudgetAdvice: ${e.message}", e)
            generateBudgetFallbackAdvice(monthlyBudget, currentSpending)
        }
    }

    private fun generateFallbackResponse(userMessage: String): String {
        return """
            Hello! I'm your AI assistant. 😊
            
            I'd love to help you with your question about "${userMessage.take(50)}...", but I'm currently having some technical issues.
            
            In the meantime, here's what I can typically help with:
            • Answering questions on any topic
            • Providing information and explanations
            • Creative writing and ideas
            • Technical and educational help
            • Personal advice and guidance
            • Financial planning and budgeting
            
            Please try again in a moment, or rephrase your question. I'm here to help! 🌟
        """.trimIndent()
    }

    private fun generateBudgetFallbackAdvice(monthlyBudget: Double, currentSpending: Double): String {
        val remaining = monthlyBudget - currentSpending
        val percentage = (currentSpending / monthlyBudget * 100).toInt()

        return """
            💰 **Budget Summary**
            
            Based on your expense tracker data:
            • Monthly Budget: $${"%.2f".format(monthlyBudget)}
            • Current Spending: $${"%.2f".format(currentSpending)}
            • Remaining: $${"%.2f".format(remaining)}
            • Budget Used: $percentage%
            
            ${if (currentSpending > monthlyBudget) "⚠️ You've exceeded your budget! Consider reviewing recent expenses." else "📊 You're on track with your budget!"}
            
            Tip: Regular tracking helps maintain financial health! 💪
        """.trimIndent()
    }
}