package com.qcb.keepaccounts.data

import android.content.Context
import androidx.room.Room
import com.qcb.keepaccounts.data.local.AppDatabase
import com.qcb.keepaccounts.data.repository.TransactionRepository

interface AppContainer {
    val transactionRepository: TransactionRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "keep_accounts.db",
    ).build()

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }
}
