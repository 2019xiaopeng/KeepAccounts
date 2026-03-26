package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
) {

    fun observeTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observeAllTransactions()

    suspend fun seedInitialTransactionsIfNeeded() {
        if (transactionDao.countTransactions() > 0) return

        val now = System.currentTimeMillis()
        val sampleTransactions = listOf(
            TransactionEntity(
                type = 0,
                amount = 30.0,
                categoryName = "餐饮",
                categoryIcon = "☕",
                remark = "星巴克拿铁",
                recordTimestamp = now - 2 * 60 * 60 * 1000,
                createdTimestamp = now,
            ),
            TransactionEntity(
                type = 0,
                amount = 28.5,
                categoryName = "交通",
                categoryIcon = "🚗",
                remark = "滴滴出行",
                recordTimestamp = now - 5 * 60 * 60 * 1000,
                createdTimestamp = now,
            ),
            TransactionEntity(
                type = 1,
                amount = 200.0,
                categoryName = "收入",
                categoryIcon = "💰",
                remark = "兼职收入",
                recordTimestamp = now - 26 * 60 * 60 * 1000,
                createdTimestamp = now,
            ),
            TransactionEntity(
                type = 0,
                amount = 45.0,
                categoryName = "餐饮",
                categoryIcon = "🍰",
                remark = "好利来蛋糕",
                recordTimestamp = now - 30 * 60 * 60 * 1000,
                createdTimestamp = now,
            ),
        )

        transactionDao.insertTransactions(sampleTransactions)
    }
}
