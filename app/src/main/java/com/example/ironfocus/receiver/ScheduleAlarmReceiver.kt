package com.example.ironfocus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.data.ScheduleRepository
import com.example.ironfocus.service.LockCountdownService
import com.example.ironfocus.service.ScheduleAlarmManager

/**
 * Automatically starts Strict Lock when a scheduled day/time alarm fires,
 * and arms the next weekly recurrence if [repeatWeekly] is enabled.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TRIGGER_SCHEDULE) return
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return

        val scheduleRepo = ScheduleRepository.getInstance(context)
        val schedule = scheduleRepo.getById(scheduleId) ?: return
        if (!schedule.isEnabled) return

        val lockRepo = LockRepository.getInstance(context)
        val durationMillis = schedule.durationMillis

        if (lockRepo.isLockCurrentlyActive()) {
            // If already locked, extend only if this scheduled window runs longer than current remaining time
            val currentRemaining = lockRepo.computeRemainingMillis()
            val extraNeeded = durationMillis - currentRemaining
            if (extraNeeded >= LockRepository.MIN_DURATION_MILLIS) {
                lockRepo.extendLock(extraNeeded)
            }
        } else {
            lockRepo.startLock(durationMillis)
        }

        // Launch ongoing tamper-proof countdown foreground service
        LockCountdownService.start(context)

        // Repeat every week on the same selected days, or disable if one-time
        if (schedule.repeatWeekly) {
            ScheduleAlarmManager.scheduleNextOccurrence(context, schedule)
        } else {
            scheduleRepo.setScheduleEnabled(schedule.id, false)
            ScheduleAlarmManager.cancelSchedule(context, schedule.id)
        }
    }

    companion object {
        const val ACTION_TRIGGER_SCHEDULE = "com.example.ironfocus.ACTION_TRIGGER_SCHEDULE"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }
}
