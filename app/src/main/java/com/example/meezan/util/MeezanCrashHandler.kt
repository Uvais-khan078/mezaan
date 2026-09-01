package com.example.meezan.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.example.meezan.MainActivity
import kotlin.system.exitProcess

class MeezanCrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Log the error
            android.util.Log.e("MeezanCrash", "FATAL CRASH in thread ${thread.name}", throwable)

            // Detect native linkage errors (16KB alignment issues etc)
            val isLinkageError = throwable is LinkageError || throwable.cause is LinkageError
            
            // Prevent infinite crash loops: check if we just restarted
            val prefs = context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
            val lastCrashTime = prefs.getLong("last_crash_timestamp", 0L)
            val crashCount = prefs.getInt("crash_count", 0)
            val currentTime = System.currentTimeMillis()

            val newCount = if (currentTime - lastCrashTime < 30000L) crashCount + 1 else 1
            
            prefs.edit()
                .putLong("last_crash_timestamp", currentTime)
                .putInt("crash_count", newCount)
                .apply()

            if (newCount > 2 || isLinkageError) {
                // If crashed more than twice within 30 seconds OR it's a native linkage error, don't restart.
                android.util.Log.e("MeezanCrash", "CRASH LOOP OR LINKAGE ERROR DETECTED. Stopping auto-restart.")
                defaultHandler?.uncaughtException(thread, throwable)
                return
            }

            // Start MainActivity with a special flag
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("FATAL_ERROR", true)
            }
            context.startActivity(intent)

            // Terminate current process
            Process.killProcess(Process.myPid())
            System.exit(10)
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun initialize(context: Context) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is MeezanCrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(MeezanCrashHandler(context, currentHandler))
            }
        }
    }
}
