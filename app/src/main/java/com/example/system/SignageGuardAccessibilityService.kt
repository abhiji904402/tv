package com.example.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.data.local.ChunkedStorageAndOtaManager

/**
 * 24x7 Guard Accessibility Service.
 * Ensures the digital signage player opens automatically on TV boot/power-on (Accessibility Services
 * are exempt from Android 10+ background activity launch restrictions) and stays in foreground
 * 24x7 when Kiosk Mode Lock is enabled.
 */
class SignageGuardAccessibilityService : AccessibilityService() {

    private var lastRelaunchEpochMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val localManager: ChunkedStorageAndOtaManager by lazy {
        ChunkedStorageAndOtaManager(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            localManager.setTemporaryExitBypass(false)
            // Always launch MainActivity when the Accessibility Service starts on TV boot / power-on
            bringSignageToFrontIfNeeded()
            mainHandler.postDelayed({
                try {
                    if (!localManager.isTemporaryExitBypassActive()) {
                        bringSignageToFrontIfNeeded()
                    }
                } catch (_: Throwable) {
                }
            }, 2500L)
        } catch (_: Throwable) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = event.packageName?.toString() ?: return

            // Ignore our own package and system permission/settings dialogs so the user can grant permissions freely
            if (pkg == packageName ||
                pkg.contains("settings", ignoreCase = true) ||
                pkg.contains("permissioncontroller", ignoreCase = true) ||
                pkg.contains("packageinstaller", ignoreCase = true) ||
                pkg.contains("systemui", ignoreCase = true) ||
                pkg.contains("documentsui", ignoreCase = true) ||
                pkg.contains("photopicker", ignoreCase = true)
            ) {
                return
            }

            if (localManager.getSavedDisplaySettings().kioskLocked && !localManager.isTemporaryExitBypassActive()) {
                val now = System.currentTimeMillis()
                if (now - lastRelaunchEpochMs > 3000L) {
                    lastRelaunchEpochMs = now
                    bringSignageToFrontIfNeeded()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun bringSignageToFrontIfNeeded() {
        try {
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(launchIntent)
        } catch (_: Throwable) {
        }
    }

    override fun onInterrupt() {
        // No-op
    }
}
