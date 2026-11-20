package com.example.ironfocus.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.ironfocus.data.ScheduleRepository
import com.example.ironfocus.domain.FocusSchedule
import com.example.ironfocus.domain.ScheduleTimeCalculator
import com.example.ironfocus.receiver.ScheduleAlarmReceiver

/**
 * Manages AlarmManager alarms for weekly recurring and one-time FocusSchedules.
 * Uses setExactAndAllowWhileIdle when exact alarm permission is available, with a
 * graceful setAndAllowWhileIdle fallback so scheduling never crashes on Android 12+.
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

    fun scheduleNextOccurrence(context: Context, schedule: FocusSchedule): Long? {
        cancelSchedule(context, schedule.id)
        if (!schedule.isEnabled || schedule.daysOfWeek.isEmpty()) return null

        val nextTriggerMillis = ScheduleTimeCalculator.computeNextTriggerTimeMillis(schedule)
            ?: return null

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return null

        val pendingIntent = buildPendingIntent(context, schedule.id)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerMillis,
                pendingIntent
            )
        }

        return nextTriggerMillis
    }

    fun cancelSchedule(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, scheduleId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(context: Context, scheduleId: String): PendingIntent {
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
}
