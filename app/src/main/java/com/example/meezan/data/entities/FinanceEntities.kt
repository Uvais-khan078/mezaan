package com.example.meezan.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Transaction type enum
 */
enum class TransactionType {
    INCOME,
    WITHDRAWAL,
    SAVINGS_WITHDRAWAL,
    SAVINGS_DEPOSIT,
    LENDING
}

/**
 * Entity for all financial transactions
 */
@Entity(
    tableName = "finance_transactions",
    indices = [androidx.room.Index(value = ["date"])]
)
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val sourceOrReason: String,
    val date: Long, // Epoch milliseconds
    val type: TransactionType,
    val isFromSavings: Boolean = false
)

/**
 * Lending type enum
 */
enum class LendingType {
    SPLIT,
    DEBT
}

/**
 * Entity for tracking money owed to the user
 */
@Entity(
    tableName = "lending_records",
    indices = [
        androidx.room.Index(value = ["transactionId"]),
        androidx.room.Index(value = ["date"])
    ]
)
data class LendingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val amount: Double,
    val type: LendingType,
    val transactionId: Long, // Reference to the withdrawal that created this
    val isCleared: Boolean = false,
    val date: Long,
    val reason: String = ""
)

/**
 * Entity for tracking overall finance profile
 */
@Entity(tableName = "finance_profile")
data class FinanceProfile(
    @PrimaryKey
    val id: Int = 1,
    val totalMonthlyBudget: Double = 0.0,
    val dailyLimit: Double = 0.0,
    val totalSavings: Double = 0.0,
    val lastUpdateDate: Long = 0
)

