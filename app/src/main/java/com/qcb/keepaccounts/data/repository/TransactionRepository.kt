package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
) {

    fun observeTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observeAllTransactions()

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransactionById(
        id: Long,
        type: Int,
        amount: Double,
        categoryName: String,
        categoryIcon: String,
        remark: String,
        recordTimestamp: Long,
    ) {
        transactionDao.updateTransactionById(
            id = id,
            type = type,
            amount = amount,
            categoryName = categoryName,
            categoryIcon = categoryIcon,
            remark = remark,
            recordTimestamp = recordTimestamp,
        )
    }

    suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteTransactionById(id)
    }
}
