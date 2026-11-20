package com.example.ironfocus.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.example.R
import com.example.ironfocus.receiver.IronFocusDeviceAdminReceiver
import com.example.ironfocus.service.BlockerAccessibilityService
import com.example.ironfocus.service.ScheduleAlarmManager

data class PermissionsStatus(
    val accessibilityEnabled: Boolean = false,
    val deviceAdminEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val exactAlarmsAllowed: Boolean = true
) {
    /**
     * Strict Lock requires both Accessibility Service AND Device Administrator to be active.
     */
    val canStartStrictLock: Boolean
        get() = accessibilityEnabled && deviceAdminEnabled

    val enabledCount: Int
        get() = listOf(
            accessibilityEnabled,
            deviceAdminEnabled,
            batteryOptimizationIgnored,
            overlayPermissionGranted
        ).count { it }
}

object PermissionsHelper {

    fun checkAll(context: Context): PermissionsStatus {
        return PermissionsStatus(
            accessibilityEnabled = isAccessibilityServiceEnabled(context),
            deviceAdminEnabled = isDeviceAdminActive(context),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
            overlayPermissionGranted = Settings.canDrawOverlays(context),
            exactAlarmsAllowed = ScheduleAlarmManager.canScheduleExactAlarms(context)
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, BlockerAccessibilityService::class.java)
        val expectedFlat = expectedComponent.flattenToString()
        val expectedShort = expectedComponent.flattenToShortString()

        // 1. Check AccessibilityManager active list
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (service in enabledServices) {
                    val info = service.resolveInfo?.serviceInfo ?: continue
                    if (info.packageName == context.packageName &&
                        info.name == BlockerAccessibilityService::class.java.name
                    ) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Cross-check Settings.Secure
        return try {
            val enabledSetting = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (enabledSetting != 1) return false

            val enabledServicesStr = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServicesStr)
            while (splitter.hasNext()) {
                val componentStr = splitter.next()
                if (componentStr.equals(expectedFlat, ignoreCase = true) ||
                    componentStr.equals(expectedShort, ignoreCase = true)
                ) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val component = ComponentName(context, IronFocusDeviceAdminReceiver::class.java)
            dpm?.isAdminActive(component) == true
        } catch (_: Exception) {
            false
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (_: Exception) {
            false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        safeStartActivity(context, intent)
    }

    fun openDeviceAdminSettings(context: Context) {
        val component = ComponentName(context, IronFocusDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_description)
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        safeStartActivity(context, intent)
    }

    @SuppressLint("BatteryLife")
    fun openIgnoreBatteryOptimizations(context: Context) {
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!safeStartActivity(context, directIntent)) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartActivity(context, fallback)
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!safeStartActivity(context, intent)) {
            val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartActivity(context, fallback)
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartActivity(context, intent)
        }
    }

    private fun safeStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
