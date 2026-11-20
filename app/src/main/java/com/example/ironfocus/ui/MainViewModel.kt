package com.example.ironfocus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ironfocus.data.InstalledApp
import com.example.ironfocus.data.InstalledAppsProvider
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.data.LockState
import com.example.ironfocus.data.ScheduleRepository
import com.example.ironfocus.domain.FocusSchedule
import com.example.ironfocus.domain.ScheduleTimeCalculator
import com.example.ironfocus.domain.UrlMatcher
import com.example.ironfocus.service.LockCountdownService
import com.example.ironfocus.service.ScheduleAlarmManager
import com.example.ironfocus.system.PermissionsHelper
import com.example.ironfocus.system.PermissionsStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SetupTab {
    DASHBOARD,
    SCHEDULES,
    APPS,
    WEBSITES
}

data class NextScheduledLockInfo(
    val scheduleName: String,
    val triggerTimeMillis: Long,
    val formattedTrigger: String,
    val repeatWeekly: Boolean
)

data class IronFocusUiState(
    val lockState: LockState = LockState(),
    val permissions: PermissionsStatus = PermissionsStatus(),
    val schedules: List<FocusSchedule> = emptyList(),
    val nextScheduledLock: NextScheduledLockInfo? = null,
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoadingApps: Boolean = true,
    val appSearchQuery: String = "",
    val showSelectedAppsOnly: Boolean = false,
    val selectedDays: Int = 0,
    val selectedHours: Int = 1,
    val selectedMinutes: Int = 0,
    val websiteInput: String = "",
    val websiteInputError: Boolean = false,
    val activeTab: SetupTab = SetupTab.DASHBOARD
) {
    val selectedDurationMillis: Long
        get() = ((selectedDays * 24L * 60L) + (selectedHours * 60L) + selectedMinutes) * 60_000L

    val hasValidDuration: Boolean
        get() = selectedDurationMillis >= LockRepository.MIN_DURATION_MILLIS

    val hasAnyTargets: Boolean
        get() = lockState.blockedPackages.isNotEmpty() || lockState.blockedDomains.isNotEmpty()

    val canEngageLock: Boolean
        get() = permissions.canStartStrictLock && hasValidDuration && hasAnyTargets

    val enabledSchedulesCount: Int
        get() = schedules.count { it.isEnabled }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = LockRepository.getInstance(appContext)
    private val scheduleRepository = ScheduleRepository.getInstance(appContext)

    private val _uiState = MutableStateFlow(
        IronFocusUiState(
            lockState = repository.refreshLiveState(),
            permissions = PermissionsHelper.checkAll(appContext),
            schedules = scheduleRepository.getAll(),
            nextScheduledLock = computeNextScheduledInfo(scheduleRepository.getAll())
        )
    )
    val uiState: StateFlow<IronFocusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { lockState ->
                _uiState.update { it.copy(lockState = lockState) }
            }
        }

        viewModelScope.launch {
            scheduleRepository.schedules.collect { schedules ->
                _uiState.update {
                    it.copy(
                        schedules = schedules,
                        nextScheduledLock = computeNextScheduledInfo(schedules)
                    )
                }
            }
        }

        // Periodic 1-second UI monotonic refresh while lock is active
        viewModelScope.launch {
            while (isActive) {
                if (repository.isLockCurrentlyActive() || _uiState.value.lockState.isLockActive) {
                    val refreshed = repository.refreshLiveState()
                    _uiState.update { it.copy(lockState = refreshed) }
                }
                delay(1_000L)
            }
        }

        loadInstalledApps()

        if (repository.isLockCurrentlyActive()) {
            LockCountdownService.start(appContext)
        }
        ScheduleAlarmManager.rescheduleAll(appContext)
    }

    fun refreshOnResume() {
        val permissions = PermissionsHelper.checkAll(appContext)
        val lockState = repository.refreshLiveState()
        val schedules = scheduleRepository.getAll()
        _uiState.update {
            it.copy(
                permissions = permissions,
                lockState = lockState,
                schedules = schedules,
                nextScheduledLock = computeNextScheduledInfo(schedules)
            )
        }
        if (lockState.isLockActive) {
            LockCountdownService.start(appContext)
        }
        ScheduleAlarmManager.rescheduleAll(appContext)
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = InstalledAppsProvider.loadBlockableApps(appContext)
            _uiState.update {
                it.copy(
                    installedApps = apps,
                    isLoadingApps = false
                )
            }
        }
    }

    fun selectTab(tab: SetupTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateAppSearchQuery(query: String) {
        _uiState.update { it.copy(appSearchQuery = query) }
    }

    fun setShowSelectedAppsOnly(selectedOnly: Boolean) {
        _uiState.update { it.copy(showSelectedAppsOnly = selectedOnly) }
    }

    fun updateDuration(days: Int, hours: Int, minutes: Int) {
        val safeDays = days.coerceIn(0, 30)
        val safeHours = hours.coerceIn(0, 23)
        val safeMinutes = minutes.coerceIn(0, 59)
        val finalMinutes = if (safeDays == 0 && safeHours == 0 && safeMinutes == 0) 1 else safeMinutes
        _uiState.update {
            it.copy(
                selectedDays = safeDays,
                selectedHours = safeHours,
                selectedMinutes = finalMinutes
            )
        }
    }

    fun toggleAppBlocked(packageName: String) {
        repository.toggleBlockedPackage(packageName)
    }

    fun addAppsDuringLock(packages: Set<String>) {
        repository.addBlockedPackages(packages)
    }

    fun toggleDomainBlocked(domain: String) {
        repository.toggleBlockedDomain(domain)
    }

    fun updateWebsiteInput(input: String) {
        _uiState.update {
            it.copy(
                websiteInput = input,
                websiteInputError = false
            )
        }
    }

    fun submitWebsiteInput(): Boolean {
        val normalized = repository.addCustomDomain(_uiState.value.websiteInput)
        return if (normalized != null) {
            _uiState.update {
                it.copy(
                    websiteInput = "",
                    websiteInputError = false
                )
            }
            true
        } else {
            _uiState.update { it.copy(websiteInputError = true) }
            false
        }
    }

    fun addWebsiteDuringLock(rawDomain: String): Boolean {
        return repository.addCustomDomain(rawDomain) != null
    }

    fun removeCustomDomain(domain: String) {
        repository.removeCustomDomain(domain)
    }

    fun normalizePreview(raw: String): String? = UrlMatcher.normalizeDomain(raw)

    fun saveSchedule(schedule: FocusSchedule) {
        val sanitizedName = schedule.name.trim().ifEmpty { "Focus Block" }
        val sanitized = schedule.copy(name = sanitizedName)
        scheduleRepository.upsertSchedule(sanitized)
        if (sanitized.isEnabled) {
            ScheduleAlarmManager.scheduleNextOccurrence(appContext, sanitized)
        } else {
            ScheduleAlarmManager.cancelSchedule(appContext, sanitized.id)
        }
    }

    fun toggleScheduleEnabled(scheduleId: String, enabled: Boolean) {
        // Strict mode invariant: cannot disable schedules during an active lock
        if (!enabled && repository.isLockCurrentlyActive()) return
        val updated = scheduleRepository.setScheduleEnabled(scheduleId, enabled) ?: return
        if (updated.isEnabled) {
            ScheduleAlarmManager.scheduleNextOccurrence(appContext, updated)
        } else {
            ScheduleAlarmManager.cancelSchedule(appContext, updated.id)
        }
    }

    fun deleteSchedule(scheduleId: String) {
        // Strict mode invariant: cannot delete schedules during an active lock
        if (repository.isLockCurrentlyActive()) return
        ScheduleAlarmManager.cancelSchedule(appContext, scheduleId)
        scheduleRepository.deleteSchedule(scheduleId)
    }

    fun engageStrictLock(): Boolean {
        val livePermissions = PermissionsHelper.checkAll(appContext)
        _uiState.update { it.copy(permissions = livePermissions) }
        if (!livePermissions.canStartStrictLock) return false

        val durationMillis = _uiState.value.selectedDurationMillis
        if (durationMillis < LockRepository.MIN_DURATION_MILLIS) return false

        val started = repository.startLock(durationMillis)
        if (started) {
            LockCountdownService.start(appContext)
        }
        return started
    }

    fun extendActiveLock(days: Int, hours: Int, minutes: Int): Boolean {
        val additionalMillis = ((days * 24L * 60L) + (hours * 60L) + minutes) * 60_000L
        if (additionalMillis < LockRepository.MIN_DURATION_MILLIS) return false
        val extended = repository.extendLock(additionalMillis)
        if (extended) {
            LockCountdownService.start(appContext)
        }
        return extended
    }

    private fun computeNextScheduledInfo(schedules: List<FocusSchedule>): NextScheduledLockInfo? {
        val now = System.currentTimeMillis()
        var bestSchedule: FocusSchedule? = null
        var bestTrigger = Long.MAX_VALUE

        for (schedule in schedules) {
            val next = ScheduleTimeCalculator.computeNextTriggerTimeMillis(schedule, now) ?: continue
            if (next < bestTrigger) {
                bestTrigger = next
                bestSchedule = schedule
            }
        }

        val winner = bestSchedule ?: return null
        return NextScheduledLockInfo(
            scheduleName = winner.name,
            triggerTimeMillis = bestTrigger,
            formattedTrigger = ScheduleTimeCalculator.formatNextTriggerLabel(bestTrigger),
            repeatWeekly = winner.repeatWeekly
        )
    }
}
