package com.example.ironfocus.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.service.LockCountdownService
import com.example.ironfocus.service.ScheduleActivator
import com.example.ironfocus.service.ScheduleAlarmManager

/**
 * Reboot, App-Update & System-Event Survival Receiver (Direct Boot + Standard Boot).
 * 1. Re-anchors monotonic uptime snapshot after a reboot and restarts LockCountdownService.
 * 2. Re-arms all weekly AlarmManager focus schedules after reboot, timezone change, or app update.
 * 3. Immediately engages Strict Lock if the current time is already inside an enabled schedule window.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (action !in validActions) return

        val appCtx = context.applicationContext
        val lockRepo = LockRepository.getInstance(appCtx)
        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"

        val remaining = if (isBootEvent) {
            lockRepo.reanchorAfterBoot()
        } else {
            lockRepo.computeRemainingMillis()
        }

        if (remaining > 0L) {
            LockCountdownService.start(appCtx)
        }

        // Check if any enabled schedule is active right now and re-arm all upcoming alarms
        ScheduleActivator.evaluateAndActivateNow(appCtx)
        ScheduleAlarmManager.rescheduleAll(appCtx)
    }
}
