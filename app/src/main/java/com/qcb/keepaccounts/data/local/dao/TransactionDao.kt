package com.qcb.keepaccounts.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countTransactions(): Int

    @Query("SELECT * FROM transactions ORDER BY recordTimestamp DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY recordTimestamp DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeTransactionById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query(
        """
        UPDATE transactions
        SET type = :type,
            amount = :amount,
            categoryName = :categoryName,
            categoryIcon = :categoryIcon,
            remark = :remark,
            recordTimestamp = :recordTimestamp
        WHERE id = :id
        """,
    )
    suspend fun updateTransactionById(
        id: Long,
        type: Int,
        amount: Double,
        categoryName: String,
        categoryIcon: String,
        remark: String,
        recordTimestamp: Long,
    )

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)
}
