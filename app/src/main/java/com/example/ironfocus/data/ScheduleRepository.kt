package com.example.ironfocus.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.example.ironfocus.domain.FocusSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Direct-Boot-aware repository storing recurring & one-time focus schedules
 * in Device Protected Storage so BootReceiver and ScheduleAlarmReceiver can read
 * and reschedule alarms immediately upon reboot.
 */
class ScheduleRepository private constructor(appContext: Context) {

    private val deviceProtectedContext: Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.createDeviceProtectedStorageContext()
        } else {
            appContext
        }

    private val prefs: SharedPreferences =
        deviceProtectedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _schedules = MutableStateFlow(readSchedulesInternal())
    val schedules: StateFlow<List<FocusSchedule>> = _schedules.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_SCHEDULES_JSON) {
            _schedules.value = readSchedulesInternal()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        _schedules.value = readSchedulesInternal()
    }

    fun getAll(): List<FocusSchedule> = readSchedulesInternal()

    fun getById(id: String): FocusSchedule? = readSchedulesInternal().firstOrNull { it.id == id }

    @Synchronized
    fun upsertSchedule(schedule: FocusSchedule): List<FocusSchedule> {
        val current = readSchedulesInternal().toMutableList()
        val index = current.indexOfFirst { it.id == schedule.id }
        if (index >= 0) {
            current[index] = schedule
        } else {
            current.add(schedule)
        }
        val sorted = sortSchedules(current)
        saveSchedulesInternal(sorted)
        return sorted
    }

    @Synchronized
    fun setScheduleEnabled(id: String, enabled: Boolean): FocusSchedule? {
        val current = readSchedulesInternal().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = current[index].copy(isEnabled = enabled)
        current[index] = updated
        saveSchedulesInternal(sortSchedules(current))
        return updated
    }

    @Synchronized
    fun deleteSchedule(id: String): Boolean {
        val current = readSchedulesInternal().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveSchedulesInternal(sortSchedules(current))
        }
        return removed
    }

    private fun sortSchedules(list: List<FocusSchedule>): List<FocusSchedule> {
        return list.sortedWith(
            compareBy<FocusSchedule> { it.startTotalMinutes }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun saveSchedulesInternal(list: List<FocusSchedule>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("days", JSONArray(item.daysOfWeek.sorted()))
                put("startHour", item.startHour)
                put("startMinute", item.startMinute)
                put("endHour", item.endHour)
                put("endMinute", item.endMinute)
                put("repeatWeekly", item.repeatWeekly)
                put("isEnabled", item.isEnabled)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SCHEDULES_JSON, array.toString()).commit()
        _schedules.value = list
    }

    private fun readSchedulesInternal(): List<FocusSchedule> {
        val raw = prefs.getString(KEY_SCHEDULES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<FocusSchedule>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val daysArray = obj.optJSONArray("days")
                val days = mutableSetOf<Int>()
                if (daysArray != null) {
                    for (j in 0 until daysArray.length()) {
                        val day = daysArray.optInt(j, -1)
                        if (day in 1..7) days.add(day)
                    }
                }
                result.add(
                    FocusSchedule(
                        id = obj.optString("id"),
                        name = obj.optString("name", "Focus Block"),
                        daysOfWeek = days,
                        startHour = obj.optInt("startHour", 9).coerceIn(0, 23),
                        startMinute = obj.optInt("startMinute", 0).coerceIn(0, 59),
                        endHour = obj.optInt("endHour", 17).coerceIn(0, 23),
                        endMinute = obj.optInt("endMinute", 0).coerceIn(0, 59),
                        repeatWeekly = obj.optBoolean("repeatWeekly", true),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
            sortSchedules(result)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "ironfocus_strict_vault"
        private const val KEY_SCHEDULES_JSON = "focus_schedules_json"

        @Volatile
        private var instance: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
