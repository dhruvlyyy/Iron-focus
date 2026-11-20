package com.example

import com.example.ironfocus.domain.BrowserRegistry
import com.example.ironfocus.domain.FocusSchedule
import com.example.ironfocus.domain.ScheduleTimeCalculator
import com.example.ironfocus.domain.TamperGuardConfig
import com.example.ironfocus.domain.TimeFormatter
import com.example.ironfocus.domain.UrlMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class IronFocusLogicTest {

    @Test
    fun normalizeDomain_stripsProtocolsWwwPathsAndTrailingSlashes() {
        assertEquals("instagram.com", UrlMatcher.normalizeDomain("https://www.instagram.com/reels/"))
        assertEquals("x.com", UrlMatcher.normalizeDomain("HTTP://WWW.X.COM/home?lang=en"))
        assertEquals("reddit.com", UrlMatcher.normalizeDomain("  www.reddit.com/// "))
        assertEquals("m.youtube.com", UrlMatcher.normalizeDomain("https://m.youtube.com/watch?v=123"))
        assertNull(UrlMatcher.normalizeDomain("not a domain"))
        assertNull(UrlMatcher.normalizeDomain(""))
    }

    @Test
    fun findMatchingBlockedDomain_matchesExactAndSubdomainsWithoutFalsePositives() {
        val blocked = setOf("instagram.com", "x.com", "twitter.com")

        assertEquals("instagram.com", UrlMatcher.findMatchingBlockedDomain("https://www.instagram.com/explore", blocked))
        assertEquals("x.com", UrlMatcher.findMatchingBlockedDomain("x.com/home", blocked))
        assertEquals("x.com", UrlMatcher.findMatchingBlockedDomain("https://mobile.x.com/i/flow", blocked))
        assertEquals("twitter.com", UrlMatcher.findMatchingBlockedDomain("TWITTER.COM", blocked))
        assertNull(UrlMatcher.findMatchingBlockedDomain("https://www.dropbox.com/home", blocked))
        assertNull(UrlMatcher.findMatchingBlockedDomain("https://www.box.com/files", blocked))
    }

    @Test
    fun tamperGuard_detectsSettingsPackagesAndForbiddenKeywords() {
        assertTrue(TamperGuardConfig.isGuardedSettingsOrSecurityPackage("com.android.settings"))
        assertTrue(TamperGuardConfig.isGuardedSettingsOrSecurityPackage("com.google.android.packageinstaller"))
        assertTrue(TamperGuardConfig.isGuardedSettingsOrSecurityPackage("com.miui.securitycenter"))
        assertFalse(TamperGuardConfig.isGuardedSettingsOrSecurityPackage("com.android.chrome"))

        assertEquals("ironfocus", TamperGuardConfig.findForbiddenKeyword("App info - IronFocus"))
        assertEquals("device admin", TamperGuardConfig.findForbiddenKeyword("Deactivate Device Admin apps"))
        assertEquals("date & time", TamperGuardConfig.findForbiddenKeyword("Date & Time Settings"))
        assertEquals("force stop", TamperGuardConfig.findForbiddenKeyword("FORCE STOP"))
        assertNull(TamperGuardConfig.findForbiddenKeyword("Display brightness"))
    }

    @Test
    fun browserRegistry_containsAllRequiredBrowsersAndFallbackTokens() {
        assertEquals("com.android.chrome:id/url_bar", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["com.android.chrome"])
        assertEquals("com.brave.browser:id/url_bar", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["com.brave.browser"])
        assertEquals("com.microsoft.emmx:id/url_bar", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["com.microsoft.emmx"])
        assertEquals("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["org.mozilla.firefox"])
        assertEquals("com.sec.android.app.sbrowser:id/location_bar_edit_text", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["com.sec.android.app.sbrowser"])
        assertEquals("com.opera.browser:id/url_field", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["com.opera.browser"])
        assertEquals("com.duckduckgo.mobile.android:id/omnibarTextInput", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["com.duckduckgo.mobile.android"])
        assertEquals("com.vivaldi.browser:id/url_bar", BrowserRegistry.KNOWN_BROWSER_URL_BAR_IDS["com.vivaldi.browser"])

        assertTrue(BrowserRegistry.isFallbackUrlNodeId("com.kiwibrowser.browser:id/url_bar"))
        assertTrue(BrowserRegistry.isFallbackUrlNodeId("mark.via.gp:id/address_bar"))
        assertTrue(BrowserRegistry.isFallbackUrlNodeId("com.mi.browser:id/search_box"))
        assertFalse(BrowserRegistry.isFallbackUrlNodeId("com.example:id/submit_button"))
    }

    @Test
    fun timeFormatter_formatsCountdownAccurately() {
        assertEquals("01:00:00", TimeFormatter.formatCountdown(3_600_000L))
        assertEquals("01:02:03:04", TimeFormatter.formatCountdown(((1 * 86400) + (2 * 3600) + (3 * 60) + 4) * 1000L))
        assertEquals("1d 2h 30m", TimeFormatter.formatDurationHuman(((1 * 1440) + (2 * 60) + 30) * 60_000L))
    }

    @Test
    fun scheduleCalculator_computesSameDayAndOvernightDurations() {
        // 9:00 AM -> 5:00 PM = 8 hours = 480 min
        assertEquals(480, ScheduleTimeCalculator.computeDurationMinutes(9, 0, 17, 0))
        // 10:30 PM -> 6:30 AM overnight = 8 hours = 480 min
        assertEquals(480, ScheduleTimeCalculator.computeDurationMinutes(22, 30, 6, 30))
        // 11:00 PM -> 11:00 PM = full 24 hours = 1440 min
        assertEquals(1440, ScheduleTimeCalculator.computeDurationMinutes(23, 0, 23, 0))
    }

    @Test
    fun scheduleCalculator_computesNextWeeklyTriggerAcrossWeeks() {
        val utc = TimeZone.getTimeZone("UTC")
        // Monday 2026-11-16 at 10:00:00 UTC (ISO day 1 = Monday)
        val monday10am = Calendar.getInstance(utc).apply {
            set(2026, Calendar.NOVEMBER, 16, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Schedule set for Mondays at 9:00 AM -> since 10:00 AM passed, next trigger must be NEXT Monday 2026-11-23 9:00 AM
        val mondayOnly = FocusSchedule(
            name = "Monday Deep Work",
            daysOfWeek = setOf(1),
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            repeatWeekly = true,
            isEnabled = true
        )

        val nextTrigger = ScheduleTimeCalculator.computeNextTriggerTimeMillis(mondayOnly, monday10am, utc)
        assertNotNull(nextTrigger)
        val resultCal = Calendar.getInstance(utc).apply { timeInMillis = nextTrigger!! }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(23, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))

        // Mid-window check at Monday 10:00 AM -> 7 hours remaining until 5:00 PM
        val remaining = ScheduleTimeCalculator.computeCurrentlyActiveRemainingMillis(mondayOnly, monday10am, utc)
        assertEquals(7 * 3_600_000L, remaining)
    }
}
