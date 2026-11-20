package com.example.ironfocus.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.ironfocus.data.ScheduleRepository
import com.example.ironfocus.domain.FocusSchedule
import com.example.ironfocus.domain.ScheduleTimeCalculator
import com.example.ironfocus.receiver.ScheduleAlarmReceiver

/**
 * Manages AlarmManager alarms for weekly recurring and one-time FocusSchedules.
 * Uses setAlarmClock (highest-priority, Doze-exempt, FGS-exempt) whenever exact alarms
 * are permitted, falling back gracefully to setExactAndAllowWhileIdle / setAndAllowWhileIdle.
 */
object ScheduleAlarmManager {

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    fun rescheduleAll(context: Context) {
        val repository = ScheduleRepository.getInstance(context)
        val schedules = repository.getAll()
        for (schedule in schedules) {
            if (schedule.isEnabled && schedule.daysOfWeek.isNotEmpty()) {
                scheduleNextOccurrence(context, schedule)
            } else {
                cancelSchedule(context, schedule.id)
            }
        }
    }

    fun scheduleNextOccurrence(
        context: Context,
        schedule: FocusSchedule,
        minLeadMillis: Long = 0L
    ): Long? {
        cancelSchedule(context, schedule.id)
        if (!schedule.isEnabled || schedule.daysOfWeek.isEmpty()) return null

        val nextTriggerMillis = ScheduleTimeCalculator.computeNextTriggerTimeMillis(
            schedule = schedule,
            minLeadMillis = minLeadMillis
        ) ?: return null

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return null

        val triggerIntent = buildBroadcastPendingIntent(context, schedule.id)
        val showIntent = buildShowPendingIntent(context, schedule.id)

        try {
            if (canScheduleExactAlarms(context)) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTriggerMillis, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, triggerIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMillis,
                    triggerIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerMillis,
                triggerIntent
            )
        }

        return nextTriggerMillis
    }

    fun cancelSchedule(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildBroadcastPendingIntent(context, scheduleId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildBroadcastPendingIntent(context: Context, scheduleId: String): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_TRIGGER_SCHEDULE
            putExtra(ScheduleAlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val requestCode = (scheduleId.hashCode() and 0x7FFFFFFF)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildShowPendingIntent(context: Context, scheduleId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = ((scheduleId.hashCode() xor 0x5A5A5A) and 0x7FFFFFFF)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
