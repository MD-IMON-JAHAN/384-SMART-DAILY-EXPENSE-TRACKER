package com.example.smartdailyexpensetracker

import java.util.Date

data class ChatMessage(
    val userId: String = "",
    val message: String = "",
    val isUser: Boolean = true,
    val timestamp: Date = Date()
)