package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.data.ScheduleRepository
import com.example.ironfocus.domain.FocusSchedule
import com.example.ironfocus.domain.ScheduleTimeCalculator
import com.example.ironfocus.service.ScheduleActivator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("IronFocus", appName)
    }

    @Test
    fun `schedule activator engages lock inside active window and deduplicates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduleRepo = ScheduleRepository.getInstance(context)
        val lockRepo = LockRepository.getInstance(context)

        val nowCal = Calendar.getInstance()
        val isoToday = ScheduleTimeCalculator.calendarDayToIso(nowCal.get(Calendar.DAY_OF_WEEK))
        val startCal = (nowCal.clone() as Calendar).apply { add(Calendar.MINUTE, -5) }
        val endCal = (nowCal.clone() as Calendar).apply { add(Calendar.MINUTE, 25) }

        val schedule = FocusSchedule(
            id = "test-active-schedule",
            name = "Active Window Test",
            daysOfWeek = setOf(isoToday),
            startHour = startCal.get(Calendar.HOUR_OF_DAY),
            startMinute = startCal.get(Calendar.MINUTE),
            endHour = endCal.get(Calendar.HOUR_OF_DAY),
            endMinute = endCal.get(Calendar.MINUTE),
            repeatWeekly = true,
            isEnabled = true
        )
        scheduleRepo.upsertSchedule(schedule)

        val firstActivation = ScheduleActivator.evaluateAndActivateNow(context, nowCal.timeInMillis)
        assertTrue("Expected schedule to activate immediately inside active window", firstActivation)
        assertTrue("Expected LockRepository to be active after schedule activation", lockRepo.isLockCurrentlyActive())

        val secondActivation = ScheduleActivator.evaluateAndActivateNow(context, nowCal.timeInMillis)
        assertFalse("Expected same schedule window occurrence not to double-activate", secondActivation)
    }
}
