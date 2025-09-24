package com.example.smartdailyexpensetracker

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.LifecycleOwner // If needed for observers
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val expenseViewModel: ExpenseViewModel by viewModels { ExpenseViewModelFactory(application) } // FIXED: Use Factory with application

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initializeViews()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Assistant"
    }

    override fun onStart() {
        super.onStart()
        // FIXED: Call suspend function inside coroutine scope
        lifecycleScope.launch {
            expenseViewModel.loadChatHistory()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        chatRecyclerView.adapter = null
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
            val validMessages = messages
                .filter { it.message.isNotBlank() }
                .sortedBy { it.timestamp }

            chatAdapter = ChatAdapter(validMessages)
            chatRecyclerView.adapter = chatAdapter

            if (validMessages.isNotEmpty()) {
                chatRecyclerView.post {
                    chatRecyclerView.smoothScrollToPosition(validMessages.size - 1)
                }
            }
        }

        expenseViewModel.isLoading.observe(this) { isLoading ->
            loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !isLoading
            messageInput.isEnabled = !isLoading

            sendButton.text = if (isLoading) "Sending..." else "Send"
        }
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            sendMessage()
        }

        messageInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN) {
                sendMessage()
                true
            } else {
                false
            }
        }

        messageInput.requestFocus()

        messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validateInput()
            }
        })
    }

    private fun validateInput() {
        val message = messageInput.text.toString().trim()
        val isLoading = expenseViewModel.isLoading.value ?: false
        sendButton.isEnabled = message.isNotEmpty() && !isLoading
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isNotEmpty()) {
            expenseViewModel.sendChatMessage(message)
            messageInput.text.clear()
            sendButton.isEnabled = false
        }
    }
}