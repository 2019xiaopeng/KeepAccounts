package com.qcb.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.data.repository.TransactionRepository
import com.qcb.keepaccounts.ui.format.applyCurrentTimeToDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class LedgerFilterMode {
    MONTH,
    YEAR,
}

data class LedgerTimeSelection(
    val year: Int,
    val month: Int,
)

class MainViewModel(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val initialCalendar = Calendar.getInstance()

    private val _ledgerTimeSelection = MutableStateFlow(
        LedgerTimeSelection(
            year = initialCalendar.get(Calendar.YEAR),
            month = initialCalendar.get(Calendar.MONTH),
        ),
    )
    val ledgerTimeSelection: StateFlow<LedgerTimeSelection> = _ledgerTimeSelection

    private val _ledgerFilterMode = MutableStateFlow(LedgerFilterMode.MONTH)
    val ledgerFilterMode: StateFlow<LedgerFilterMode> = _ledgerFilterMode

    val transactions: StateFlow<List<TransactionEntity>> =
        transactionRepository.observeTransactions().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val selectedMonthTransactions: StateFlow<List<TransactionEntity>> =
        combine(transactions, ledgerTimeSelection) { all, selection ->
            all.filter { tx ->
                val cal = Calendar.getInstance().apply { timeInMillis = tx.recordTimestamp }
                cal.get(Calendar.YEAR) == selection.year &&
                    cal.get(Calendar.MONTH) == selection.month
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val selectedYearTransactions: StateFlow<List<TransactionEntity>> =
        combine(transactions, ledgerTimeSelection) { all, selection ->
            all.filter { tx ->
                val cal = Calendar.getInstance().apply { timeInMillis = tx.recordTimestamp }
                cal.get(Calendar.YEAR) == selection.year
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val filteredTransactions: StateFlow<List<TransactionEntity>> =
        combine(selectedMonthTransactions, selectedYearTransactions, ledgerFilterMode) { monthList, yearList, mode ->
            when (mode) {
                LedgerFilterMode.MONTH -> monthList
                LedgerFilterMode.YEAR -> yearList
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun updateLedgerTimeSelection(year: Int, month: Int) {
        val normalizedMonth = month.coerceIn(0, 11)
        val current = _ledgerTimeSelection.value
        if (current.year == year && current.month == normalizedMonth) return
        _ledgerTimeSelection.value = LedgerTimeSelection(year = year, month = normalizedMonth)
    }

    fun setLedgerFilterMode(mode: LedgerFilterMode) {
        if (_ledgerFilterMode.value == mode) return
        _ledgerFilterMode.value = mode
    }

    fun saveManualTransaction(
        transactionId: Long?,
        type: Int,
        amount: Double,
        categoryName: String,
        categoryIcon: String,
        remark: String,
        recordTimestamp: Long,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val effectiveRecordTimestamp = applyCurrentTimeToDate(recordTimestamp, now)
            if (transactionId != null && transactionId > 0L) {
                transactionRepository.updateTransactionById(
                    id = transactionId,
                    type = type,
                    amount = amount,
                    categoryName = categoryName,
                    categoryIcon = categoryIcon,
                    remark = remark,
                    recordTimestamp = effectiveRecordTimestamp,
                )
            } else {
                transactionRepository.insertTransaction(
                    TransactionEntity(
                        type = type,
                        amount = amount,
                        categoryName = categoryName,
                        categoryIcon = categoryIcon,
                        remark = remark,
                        recordTimestamp = effectiveRecordTimestamp,
                        createdTimestamp = now,
                    ),
                )
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransactionById(id)
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
