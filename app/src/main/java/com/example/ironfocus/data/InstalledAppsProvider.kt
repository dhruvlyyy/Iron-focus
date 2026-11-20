package com.example.ironfocus.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object InstalledAppsProvider {

    private val ALWAYS_PROTECTED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.android.contacts",
        "com.android.emergency",
        "android"
    )

    /**
     * Computes all packages that IronFocus must NEVER allow blocking:
     * IronFocus itself, Phone/Dialer apps, System UI, and the default Home launcher.
     */
    fun getProtectedPackages(context: Context): Set<String> {
        val protectedSet = ALWAYS_PROTECTED_PACKAGES.toMutableSet()
        protectedSet.add(context.packageName)

        // Default dialer via TelecomManager
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage?.let { protectedSet.add(it) }
        } catch (_: Exception) {
        }

        val pm = context.packageManager

        // All apps capable of handling ACTION_DIAL (Phone/Dialer apps)
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
        queryActivityPackages(pm, dialIntent).forEach { protectedSet.add(it) }

        // Default Home Launcher so GLOBAL_ACTION_HOME always lands safely
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        defaultHome?.activityInfo?.packageName?.let { protectedSet.add(it) }

        return protectedSet
    }

    /**
     * Queries all installed launchable apps excluding protected packages.
     */
    suspend fun loadBlockableApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val protectedPackages = getProtectedPackages(context)

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }

        val uniqueByPackage = LinkedHashMap<String, InstalledApp>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg in protectedPackages) continue
            if (uniqueByPackage.containsKey(pkg)) continue

            val label = try {
                info.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
            } catch (_: Exception) {
                pkg
            }

            val icon = try {
                info.loadIcon(pm)
            } catch (_: Exception) {
                null
            }

            uniqueByPackage[pkg] = InstalledApp(
                packageName = pkg,
                label = label,
                icon = icon
            )
        }

        uniqueByPackage.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Discovers all installed apps that handle ACTION_VIEW for https:// URLs
     * to power the Universal Browser Fallback in BlockerAccessibilityService.
     */
    fun getInstalledBrowserPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val packages = queryActivityPackages(pm, browserIntent).toMutableSet()
        // Exclude IronFocus itself and system UI
        packages.remove(context.packageName)
        packages.remove("com.android.systemui")
        return packages
    }

    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun queryActivityPackages(pm: PackageManager, intent: Intent): Set<String> {
        val results = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
        } catch (_: Exception) {
            emptyList()
        }
        return results.mapNotNull { it.activityInfo?.packageName }.toSet()
    }
}
