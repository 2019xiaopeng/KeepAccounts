package com.qcb.keepaccounts.ui.format

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticDateTimeTest {
    @Test
    fun semanticDateTimeText_usesRelativeLabelsWithinThreeDays() {
        val now = calendarOf(2026, 4, 7, 16, 18)
        val today = semanticDateTimeText(calendarOf(2026, 4, 7, 9, 30), now)
        val yesterday = semanticDateTimeText(calendarOf(2026, 4, 6, 12, 5), now)
        val beforeYesterday = semanticDateTimeText(calendarOf(2026, 4, 5, 8, 45), now)

        assertEquals("今天", today.dateLabel)
        assertEquals("今天 04-07", today.dateText)
        assertEquals("今天 09:30", today.dateTimeText)
        assertEquals("昨天", yesterday.dateLabel)
        assertEquals("昨天 04-06", yesterday.dateText)
        assertEquals("昨天 12:05", yesterday.dateTimeText)
        assertEquals("前天", beforeYesterday.dateLabel)
        assertEquals("前天 04-05", beforeYesterday.dateText)
        assertEquals("前天 08:45", beforeYesterday.dateTimeText)
    }

    @Test
    fun semanticDateTimeText_usesAbsoluteLabelsForOlderDates() {
        val now = calendarOf(2026, 4, 7, 16, 18)
        val text = semanticDateTimeText(calendarOf(2026, 3, 30, 8, 5), now)

        assertEquals("2026-03-30", text.dateLabel)
        assertEquals("2026-03-30", text.dateText)
        assertEquals("08:05", text.timeText)
        assertEquals("03-30 08:05", text.dateTimeText)
    }

    @Test
    fun applyCurrentTimeToDate_keepsSelectedDateAndUsesCurrentClock() {
        val selected = calendarOf(2026, 4, 5, 0, 0)
        val now = calendarOf(2026, 4, 7, 16, 18)
        val actual = applyCurrentTimeToDate(selected, now)
        val calendar = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = actual }

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, calendar.get(Calendar.MONTH))
        assertEquals(5, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(16, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(18, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
    }

    @Test
    fun formatWeChatStyleTime_formatsToday() {
        val now = calendarOf(2026, 4, 12, 20, 30)
        val target = calendarOf(2026, 4, 12, 14, 20)

        assertEquals("14:20", formatWeChatStyleTime(target, now))
    }

    @Test
    fun formatWeChatStyleTime_formatsYesterday() {
        val now = calendarOf(2026, 4, 12, 20, 30)
        val target = calendarOf(2026, 4, 11, 14, 20)

        assertEquals("昨天 14:20", formatWeChatStyleTime(target, now))
    }

    @Test
    fun formatWeChatStyleTime_formatsWeekdayWithinSixDays() {
        val now = calendarOf(2026, 4, 12, 20, 30)
        val target = calendarOf(2026, 4, 9, 14, 20)

        assertEquals("星期四 14:20", formatWeChatStyleTime(target, now))
    }

    @Test
    fun formatWeChatStyleTime_formatsSameYearDate() {
        val now = calendarOf(2026, 4, 12, 20, 30)
        val target = calendarOf(2026, 2, 5, 14, 20)

        assertEquals("2月5日 14:20", formatWeChatStyleTime(target, now))
    }

    @Test
    fun formatWeChatStyleTime_formatsCrossYearDate() {
        val now = calendarOf(2026, 4, 12, 20, 30)
        val target = calendarOf(2025, 11, 5, 14, 20)

        assertEquals("2025年11月5日 14:20", formatWeChatStyleTime(target, now))
    }

    private fun calendarOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(Locale.CHINA).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
