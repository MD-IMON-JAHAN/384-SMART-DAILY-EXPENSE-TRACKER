package com.example.smartdailyexpensetracker

import com.google.firebase.Timestamp

data class ChatMessage(
    val userId: String = "",
    val message: String = "",
    val isUser: Boolean = true,
    val timestamp: Timestamp = Timestamp.now()
)