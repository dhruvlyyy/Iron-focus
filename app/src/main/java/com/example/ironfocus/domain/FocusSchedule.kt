package com.example.ironfocus.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Represents a scheduled automatic Strict Lock window.
 *
 * [daysOfWeek] uses ISO-8601 day numbering:
 * 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday, 6 = Saturday, 7 = Sunday.
 */
data class FocusSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val daysOfWeek: Set<Int>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val repeatWeekly: Boolean = true,
    val isEnabled: Boolean = true
) {
    val startTotalMinutes: Int
        get() = (startHour.coerceIn(0, 23) * 60) + startMinute.coerceIn(0, 59)

    val endTotalMinutes: Int
        get() = (endHour.coerceIn(0, 23) * 60) + endMinute.coerceIn(0, 59)

    val isOvernight: Boolean
        get() = endTotalMinutes <= startTotalMinutes

    val durationMinutes: Int
        get() = ScheduleTimeCalculator.computeDurationMinutes(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute
        )

    val durationMillis: Long
        get() = durationMinutes * 60_000L
}

object ScheduleTimeCalculator {

    val ALL_DAYS_ISO: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
    val WEEKDAYS_ISO: Set<Int> = setOf(1, 2, 3, 4, 5)
    val WEEKENDS_ISO: Set<Int> = setOf(6, 7)

    fun dayShortLetter(isoDay: Int): String = when (isoDay) {
        1 -> "M"
        2 -> "T"
        3 -> "W"
        4 -> "T"
        5 -> "F"
        6 -> "S"
        7 -> "S"
        else -> "?"
    }

    fun dayShortName(isoDay: Int): String = when (isoDay) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> ""
    }

    fun calendarDayToIso(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    /**
     * Computes duration in minutes between start and end time.
     * If end <= start, wraps overnight across midnight (e.g. 22:00 -> 06:00 = 480 minutes).
     * Guarantees at least 1 minute.
     */
    fun computeDurationMinutes(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): Int {
        val start = (startHour.coerceIn(0, 23) * 60) + startMinute.coerceIn(0, 59)
        val end = (endHour.coerceIn(0, 23) * 60) + endMinute.coerceIn(0, 59)
        val raw = if (end > start) {
            end - start
        } else {
            (24 * 60 - start) + end
        }
        return raw.coerceAtLeast(1)
    }

    /**
     * Computes the exact next future trigger epoch millis for [schedule] after [nowMillis].
     * Returns null if disabled or no days are selected.
     */
    fun computeNextTriggerTimeMillis(
        schedule: FocusSchedule,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (!schedule.isEnabled || schedule.daysOfWeek.isEmpty()) return null

        val candidate = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, schedule.startHour.coerceIn(0, 23))
            set(Calendar.MINUTE, schedule.startMinute.coerceIn(0, 59))
        }

        // Check today through +7 days (covering next week's same day if today's time already passed)
        for (dayOffset in 0..7) {
            val checkCal = (candidate.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val isoDay = calendarDayToIso(checkCal.get(Calendar.DAY_OF_WEEK))
            if (isoDay in schedule.daysOfWeek && checkCal.timeInMillis > nowMillis) {
                return checkCal.timeInMillis
            }
        }
        return null
    }

    /**
     * Checks if [nowMillis] currently falls INSIDE an active occurrence of [schedule]
     * (including overnight schedules that began yesterday).
     * Returns remaining millis until scheduled end, or 0L if outside the window.
     */
    fun computeCurrentlyActiveRemainingMillis(
        schedule: FocusSchedule,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        if (!schedule.isEnabled || schedule.daysOfWeek.isEmpty()) return 0L

        val durationMs = schedule.durationMillis
        val base = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, schedule.startHour.coerceIn(0, 23))
            set(Calendar.MINUTE, schedule.startMinute.coerceIn(0, 59))
        }

        // Check both yesterday's start (for overnight windows) and today's start
        for (offset in listOf(-1, 0)) {
            val startCal = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val isoDay = calendarDayToIso(startCal.get(Calendar.DAY_OF_WEEK))
            if (isoDay in schedule.daysOfWeek) {
                val startMs = startCal.timeInMillis
                val endMs = startMs + durationMs
                if (nowMillis in startMs until endMs) {
                    return (endMs - nowMillis).coerceAtLeast(0L)
                }
            }
        }
        return 0L
    }

    fun formatTime12h(hour: Int, minute: Int): String {
        val h24 = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val amPm = if (h24 < 12) "AM" else "PM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format(Locale.US, "%d:%02d %s", h12, m, amPm)
    }

    fun formatDaysSummary(daysOfWeek: Set<Int>): String {
        val sorted = daysOfWeek.sorted()
        return when {
            sorted.size == 7 -> "Every day"
            sorted.toSet() == WEEKDAYS_ISO -> "Weekdays"
            sorted.toSet() == WEEKENDS_ISO -> "Weekends"
            sorted.isEmpty() -> "No days"
            else -> sorted.joinToString(", ") { dayShortName(it) }
        }
    }

    fun formatNextTriggerLabel(triggerMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = triggerMillis }
        val day = dayShortName(calendarDayToIso(cal.get(Calendar.DAY_OF_WEEK)))
        val time = formatTime12h(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        return "$day at $time"
    }
}
