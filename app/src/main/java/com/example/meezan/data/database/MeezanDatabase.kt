package com.example.meezan.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.meezan.data.dao.*
import com.example.meezan.data.entities.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Type converters for Room to handle enums and other custom types
 */
class TaskStatusConverter {
    @androidx.room.TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @androidx.room.TypeConverter
    fun toTaskStatus(status: String): TaskStatus = TaskStatus.valueOf(status)
}

class FinanceConverters {
    @androidx.room.TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @androidx.room.TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @androidx.room.TypeConverter
    fun fromLendingType(type: LendingType): String = type.name

    @androidx.room.TypeConverter
    fun toLendingType(value: String): LendingType = LendingType.valueOf(value)
}

@Database(
    entities = [
        PrayerTiming::class,
        PrayerReminderRule::class,
        Goal::class,
        GoalTask::class,
        FocusSession::class,
        Habit::class,
        HabitLog::class,
        DailyScore::class,
        PrayerLog::class,
        FinanceTransaction::class,
        LendingRecord::class,
        FinanceProfile::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(TaskStatusConverter::class, FinanceConverters::class)
abstract class MeezanDatabase : RoomDatabase() {
    abstract fun prayerTimingDao(): PrayerTimingDao
    abstract fun prayerLogDao(): PrayerLogDao
    abstract fun prayerReminderRuleDao(): PrayerReminderRuleDao
    abstract fun goalDao(): GoalDao
    abstract fun goalTaskDao(): GoalTaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun dailyScoreDao(): DailyScoreDao
    abstract fun transactionDao(): FinanceTransactionDao
    abstract fun lendingDao(): LendingDao
    abstract fun financeProfileDao(): FinanceProfileDao

    companion object {
        @Volatile
        private var Instance: MeezanDatabase? = null

        fun getDatabase(context: Context, passphrase: ByteArray? = null): MeezanDatabase {
            return Instance ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    MeezanDatabase::class.java,
                    "meezan_database"
                )
                
                if (passphrase != null) {
                    val factory = SupportOpenHelperFactory(passphrase)
                    builder.openHelperFactory(factory)
                }
                
                builder
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

