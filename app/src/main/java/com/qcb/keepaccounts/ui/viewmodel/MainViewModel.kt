package com.qcb.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val transactions: StateFlow<List<TransactionEntity>> =
        transactionRepository.observeTransactions().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        seedSampleTransactions()
    }

    private fun seedSampleTransactions() {
        viewModelScope.launch {
            transactionRepository.seedInitialTransactionsIfNeeded()
        }
    }

    fun addManualTransaction(
        type: Int,
        amount: Double,
        categoryName: String,
        categoryIcon: String,
        remark: String,
        recordTimestamp: Long,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            transactionRepository.insertTransaction(
                TransactionEntity(
                    type = type,
                    amount = amount,
                    categoryName = categoryName,
                    categoryIcon = categoryIcon,
                    remark = remark,
                    recordTimestamp = recordTimestamp,
                    createdTimestamp = now,
                ),
            )
        }
    }

    companion object {
        fun provideFactory(
            transactionRepository: TransactionRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(transactionRepository) as T
            }
        }
    }
}
