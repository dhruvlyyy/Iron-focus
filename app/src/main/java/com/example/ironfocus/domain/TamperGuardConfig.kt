package com.example.ironfocus.domain

import java.util.Locale

object TamperGuardConfig {

    /**
     * Exact packages monitored for Settings / Installer / OEM Security Guard protection.
     */
    private val GUARDED_EXACT_PACKAGES: Set<String> = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter",
        "com.miui.permcenter",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.heytap.safecenter",
        "com.iqoo.secure",
        "com.vivo.permissionmanager",
        "com.huawei.systemmanager",
        "com.oneplus.security",
        "com.samsung.android.lool",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    /**
     * Package name substrings used by OEM security, app-manager, and permission controllers.
     */
    private val GUARDED_PACKAGE_SUBSTRINGS: List<String> = listOf(
        "packageinstaller",
        "securitycenter",
        "safecenter",
        "permissioncontroller",
        "permissionmanager",
        "systemmanager",
        "devicecare",
        "phonemanager",
        ".settings"
    )

    /**
     * Case-insensitive keywords that indicate an attempt to disable IronFocus,
     * revoke Accessibility / Device Admin, force stop, uninstall, or cheat the system clock.
     */
    val FORBIDDEN_KEYWORDS: List<String> = listOf(
        "ironfocus",
        "device admin",
        "device management",
        "accessibility",
        "force stop",
        "uninstall",
        "date & time",
        "set time",
        "automatic date"
    )

    fun isGuardedSettingsOrSecurityPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.lowercase(Locale.ROOT)
        if (pkg in GUARDED_EXACT_PACKAGES) return true
        return GUARDED_PACKAGE_SUBSTRINGS.any { pkg.contains(it) }
    }

    fun findForbiddenKeyword(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.toString().lowercase(Locale.ROOT)
        return FORBIDDEN_KEYWORDS.firstOrNull { keyword -> lower.contains(keyword) }
    }
}

object BrowserRegistry {

    /**
     * Known major Android browsers mapped to their exact URL bar view resource IDs.
     */
    val KNOWN_BROWSER_URL_BAR_IDS: Map<String, String> = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
        "com.vivaldi.browser" to "com.vivaldi.browser:id/url_bar"
    )

    /**
     * Resource ID fragments checked by the Universal Browser Fallback scanner.
     */
    val FALLBACK_URL_ID_TOKENS: List<String> = listOf(
        "url",
        "address",
        "omnibar",
        "location_bar",
        "search_box"
    )

    fun isFallbackUrlNodeId(viewIdResourceName: String?): Boolean {
        if (viewIdResourceName.isNullOrBlank()) return false
        val lower = viewIdResourceName.lowercase(Locale.ROOT)
        return FALLBACK_URL_ID_TOKENS.any { lower.contains(it) }
    }
}
