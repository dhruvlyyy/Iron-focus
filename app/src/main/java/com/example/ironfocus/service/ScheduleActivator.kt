package com.example.ironfocus.service

import android.content.Context
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.data.ScheduleRepository
import com.example.ironfocus.domain.FocusSchedule
import com.example.ironfocus.domain.ScheduleTimeCalculator

/**
 * Unified, deduplicated activation engine for automated weekly and one-time FocusSchedules.
 *
 * Called redundantly from:
 * 1. ScheduleAlarmReceiver (AlarmManager exact/alarm-clock trigger)
 * 2. BlockerAccessibilityService (real-time event checks + 15-second Doze-immune watchdog)
 * 3. MainViewModel (when saving/enabling schedules or while the app is open)
 * 4. BootReceiver (on reboot, timezone change, or app update via MY_PACKAGE_REPLACED)
 *
 * Each scheduled occurrence (scheduleId + windowStartMillis) is marked activated once so it
 * activates reliably on time without re-triggering at the very end of the window.
 */
object ScheduleActivator {

    private const val EARLY_ALARM_TOLERANCE_MS = 10_000L
    private const val RESCHEDULE_LEAD_MS = 65_000L

    /**
     * Triggered directly by AlarmManager for [scheduleId].
     * Tolerates alarms firing a few seconds early before the exact minute boundary.
     */
    @Synchronized
    fun activateFromAlarm(
        context: Context,
        scheduleId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val scheduleRepo = ScheduleRepository.getInstance(context)
        val schedule = scheduleRepo.getById(scheduleId) ?: return false
        if (!schedule.isEnabled) return false

        val activeWindow = ScheduleTimeCalculator.findActiveWindow(schedule, nowMillis)
            ?: ScheduleTimeCalculator.findActiveWindow(schedule, nowMillis + EARLY_ALARM_TOLERANCE_MS)

        var activated = false
        if (activeWindow != null) {
            activated = engageWindowIfNeeded(
                context = context,
                scheduleRepo = scheduleRepo,
                schedule = schedule,
                windowStartMillis = activeWindow.windowStartMillis,
                remainingMillis = activeWindow.remainingMillis
            )
        }

        // Advance weekly recurrence (skipping past the current start minute) or disable one-time schedule
        if (schedule.repeatWeekly) {
            ScheduleAlarmManager.scheduleNextOccurrence(
                context = context,
                schedule = schedule,
                minLeadMillis = RESCHEDULE_LEAD_MS
            )
        } else if (activated) {
            scheduleRepo.setScheduleEnabled(schedule.id, false)
            ScheduleAlarmManager.cancelSchedule(context, schedule.id)
        }

        return activated
    }

    /**
     * Scans all enabled schedules and immediately engages Strict Lock if [nowMillis]
     * is currently inside any enabled schedule window that hasn't been activated yet.
     *
     * Returns true if a lock was newly started or extended.
     */
    @Synchronized
    fun evaluateAndActivateNow(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val scheduleRepo = ScheduleRepository.getInstance(context)
        val schedules = scheduleRepo.getAll()
        if (schedules.isEmpty()) return false

        var anyActivated = false
        for (schedule in schedules) {
            if (!schedule.isEnabled || schedule.daysOfWeek.isEmpty()) continue
            val activeWindow = ScheduleTimeCalculator.findActiveWindow(schedule, nowMillis) ?: continue

            val triggered = engageWindowIfNeeded(
                context = context,
                scheduleRepo = scheduleRepo,
                schedule = schedule,
                windowStartMillis = activeWindow.windowStartMillis,
                remainingMillis = activeWindow.remainingMillis
            )
            if (triggered) {
                anyActivated = true
                if (schedule.repeatWeekly) {
                    ScheduleAlarmManager.scheduleNextOccurrence(
                        context = context,
                        schedule = schedule,
                        minLeadMillis = RESCHEDULE_LEAD_MS
                    )
                } else {
                    scheduleRepo.setScheduleEnabled(schedule.id, false)
                    ScheduleAlarmManager.cancelSchedule(context, schedule.id)
                }
            }
        }
        return anyActivated
    }

    private fun engageWindowIfNeeded(
        context: Context,
        scheduleRepo: ScheduleRepository,
        schedule: FocusSchedule,
        windowStartMillis: Long,
        remainingMillis: Long
    ): Boolean {
        if (remainingMillis < LockRepository.MIN_SCHEDULE_ACTIVATION_MILLIS) return false
        if (scheduleRepo.hasActivatedWindow(schedule.id, windowStartMillis)) return false

        scheduleRepo.markWindowActivated(schedule.id, windowStartMillis)

        val lockRepo = LockRepository.getInstance(context)
        lockRepo.startOrExtendForSchedule(remainingMillis)
        LockCountdownService.start(context)
        return true
    }
}
