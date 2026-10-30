package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.MainActivity

/**
 * Automatically launches PulseCast Digital Signage Player whenever the Android TV / Display
 * powers on, reboots, or updates (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, QUICKBOOT_POWERON).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("PulseCastBoot", "Boot signal received ($action). Launching MainActivity for 24x7 signage...")
            val appContext = context.applicationContext
            launchSignageActivity(appContext)

            // Also re-assert foreground after 2.5s in case the Android TV stock launcher initializes slightly after BOOT_COMPLETED
            val pendingResult = goAsync()
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    launchSignageActivity(appContext)
                } catch (_: Throwable) {
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (_: Throwable) {
                    }
                }
            }, 2500L)
        }
    }

    private fun launchSignageActivity(context: Context) {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e("PulseCastBoot", "Failed to auto-launch MainActivity on boot: ${e.message}", e)
        }
    }
}
