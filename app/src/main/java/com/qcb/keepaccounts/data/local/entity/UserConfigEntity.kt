package com.qcb.keepaccounts.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_config")
data class UserConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val aiName: String,
    val aiPersona: String,
    val themeColor: String,
    val monthlyBudget: Double,
)
