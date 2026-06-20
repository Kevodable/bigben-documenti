package it.bigbenmatic.gamelauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * Wraps Android's Lock Task ("kiosk") APIs. Full protection (blocking Home, Recents,
 * status bar and any app outside the allow-list) only works once this app has been
 * provisioned as Device Owner on the tablet — see README.md for the one-time adb command.
 * Without Device Owner, lock task is not engaged automatically so that launching the
 * actual games (which run as separate apps/tasks) keeps working.
 */
object KioskManager {

    fun adminComponent(context: Context) = ComponentName(context, DeviceOwnerReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /** Keeps the lock-task allow-list in sync with the games chosen in Settings. */
    fun syncAllowedPackages(context: Context, selectedGamePackages: Set<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val allowed = (selectedGamePackages + context.packageName).toTypedArray()
        dpm.setLockTaskPackages(adminComponent(context), allowed)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Block Home/Recents/notifications; allow the power-button global actions menu.
            dpm.setLockTaskFeatures(
                adminComponent(context),
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
            )
        }
    }

    /** Engages kiosk pinning if this app is Device Owner. Safe to call repeatedly. */
    fun engage(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        if (am.lockTaskModeState == android.app.ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { activity.startLockTask() }
        }
    }

    /** Lets a parent temporarily leave kiosk mode (e.g. to reach Android Settings for maintenance). */
    fun release(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }
}
