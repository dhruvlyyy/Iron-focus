package com.example.ironfocus.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.data.ScheduleRepository
import com.example.ironfocus.domain.ScheduleTimeCalculator
import com.example.ironfocus.service.LockCountdownService
import com.example.ironfocus.service.ScheduleAlarmManager

/**
 * Reboot & System-Event Survival Receiver (Direct Boot + Standard Boot).
 * 1. Re-anchors monotonic uptime snapshot after a reboot and restarts LockCountdownService.
 * 2. Re-arms all weekly AlarmManager focus schedules (since Android clears alarms on reboot).
 * 3. If the device boots into an already-active scheduled focus window, engages Strict Lock.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (action !in validActions) return

        val lockRepo = LockRepository.getInstance(context)
        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"

        val remaining = if (isBootEvent) {
            lockRepo.reanchorAfterBoot()
        } else {
            lockRepo.computeRemainingMillis()
        }

        if (remaining > 0L) {
            LockCountdownService.start(context)
        } else if (isBootEvent) {
            // Check if any enabled schedule is currently mid-window after boot
            val schedules = ScheduleRepository.getInstance(context).getAll()
            val maxActiveRemaining = schedules.maxOfOrNull {
                ScheduleTimeCalculator.computeCurrentlyActiveRemainingMillis(it)
            } ?: 0L
            if (maxActiveRemaining >= LockRepository.MIN_DURATION_MILLIS) {
                lockRepo.startLock(maxActiveRemaining)
                LockCountdownService.start(context)
            }
        }

        // Always re-arm weekly recurring AlarmManager triggers
        ScheduleAlarmManager.rescheduleAll(context)
    }
}
