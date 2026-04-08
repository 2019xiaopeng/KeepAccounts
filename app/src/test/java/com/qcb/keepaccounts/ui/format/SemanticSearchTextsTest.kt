package com.qcb.keepaccounts.ui.format

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchTextsTest {
    @Test
    fun buildSemanticDateSearchTexts_containsRelativeAndAbsoluteTokens() {
        val now = calendarOf(2026, 4, 8, 16, 18)
        val yesterdayLunch = calendarOf(2026, 4, 7, 12, 5)

        val tokens = buildSemanticDateSearchTexts(yesterdayLunch, now)

        assertTrue(tokens.contains("昨天"))
        assertTrue(tokens.contains("昨天 04-07"))
        assertTrue(tokens.contains("昨天 12:05"))
        assertTrue(tokens.contains("2026-04-07"))
        assertTrue(tokens.contains("12:05"))
    }

    @Test
    fun buildSemanticDateSearchTexts_oldDateDoesNotContainRelativeKeyword() {
        val now = calendarOf(2026, 4, 8, 16, 18)
        val oldDate = calendarOf(2026, 3, 30, 8, 5)

        val tokens = buildSemanticDateSearchTexts(oldDate, now)

        assertTrue(tokens.contains("2026-03-30"))
        assertTrue(tokens.contains("03-30"))
        assertTrue(tokens.contains("03-30 08:05"))
        assertFalse(tokens.contains("今天"))
        assertFalse(tokens.contains("昨天"))
        assertFalse(tokens.contains("前天"))
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
