package com.example.chat

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ChatRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var chatDao: ChatDao? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun init(context: Context) {
        if (chatDao == null) {
            val db = ChatDatabase.getDatabase(context.applicationContext)
            chatDao = db.chatDao()

            scope.launch {
                chatDao?.getAllMessages()?.collect { list ->
                    _messages.value = list
                }
            }
        }
    }

    fun addMessage(
        sender: String,
        content: String,
        isVoice: Boolean = false,
        actionResult: String? = null
    ) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        scope.launch {
            val msg = ChatMessage(
                sender = sender,
                content = trimmed,
                isVoice = isVoice,
                actionResult = actionResult
            )
            chatDao?.insertMessage(msg)
        }
    }

    fun clearAll() {
        scope.launch {
            chatDao?.clearAll()
        }
    }
}
