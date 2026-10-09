package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: Int = 1,
    val monthlyLimit: Double = 15000.0,
    val spreadsheetId: String = "",
    val scriptUrl: String = "",
    val googleToken: String = "",
    val refreshToken: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val lastSyncTime: Long = 0L,
    val customCategories: String = ""
)
