package com.qcb.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import com.qcb.keepaccounts.data.repository.ChatRepository
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiChatRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val pagedMessages: Flow<PagingData<AiChatRecord>> =
        Pager(PagingConfig(pageSize = 20)) {
            chatRepository.getPagedMessages()
        }.flow
            .map { pagingData ->
                pagingData.map { entity -> entity.toPagedChatRecord() }
            }
            .cachedIn(viewModelScope)

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

    fun deleteSelectedMessages(ids: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteSelectedMessages(ids)
        }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.clearChat()
        }
    }

    private fun ChatMessageEntity.toPagedChatRecord(): AiChatRecord {
        return AiChatRecord(
            id = id,
            timestamp = timestamp,
            role = role,
            content = content,
            isReceipt = isReceipt,
            transactionId = transactionId,
            transactionIds = listOfNotNull(transactionId),
            receiptRecordTimestamp = null,
            receiptType = null,
            receiptSummary = null,
        )
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
