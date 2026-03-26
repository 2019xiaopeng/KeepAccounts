package com.qcb.keepaccounts.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Int,
    val amount: Double,
    val categoryName: String,
    val categoryIcon: String,
    val remark: String,
    val recordTimestamp: Long,
    val createdTimestamp: Long,
)
