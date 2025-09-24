package com.example.smartdailyexpensetracker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var loadingProgressBar: ProgressBar

    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        initializeViews()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Assistant"

        // Load chat history
        expenseViewModel.loadChatHistory()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(emptyList())
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecyclerView.adapter = chatAdapter
    }

    private fun setupObservers() {
        expenseViewModel.chatMessages.observe(this) { messages ->
            chatAdapter = ChatAdapter(messages)
            chatRecyclerView.adapter = chatAdapter
            if (messages.isNotEmpty()) {
                chatRecyclerView.post {
                    chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        expenseViewModel.isLoading.observe(this) { isLoading ->
            loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !isLoading
            messageInput.isEnabled = !isLoading

            if (isLoading) {
                sendButton.text = "Sending..."
            } else {
                sendButton.text = "Send"
            }
        }
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            sendMessage()
        }

        messageInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Auto-focus on message input
        messageInput.requestFocus()
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isNotEmpty()) {
            expenseViewModel.sendChatMessage(message)
            messageInput.text.clear()
        }
    }
}