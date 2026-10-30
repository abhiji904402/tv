package com.example.system

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class KioskPermissionsStatus(
    val isDefaultHomeLauncher: Boolean = false,
    val isDeviceAdminEnabled: Boolean = false,
    val isAccessibilityGuardEnabled: Boolean = false,
    val isSystemOverlayGranted: Boolean = false,
    val isUnrestrictedBatteryAllowed: Boolean = false
) {
    val grantedCount: Int
        get() = listOf(
            isDefaultHomeLauncher,
            isDeviceAdminEnabled,
            isAccessibilityGuardEnabled,
            isSystemOverlayGranted,
            isUnrestrictedBatteryAllowed
        ).count { it }
}

object KioskPermissionHelper {

    fun checkAllPermissions(context: Context): KioskPermissionsStatus {
        return KioskPermissionsStatus(
            isDefaultHomeLauncher = isDefaultHomeLauncher(context),
            isDeviceAdminEnabled = isDeviceAdminActive(context),
            isAccessibilityGuardEnabled = isAccessibilityServiceEnabled(context),
            isSystemOverlayGranted = isOverlayGranted(context),
            isUnrestrictedBatteryAllowed = isBatteryOptimizationIgnored(context)
        )
    }

    fun isDefaultHomeLauncher(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return true
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, SignageDeviceAdminReceiver::class.java)
            dpm?.isAdminActive(adminComponent) == true
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val expectedComponent = ComponentName(context, SignageGuardAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabledServices.split(':').any {
                ComponentName.unflattenFromString(it) == expectedComponent
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isOverlayGranted(context: Context): Boolean {
        return try {
            Settings.canDrawOverlays(context)
        } catch (_: Exception) {
            false
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 1. Opens Default HOME Launcher chooser popup (or Home Settings fallback).
     */
    fun requestDefaultHomeLauncherPopup(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun openHomeLauncherSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val chooser = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(chooser, "Select PulseCast as Default Home Launcher"))
            } catch (_: Exception) {
                openGeneralSettingsFallback(context)
            }
        }
    }

    /**
     * 2. Opens Device Admin activation popup directly (DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).
     */
    fun buildDeviceAdminPopupIntent(context: Context): Intent {
        val adminComponent = ComponentName(context, SignageDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable PulseCast Device Admin for 24x7 Kiosk Lock & unattended TV startup protection."
            )
        }
    }

    fun launchDeviceAdminPopup(context: Context) {
        try {
            val intent = buildDeviceAdminPopupIntent(context).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openGeneralSettingsFallback(context)
        }
    }

    /**
     * 3. Opens Accessibility Settings so user can enable PulseCast 24x7 Guard.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openGeneralSettingsFallback(context)
        }
    }

    /**
     * 4. Opens System Overlay ("Display Over Other Apps") permission popup/screen directly.
     */
    fun openOverlayPermissionPopup(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                openGeneralSettingsFallback(context)
            }
        }
    }

    /**
     * 5. Opens the direct "Allow 24x7 Unrestricted Battery" system dialog popup
     * (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) with fallback to battery optimization settings.
     */
    fun openUnrestrictedBatteryPopup(context: Context) {
        try {
            val directPopup = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(directPopup)
        } catch (_: Exception) {
            try {
                val listScreen = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(listScreen)
            } catch (_: Exception) {
                openGeneralSettingsFallback(context)
            }
        }
    }

    private fun openGeneralSettingsFallback(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
