package com.example.smartdailyexpensetracker

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CancellationException

class GeminiAIService {
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AIzaSyDdGudeZ3-KgjDGjMetuxaaBjt0V6_IcKY" // Replace with your actual API key
        )
    }

    suspend fun getBudgetAdvice(monthlyBudget: Double, currentSpending: Double, recentEntries: List<Entry>): String {
        return try {
            val prompt = """
                My monthly budget is \$${monthlyBudget} and I've spent \$${currentSpending} so far.
                Recent entries: ${recentEntries.joinToString { "${it.title}: \$${it.amount} (${it.category})" }}
                Give me some financial advice to manage my budget better.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            response.text ?: "No advice available"
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("AI advice generation cancelled")
            } else {
                Log.e("GeminiAIService", "Error getting advice", e)
                "Error getting AI advice"
            }
        }
    }

    suspend fun chatWithAI(message: String): String {
        return try {
            val response = model.generateContent(content { text(message) })
            response.text ?: "No response"
        } catch (e: Exception) {
            if (isCancellationException(e)) {
                throw CancellationException("AI chat cancelled")
            } else {
                Log.e("GeminiAIService", "Error in chat", e)
                "Error in AI chat"
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