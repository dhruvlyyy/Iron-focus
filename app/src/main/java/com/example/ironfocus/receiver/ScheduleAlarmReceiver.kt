package com.example.ironfocus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ironfocus.service.ScheduleActivator

/**
 * Automatically starts Strict Lock when a scheduled day/time alarm fires,
 * and arms the next weekly recurrence if [repeatWeekly] is enabled.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TRIGGER_SCHEDULE) return
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        ScheduleActivator.activateFromAlarm(context.applicationContext, scheduleId)
    }

    companion object {
        const val ACTION_TRIGGER_SCHEDULE = "com.example.ironfocus.ACTION_TRIGGER_SCHEDULE"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }
}
