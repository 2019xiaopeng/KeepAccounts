package com.qcb.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qcb.keepaccounts.data.repository.ChatRepository
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiChatRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val chatRecords: StateFlow<List<AiChatRecord>> =
        chatRepository.observeChatRecords().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun sendMessage(
        text: String,
        aiConfig: AiAssistantConfig,
        userName: String,
    ) {
        val userInput = text.trim()
        if (userInput.isBlank() || _isSending.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSending.value = true
            try {
                chatRepository.sendMessage(
                    userInput = userInput,
                    aiConfig = aiConfig,
                    userName = userName,
                )
            } finally {
                _isSending.value = false
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteMessage(messageId)
        }
    }

    companion object {
        fun provideFactory(
            chatRepository: ChatRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(chatRepository) as T
            }
        }
    }
}
