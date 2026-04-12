package com.qcb.keepaccounts.domain.agent

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalResolverV2Test {

    private val resolver = TemporalResolverV2()

    @Test
    fun resolve_supportsRelativeWeekdayWithPeriodHint() {
        val result = resolver.resolve("上周三晚上", fixedNow())

        assertNotNull(result)
        assertNotNull(result?.date)
        assertNotNull(result?.time)
        assertTrue((result?.confidence ?: 0.0) > 0.6)

        val date = result?.date ?: return
        val weekday = Calendar.getInstance().apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.month - 1)
            set(Calendar.DAY_OF_MONTH, date.day)
        }.get(Calendar.DAY_OF_WEEK)
        assertEquals(Calendar.WEDNESDAY, weekday)
        assertEquals(19, result.time?.hour)
    }

    @Test
    fun resolve_supportsRelativeMonthDay() {
        val result = resolver.resolve("上个月五号", fixedNow())

        assertNotNull(result)
        val date = result?.date ?: return
        assertEquals(2026, date.year)
        assertEquals(3, date.month)
        assertEquals(5, date.day)
    }

    @Test
    fun resolve_supportsExplicitDateAndTime() {
        val result = resolver.resolve("2026-04-08 18:20", fixedNow())

        assertNotNull(result)
        val date = result?.date ?: return
        val time = result.time ?: return
        assertEquals(2026, date.year)
        assertEquals(4, date.month)
        assertEquals(8, date.day)
        assertEquals(18, time.hour)
        assertEquals(20, time.minute)
    }

    private fun fixedNow(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
