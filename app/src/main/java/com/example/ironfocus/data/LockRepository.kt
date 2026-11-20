package com.example.ironfocus.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.example.ironfocus.domain.UrlMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LockState(
    val isLockActive: Boolean = false,
    val remainingMillis: Long = 0L,
    val totalDurationMillis: Long = 0L,
    val blockedPackages: Set<String> = emptySet(),
    val blockedDomains: Set<String> = UrlMatcher.DEFAULT_QUICK_DOMAINS.toSet(),
    val customDomains: Set<String> = emptySet()
) {
    val progress: Float
        get() = if (!isLockActive || totalDurationMillis <= 0L) {
            0f
        } else {
            (remainingMillis.toFloat() / totalDurationMillis.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * Tamper-proof lock state repository backed by Device Protected Storage SharedPreferences
 * (direct-boot aware so BootReceiver can resume immediately on LOCKED_BOOT_COMPLETED).
 *
 * Timer calculation strictly uses monotonic SystemClock.elapsedRealtime() combined with
 * Settings.Global.BOOT_COUNT — NEVER System.currentTimeMillis() — so manually changing
 * the device date/time cannot shorten or bypass an active lock.
 */
class LockRepository private constructor(appContext: Context) {

    private val deviceProtectedContext: Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.createDeviceProtectedStorageContext()
        } else {
            appContext
        }

    private val prefs: SharedPreferences =
        deviceProtectedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readStateInternal())
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = readStateInternal()
    }

    init {
        ensureDefaultsSeeded()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        _state.value = readStateInternal()
    }

    private fun ensureDefaultsSeeded() {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putStringSet(KEY_BLOCKED_DOMAINS, UrlMatcher.DEFAULT_QUICK_DOMAINS.toSet())
                .putStringSet(KEY_CUSTOM_DOMAINS, emptySet())
                .putStringSet(KEY_BLOCKED_PACKAGES, emptySet())
                .apply()
        }
    }

    private fun currentBootCount(): Int {
        return try {
            Settings.Global.getInt(deviceProtectedContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Pure monotonic calculation helper, exposed internally for unit testing.
     */
    fun computeRemainingMillis(
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime(),
        bootCountNow: Int = currentBootCount()
    ): Long {
        val active = prefs.getBoolean(KEY_IS_LOCK_ACTIVE, false)
        if (!active) return 0L

        val savedRemaining = prefs.getLong(KEY_REMAINING_MILLIS, 0L)
        if (savedRemaining <= 0L) return 0L

        val savedElapsed = prefs.getLong(KEY_SNAPSHOT_ELAPSED_REALTIME, -1L)
        val savedBootCount = prefs.getInt(KEY_BOOT_COUNT, -1)

        val rebootDetected = (savedBootCount != -1 && bootCountNow != -1 && bootCountNow != savedBootCount) ||
            savedElapsed < 0L ||
            nowElapsedRealtime < savedElapsed

        if (rebootDetected) {
            // Reboot occurred before re-anchor: never subtract stale uptime, preserve saved remaining.
            return savedRemaining
        }

        val delta = (nowElapsedRealtime - savedElapsed).coerceAtLeast(0L)
        return (savedRemaining - delta).coerceAtLeast(0L)
    }

    fun isLockCurrentlyActive(): Boolean {
        return computeRemainingMillis() > 0L
    }

    /**
     * Re-computes live remaining time from monotonic clock and updates the reactive StateFlow.
     * Automatically concludes the lock if remainingMillis has hit zero.
     */
    fun refreshLiveState(): LockState {
        val current = readStateInternal()
        if (prefs.getBoolean(KEY_IS_LOCK_ACTIVE, false) && current.remainingMillis <= 0L) {
            finishExpiredLock()
            return readStateInternal()
        }
        _state.value = current
        return current
    }

    /**
     * Persists the latest remainingMillis checkpoint using monotonic elapsedRealtime.
     * Called every 30 seconds by LockCountdownService and before shutdown/destruction.
     */
    @Synchronized
    fun persistCheckpoint(): Long {
        if (!prefs.getBoolean(KEY_IS_LOCK_ACTIVE, false)) return 0L

        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        val remaining = computeRemainingMillis(nowElapsed, bootCount)

        if (remaining <= 0L) {
            finishExpiredLock()
            return 0L
        }

        prefs.edit()
            .putLong(KEY_REMAINING_MILLIS, remaining)
            .putLong(KEY_SNAPSHOT_ELAPSED_REALTIME, nowElapsed)
            .putInt(KEY_BOOT_COUNT, bootCount)
            .commit()

        _state.value = readStateInternal()
        return remaining
    }

    /**
     * Called by BootReceiver upon BOOT_COMPLETED / LOCKED_BOOT_COMPLETED.
     * Re-anchors elapsedRealtime to the new boot timeline while preserving the last persisted
     * remainingMillis snapshot so the countdown seamlessly resumes after reboot.
     */
    @Synchronized
    fun reanchorAfterBoot(): Long {
        if (!prefs.getBoolean(KEY_IS_LOCK_ACTIVE, false)) return 0L

        val savedRemaining = prefs.getLong(KEY_REMAINING_MILLIS, 0L)
        if (savedRemaining <= 0L) {
            finishExpiredLock()
            return 0L
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()

        prefs.edit()
            .putLong(KEY_REMAINING_MILLIS, savedRemaining)
            .putLong(KEY_SNAPSHOT_ELAPSED_REALTIME, nowElapsed)
            .putInt(KEY_BOOT_COUNT, bootCount)
            .commit()

        _state.value = readStateInternal()
        return savedRemaining
    }

    /**
     * Starts a Strict Lock session. Minimum duration is strictly 1 minute (60,000ms).
     */
    @Synchronized
    fun startLock(durationMillis: Long): Boolean {
        if (isLockCurrentlyActive()) return false
        val safeDuration = durationMillis.coerceAtLeast(MIN_DURATION_MILLIS)
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()

        prefs.edit()
            .putBoolean(KEY_IS_LOCK_ACTIVE, true)
            .putLong(KEY_TOTAL_DURATION_MILLIS, safeDuration)
            .putLong(KEY_REMAINING_MILLIS, safeDuration)
            .putLong(KEY_SNAPSHOT_ELAPSED_REALTIME, nowElapsed)
            .putInt(KEY_BOOT_COUNT, bootCount)
            .commit()

        _state.value = readStateInternal()
        return true
    }

    /**
     * Extends an active Strict Lock session. Strictly refuses zero or negative values.
     */
    @Synchronized
    fun extendLock(additionalMillis: Long): Boolean {
        if (!isLockCurrentlyActive()) return false
        if (additionalMillis < MIN_DURATION_MILLIS) return false

        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        val currentRemaining = computeRemainingMillis(nowElapsed, bootCount)
        if (currentRemaining <= 0L) {
            finishExpiredLock()
            return false
        }

        val newRemaining = currentRemaining + additionalMillis
        val currentTotal = prefs.getLong(KEY_TOTAL_DURATION_MILLIS, currentRemaining)
        val newTotal = maxOf(currentTotal + additionalMillis, newRemaining)

        prefs.edit()
            .putLong(KEY_REMAINING_MILLIS, newRemaining)
            .putLong(KEY_TOTAL_DURATION_MILLIS, newTotal)
            .putLong(KEY_SNAPSHOT_ELAPSED_REALTIME, nowElapsed)
            .putInt(KEY_BOOT_COUNT, bootCount)
            .commit()

        _state.value = readStateInternal()
        return true
    }

    /**
     * Used exclusively by the automated schedule engine.
     * Starts a Strict Lock for [remainingWindowMillis], or extends an already-active lock
     * if the scheduled window runs beyond the current remaining duration.
     */
    @Synchronized
    fun startOrExtendForSchedule(remainingWindowMillis: Long): Boolean {
        if (remainingWindowMillis < MIN_SCHEDULE_ACTIVATION_MILLIS) return false
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()

        return if (isLockCurrentlyActive()) {
            val currentRemaining = computeRemainingMillis(nowElapsed, bootCount)
            val extraNeeded = remainingWindowMillis - currentRemaining
            if (extraNeeded >= 15_000L) {
                val newRemaining = currentRemaining + extraNeeded
                val currentTotal = prefs.getLong(KEY_TOTAL_DURATION_MILLIS, currentRemaining)
                val newTotal = maxOf(currentTotal + extraNeeded, newRemaining)
                prefs.edit()
                    .putLong(KEY_REMAINING_MILLIS, newRemaining)
                    .putLong(KEY_TOTAL_DURATION_MILLIS, newTotal)
                    .putLong(KEY_SNAPSHOT_ELAPSED_REALTIME, nowElapsed)
                    .putInt(KEY_BOOT_COUNT, bootCount)
                    .commit()
                _state.value = readStateInternal()
                true
            } else {
                false
            }
        } else {
            prefs.edit()
                .putBoolean(KEY_IS_LOCK_ACTIVE, true)
                .putLong(KEY_TOTAL_DURATION_MILLIS, remainingWindowMillis)
                .putLong(KEY_REMAINING_MILLIS, remainingWindowMillis)
                .putLong(KEY_SNAPSHOT_ELAPSED_REALTIME, nowElapsed)
                .putInt(KEY_BOOT_COUNT, bootCount)
                .commit()
            _state.value = readStateInternal()
            true
        }
    }

    /**
     * Toggle an app in pre-lock setup mode. While locked, can ONLY add packages, never remove.
     */
    @Synchronized
    fun toggleBlockedPackage(packageName: String) {
        val current = getBlockedPackages().toMutableSet()
        if (isLockCurrentlyActive()) {
            // Strict mode invariant: additions only!
            current.add(packageName)
        } else {
            if (!current.add(packageName)) {
                current.remove(packageName)
            }
        }
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, current).apply()
        _state.value = readStateInternal()
    }

    /**
     * Permanently append additional apps during an active Strict Lock (or in setup).
     */
    @Synchronized
    fun addBlockedPackages(packagesToAdd: Collection<String>) {
        if (packagesToAdd.isEmpty()) return
        val current = getBlockedPackages().toMutableSet()
        current.addAll(packagesToAdd)
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, current).commit()
        _state.value = readStateInternal()
    }

    /**
     * Toggle a domain in pre-lock setup mode. While locked, can ONLY enable/add, never disable.
     */
    @Synchronized
    fun toggleBlockedDomain(domain: String) {
        val normalized = UrlMatcher.normalizeDomain(domain) ?: return
        val current = getBlockedDomains().toMutableSet()
        if (isLockCurrentlyActive()) {
            current.add(normalized)
        } else {
            if (!current.add(normalized)) {
                current.remove(normalized)
            }
        }
        prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, current).apply()
        _state.value = readStateInternal()
    }

    /**
     * Adds a custom domain (normalized) and enables it. Works both before and during Strict Lock.
     */
    @Synchronized
    fun addCustomDomain(rawInput: String): String? {
        val normalized = UrlMatcher.normalizeDomain(rawInput) ?: return null
        val domains = getBlockedDomains().toMutableSet()
        domains.add(normalized)

        val custom = getCustomDomains().toMutableSet()
        if (normalized !in UrlMatcher.DEFAULT_QUICK_DOMAINS) {
            custom.add(normalized)
        }

        prefs.edit()
            .putStringSet(KEY_BLOCKED_DOMAINS, domains)
            .putStringSet(KEY_CUSTOM_DOMAINS, custom)
            .commit()

        _state.value = readStateInternal()
        return normalized
    }

    /**
     * Removes a custom domain. Strictly blocked while a lock session is active!
     */
    @Synchronized
    fun removeCustomDomain(domain: String): Boolean {
        if (isLockCurrentlyActive()) return false
        val normalized = UrlMatcher.normalizeDomain(domain) ?: return false

        val domains = getBlockedDomains().toMutableSet()
        domains.remove(normalized)

        val custom = getCustomDomains().toMutableSet()
        custom.remove(normalized)

        prefs.edit()
            .putStringSet(KEY_BLOCKED_DOMAINS, domains)
            .putStringSet(KEY_CUSTOM_DOMAINS, custom)
            .apply()

        _state.value = readStateInternal()
        return true
    }

    @Synchronized
    private fun finishExpiredLock() {
        prefs.edit()
            .putBoolean(KEY_IS_LOCK_ACTIVE, false)
            .putLong(KEY_REMAINING_MILLIS, 0L)
            .putLong(KEY_TOTAL_DURATION_MILLIS, 0L)
            .putLong(KEY_SNAPSHOT_ELAPSED_REALTIME, -1L)
            .commit()
        _state.value = readStateInternal()
    }

    fun getBlockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun getBlockedDomains(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_DOMAINS, UrlMatcher.DEFAULT_QUICK_DOMAINS.toSet())?.toSet()
            ?: UrlMatcher.DEFAULT_QUICK_DOMAINS.toSet()

    fun getCustomDomains(): Set<String> =
        prefs.getStringSet(KEY_CUSTOM_DOMAINS, emptySet())?.toSet() ?: emptySet()

    private fun readStateInternal(): LockState {
        val activeFlag = prefs.getBoolean(KEY_IS_LOCK_ACTIVE, false)
        val remaining = if (activeFlag) computeRemainingMillis() else 0L
        val effectivelyActive = activeFlag && remaining > 0L
        val total = if (effectivelyActive) {
            prefs.getLong(KEY_TOTAL_DURATION_MILLIS, remaining).coerceAtLeast(remaining)
        } else {
            0L
        }

        return LockState(
            isLockActive = effectivelyActive,
            remainingMillis = remaining,
            totalDurationMillis = total,
            blockedPackages = getBlockedPackages(),
            blockedDomains = getBlockedDomains(),
            customDomains = getCustomDomains()
        )
    }

    companion object {
        private const val PREFS_NAME = "ironfocus_strict_vault"
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_IS_LOCK_ACTIVE = "is_lock_active"
        private const val KEY_TOTAL_DURATION_MILLIS = "total_duration_millis"
        private const val KEY_REMAINING_MILLIS = "remaining_millis"
        private const val KEY_SNAPSHOT_ELAPSED_REALTIME = "snapshot_elapsed_realtime"
        private const val KEY_BOOT_COUNT = "boot_count"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_BLOCKED_DOMAINS = "blocked_domains"
        private const val KEY_CUSTOM_DOMAINS = "custom_domains"

        const val MIN_DURATION_MILLIS = 60_000L
        const val MIN_SCHEDULE_ACTIVATION_MILLIS = 5_000L

        @Volatile
        private var instance: LockRepository? = null

        fun getInstance(context: Context): LockRepository {
            return instance ?: synchronized(this) {
                instance ?: LockRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
