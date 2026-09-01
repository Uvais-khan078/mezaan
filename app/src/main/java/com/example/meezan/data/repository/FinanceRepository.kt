package com.example.meezan.data.repository

import com.example.meezan.data.dao.*
import com.example.meezan.data.database.MeezanDatabase
import com.example.meezan.data.entities.*
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import com.example.meezan.util.DateTimeUtil
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar

class FinanceRepository(
    private val database: MeezanDatabase,
    private val transactionDao: FinanceTransactionDao,
    private val lendingDao: LendingDao,
    private val profileDao: FinanceProfileDao
) : BaseRepository() {

    fun observeAllTransactions(): Flow<List<FinanceTransaction>> = transactionDao.observeAllTransactions()
    
    fun observeTransactionsInRange(startDate: Long, endDate: Long): Flow<List<FinanceTransaction>> = 
        transactionDao.observeTransactionsInRange(startDate, endDate)
    
    fun observeActiveLending(): Flow<List<LendingRecord>> = lendingDao.observeActiveLending()
    
    fun observeProfile(): Flow<FinanceProfile?> = profileDao.observeProfile()

    suspend fun addBudget(amount: Double, source: String): Result<Unit> = safeDbCall {
        database.withTransaction {
            val profile = profileDao.getProfile() ?: FinanceProfile()
            val newBudget = profile.totalMonthlyBudget + amount
            
            transactionDao.insert(
                FinanceTransaction(
                    amount = amount,
                    sourceOrReason = source,
                    date = System.currentTimeMillis(),
                    type = TransactionType.INCOME
                )
            )
            
            val updatedProfile = profile.copy(
                totalMonthlyBudget = newBudget,
                lastUpdateDate = System.currentTimeMillis()
            )
            profileDao.updateProfile(updatedProfile)
            recalculateDailyLimit(updatedProfile)
        }
    }

    suspend fun withdraw(
        amount: Double, 
        reason: String, 
        isSplit: Boolean = false, 
        isDebt: Boolean = false,
        people: List<String> = emptyList()
    ): Result<Unit> = safeDbCall {
        database.withTransaction {
            val profile = profileDao.getProfile() ?: FinanceProfile()
            var remainingBudget = profile.totalMonthlyBudget
            var remainingSavings = profile.totalSavings
            
            if (remainingBudget + remainingSavings < amount) {
                throw AppErrorException(AppError.InsufficientFunds(remainingBudget + remainingSavings))
            }

            var isFromSavings = false
            if (remainingBudget < amount) {
                isFromSavings = true
                val neededFromSavings = amount - remainingBudget
                remainingBudget = 0.0
                remainingSavings -= neededFromSavings
            } else {
                remainingBudget -= amount
            }

            val type = when {
                isDebt || isSplit -> TransactionType.LENDING
                isFromSavings -> TransactionType.SAVINGS_WITHDRAWAL
                else -> TransactionType.WITHDRAWAL
            }

            val transactionId = transactionDao.insert(
                FinanceTransaction(
                    amount = amount,
                    sourceOrReason = reason,
                    date = System.currentTimeMillis(),
                    type = type,
                    isFromSavings = isFromSavings
                )
            )

            // Handle Splitting
            if (isSplit && people.isNotEmpty()) {
                val perPerson = amount / (people.size + 1)
                people.forEach { name ->
                    lendingDao.insert(
                        LendingRecord(
                            personName = name,
                            amount = perPerson,
                            type = LendingType.SPLIT,
                            transactionId = transactionId,
                            date = System.currentTimeMillis(),
                            reason = reason
                        )
                    )
                }
            }

            // Handle Debt
            if (isDebt && people.isNotEmpty()) {
                people.forEach { name ->
                    lendingDao.insert(
                        LendingRecord(
                            personName = name,
                            amount = amount,
                            type = LendingType.DEBT,
                            transactionId = transactionId,
                            date = System.currentTimeMillis(),
                            reason = reason
                        )
                    )
                }
            }

            val updatedProfile = profile.copy(
                totalMonthlyBudget = remainingBudget,
                totalSavings = remainingSavings,
                lastUpdateDate = System.currentTimeMillis()
            )
            profileDao.updateProfile(updatedProfile)
            recalculateDailyLimit(updatedProfile)
        }
    }

    suspend fun clearLending(record: LendingRecord): Result<Unit> = safeDbCall {
        database.withTransaction {
            val profile = profileDao.getProfile() ?: FinanceProfile()
            
            lendingDao.update(record.copy(isCleared = true))
            
            transactionDao.insert(
                FinanceTransaction(
                    amount = record.amount,
                    sourceOrReason = "Cleared ${record.type} from ${record.personName}",
                    date = System.currentTimeMillis(),
                    type = TransactionType.INCOME
                )
            )

            val updatedProfile = profile.copy(
                totalMonthlyBudget = profile.totalMonthlyBudget + record.amount
            )
            profileDao.updateProfile(updatedProfile)
            recalculateDailyLimit(updatedProfile)
        }
    }

    suspend fun addToSavings(amount: Double): Result<Unit> = safeDbCall {
        database.withTransaction {
            val profile = profileDao.getProfile() ?: FinanceProfile()
            if (profile.totalMonthlyBudget < amount) throw AppErrorException(AppError.ValidationError("Insufficient budget to save this amount."))

            transactionDao.insert(
                FinanceTransaction(
                    amount = amount,
                    sourceOrReason = "Transfer to Savings",
                    date = System.currentTimeMillis(),
                    type = TransactionType.SAVINGS_DEPOSIT
                )
            )

            val updatedProfile = profile.copy(
                totalMonthlyBudget = profile.totalMonthlyBudget - amount,
                totalSavings = profile.totalSavings + amount
            )
            profileDao.updateProfile(updatedProfile)
            recalculateDailyLimit(updatedProfile)
        }
    }

    suspend fun transferFromSavings(amount: Double): Result<Unit> = safeDbCall {
        database.withTransaction {
            val profile = profileDao.getProfile() ?: FinanceProfile()
            if (profile.totalSavings < amount) throw AppErrorException(AppError.ValidationError("Insufficient savings to withdraw this amount."))

            transactionDao.insert(
                FinanceTransaction(
                    amount = amount,
                    sourceOrReason = "Transfer from Savings",
                    date = System.currentTimeMillis(),
                    type = TransactionType.INCOME,
                    isFromSavings = true
                )
            )

            val updatedProfile = profile.copy(
                totalMonthlyBudget = profile.totalMonthlyBudget + amount,
                totalSavings = profile.totalSavings - amount
            )
            profileDao.updateProfile(updatedProfile)
            recalculateDailyLimit(updatedProfile)
        }
    }

    suspend fun recalculateDailyLimit(profile: FinanceProfile): Result<Unit> = safeDbCall {
        val cal = Calendar.getInstance()
        val todayMidnight = DateTimeUtil.normalizeToMidnight(cal.timeInMillis)
        
        // Check if month changed since last update
        val lastUpdateCal = Calendar.getInstance().apply { timeInMillis = profile.lastUpdateDate }
        val isNewMonth = (lastUpdateCal.get(Calendar.MONTH) != cal.get(Calendar.MONTH)) || 
                         (lastUpdateCal.get(Calendar.YEAR) != cal.get(Calendar.YEAR))

        var currentProfile = profile
        
        if (isNewMonth && profile.lastUpdateDate > 0) {
            // Month changed! Move remaining budget to savings
            val remainder = profile.totalMonthlyBudget
            if (remainder > 0) {
                transactionDao.insert(
                    FinanceTransaction(
                        amount = remainder,
                        sourceOrReason = "Month-end Rollover to Savings",
                        date = System.currentTimeMillis(),
                        type = TransactionType.SAVINGS_DEPOSIT
                    )
                )
                currentProfile = currentProfile.copy(
                    totalSavings = currentProfile.totalSavings + remainder,
                    totalMonthlyBudget = 0.0
                )
            }
        }

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = (daysInMonth - currentDay + 1).coerceAtLeast(1)

        val dailyLimit = currentProfile.totalMonthlyBudget / daysRemaining
        profileDao.updateProfile(currentProfile.copy(
            dailyLimit = dailyLimit,
            lastUpdateDate = todayMidnight
        ))
    }

    suspend fun getTodaysSavings(): Double {
        val profile = profileDao.getProfile() ?: return 0.0
        val today = DateTimeUtil.normalizeToMidnight(System.currentTimeMillis())
        val tomorrow = today + 24 * 60 * 60 * 1000L
        
        val spentToday = transactionDao.getTotalWithdrawalsInRange(today, tomorrow) ?: 0.0
        return (profile.dailyLimit - spentToday).coerceAtLeast(0.0)
    }

}
