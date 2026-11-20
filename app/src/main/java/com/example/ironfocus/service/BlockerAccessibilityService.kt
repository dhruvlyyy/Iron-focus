package com.example.ironfocus.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ironfocus.data.InstalledAppsProvider
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.domain.BrowserRegistry
import com.example.ironfocus.domain.TamperGuardConfig
import com.example.ironfocus.domain.UrlMatcher
import com.example.ironfocus.ui.BlockedOverlayActivity

/**
 * Multi-Window, Multi-Browser Blocking Engine + Settings Anti-Tamper Guard.
 *
 * Inspects all active windows via getWindows() (falling back to rootInActiveWindow)
 * to prevent Split-Screen or Floating Window bypasses.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var repository: LockRepository
    private var protectedPackages: Set<String> = emptySet()
    private var installedBrowsers: Set<String> = emptySet()
    private var lastBrowserCacheRefreshElapsed: Long = 0L
    private var lastTriggerElapsed: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = LockRepository.getInstance(applicationContext)
        refreshPackageCaches(force = true)

        // Ensure foreground timer service is alive if a lock is active
        if (repository.isLockCurrentlyActive()) {
            LockCountdownService.start(applicationContext)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::repository.isInitialized) {
            repository = LockRepository.getInstance(applicationContext)
        }

        val state = repository.refreshLiveState()
        if (!state.isLockActive || state.remainingMillis <= 0L) {
            return
        }

        refreshPackageCaches(force = false)

        // Collect all active window roots (multi-window / split-screen / floating window support)
        val windowRoots = collectActiveWindowRoots()
        if (windowRoots.isEmpty()) {
            // Fallback check using event.packageName if window roots are momentarily unavailable
            val eventPkg = event?.packageName?.toString()
            if (!eventPkg.isNullOrBlank() &&
                eventPkg !in protectedPackages &&
                eventPkg in state.blockedPackages
            ) {
                triggerAppBlock(eventPkg)
            }
            return
        }

        try {
            // Pass 1: App Blocking across ALL active windows
            for (root in windowRoots) {
                val pkg = root.packageName?.toString() ?: continue
                if (pkg in protectedPackages) continue
                if (pkg in state.blockedPackages) {
                    triggerAppBlock(pkg)
                    return
                }
            }

            // Pass 2: Settings & Anti-Tamper Guard across ALL active windows
            for (root in windowRoots) {
                val pkg = root.packageName?.toString() ?: continue
                if (pkg == applicationContext.packageName) continue
                if (TamperGuardConfig.isGuardedSettingsOrSecurityPackage(pkg)) {
                    val matchedKeyword = scanWindowForTamperKeywords(root)
                    if (matchedKeyword != null) {
                        triggerTamperBlock(matchedKeyword)
                        return
                    }
                }
            }

            // Pass 3: Multi-Browser Website Blocking across ALL active windows
            if (state.blockedDomains.isNotEmpty()) {
                for (root in windowRoots) {
                    val pkg = root.packageName?.toString() ?: continue
                    if (pkg in protectedPackages) continue

                    val isKnownBrowser = BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS.containsKey(pkg)
                    val isFallbackBrowser = pkg in installedBrowsers

                    if (isKnownBrowser || isFallbackBrowser) {
                        val matchedDomain = inspectBrowserWindowForBlockedUrl(
                            root = root,
                            packageName = pkg,
                            blockedDomains = state.blockedDomains
                        )
                        if (matchedDomain != null) {
                            triggerWebsiteBlock(matchedDomain)
                            return
                        }
                    }
                }
            }
        } finally {
            // No manual recycle needed on modern Android; safe guard prevents leaks on older APIs
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    private fun collectActiveWindowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            val activeWindows = windows
            if (!activeWindows.isNullOrEmpty()) {
                for (window in activeWindows) {
                    val root = window.root ?: continue
                    roots.add(root)
                }
            }
        } catch (_: Exception) {
        }

        if (roots.isEmpty()) {
            try {
                rootInActiveWindow?.let { roots.add(it) }
            } catch (_: Exception) {
            }
        }
        return roots
    }

    private fun inspectBrowserWindowForBlockedUrl(
        root: AccessibilityNodeInfo,
        packageName: String,
        blockedDomains: Set<String>
    ): String? {
        // 1. Direct known browser URL bar lookup by exact resource ID
        val knownUrlBarId = BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS[packageName]
        if (knownUrlBarId != null) {
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId(knownUrlBarId)
            } catch (_: Exception) {
                emptyList()
            }
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val match = UrlMatcher.findMatchingBlockedDomain(node.text, blockedDomains)
                        ?: UrlMatcher.findMatchingBlockedDomain(node.contentDescription, blockedDomains)
                    if (match != null) return match
                }
                // Found known URL bar with text, no blocked domain -> still check fallback only if empty
                val hasText = nodes.any { !it.text.isNullOrBlank() }
                if (hasText) return null
            }
        }

        // 2. Universal Browser Fallback: traverse top-level hierarchy for EditText or URL-like resource IDs
        return scanUniversalBrowserUrlNodes(root, blockedDomains)
    }

    private fun scanUniversalBrowserUrlNodes(
        root: AccessibilityNodeInfo,
        blockedDomains: Set<String>
    ): String? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_URL_SCAN_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            val isEditText = node.className?.toString()?.contains("EditText", ignoreCase = true) == true
            val isUrlId = BrowserRegistry.isFallbackUrlNodeId(node.viewIdResourceName)

            if (isEditText || isUrlId) {
                val match = UrlMatcher.findMatchingBlockedDomain(node.text, blockedDomains)
                    ?: UrlMatcher.findMatchingBlockedDomain(node.contentDescription, blockedDomains)
                if (match != null) {
                    return match
                }
            }

            if (depth < MAX_URL_SCAN_DEPTH) {
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child to depth + 1)
                }
            }
        }
        return null
    }

    private fun scanWindowForTamperKeywords(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_SETTINGS_SCAN_NODES) {
            val node = queue.removeFirst()
            visited++

            val textMatch = TamperGuardConfig.findForbiddenKeyword(node.text)
            if (textMatch != null) return textMatch

            val descMatch = TamperGuardConfig.findForbiddenKeyword(node.contentDescription)
            if (descMatch != null) return descMatch

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }
        return null
    }

    private fun triggerAppBlock(packageName: String) {
        if (!canTriggerNow()) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        val label = InstalledAppsProvider.getAppLabel(applicationContext, packageName)
        launchBlockedOverlay(
            reasonType = BlockedOverlayActivity.REASON_APP,
            targetDisplay = label
        )
    }

    private fun triggerWebsiteBlock(matchedDomain: String) {
        if (!canTriggerNow()) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        launchBlockedOverlay(
            reasonType = BlockedOverlayActivity.REASON_WEBSITE,
            targetDisplay = matchedDomain
        )
    }

    private fun triggerTamperBlock(keyword: String) {
        if (!canTriggerNow()) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        launchBlockedOverlay(
            reasonType = BlockedOverlayActivity.REASON_TAMPER,
            targetDisplay = keyword
        )
    }

    private fun canTriggerNow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerElapsed < TRIGGER_COOLDOWN_MS) {
            return false
        }
        lastTriggerElapsed = now
        return true
    }

    private fun launchBlockedOverlay(reasonType: String, targetDisplay: String) {
        val intent = Intent(applicationContext, BlockedOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockedOverlayActivity.EXTRA_REASON_TYPE, reasonType)
            putExtra(BlockedOverlayActivity.EXTRA_TARGET_NAME, targetDisplay)
        }
        try {
            applicationContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun refreshPackageCaches(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && (now - lastBrowserCacheRefreshElapsed) < PACKAGE_CACHE_TTL_MS) {
            return
        }
        lastBrowserCacheRefreshElapsed = now
        protectedPackages = InstalledAppsProvider.getProtectedPackages(applicationContext)
        installedBrowsers = InstalledAppsProvider.getInstalledBrowserPackages(applicationContext)
    }

    companion object {
        private const val TRIGGER_COOLDOWN_MS = 650L
        private const val PACKAGE_CACHE_TTL_MS = 60_000L
        private const val MAX_URL_SCAN_NODES = 180
        private const val MAX_URL_SCAN_DEPTH = 14
        private const val MAX_SETTINGS_SCAN_NODES = 350
    }
}
