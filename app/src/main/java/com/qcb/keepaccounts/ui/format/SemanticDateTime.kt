package com.qcb.keepaccounts.ui.format

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SemanticDateTimeText(
    val dateLabel: String,
    val dateText: String,
    val timeText: String,
    val dateTimeText: String,
)

fun semanticDateTimeText(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis(),
): SemanticDateTimeText {
    val keyword = relativeDayKeyword(timestamp, nowMillis)
    val date = Date(timestamp)
    val dateLabel = keyword ?: SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(date)
    val dateText = if (keyword != null) {
        "$keyword ${SimpleDateFormat("MM-dd", Locale.CHINA).format(date)}"
    } else {
        dateLabel
    }
    val timeText = formatClockTime(timestamp)
    val dateTimeText = if (keyword != null) {
        "$keyword $timeText"
    } else {
        "${SimpleDateFormat("MM-dd", Locale.CHINA).format(date)} $timeText"
    }
    return SemanticDateTimeText(
        dateLabel = dateLabel,
        dateText = dateText,
        timeText = timeText,
        dateTimeText = dateTimeText,
    )
}

fun formatRelativeDateLabel(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    return semanticDateTimeText(timestamp, nowMillis).dateLabel
}

fun formatRelativeDateText(
    timestamp: Long,
    relativePattern: String = "MM-dd",
    absolutePattern: String = "yyyy-MM-dd",
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val keyword = relativeDayKeyword(timestamp, nowMillis)
    val formatter = if (keyword != null) {
        SimpleDateFormat(relativePattern, Locale.CHINA)
    } else {
        SimpleDateFormat(absolutePattern, Locale.CHINA)
    }
    val formattedDate = formatter.format(Date(timestamp))
    return if (keyword != null) "$keyword $formattedDate" else formattedDate
}

fun formatRelativeDateTime(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    return semanticDateTimeText(timestamp, nowMillis).dateTimeText
}

fun formatClockTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
}

fun applyCurrentTimeToDate(
    selectedTimestamp: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    val selectedDate = calendarOf(selectedTimestamp)
    val now = calendarOf(nowMillis)
    selectedDate.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
    selectedDate.set(Calendar.MINUTE, now.get(Calendar.MINUTE))
    selectedDate.set(Calendar.SECOND, 0)
    selectedDate.set(Calendar.MILLISECOND, 0)
    return selectedDate.timeInMillis
}

private fun relativeDayKeyword(
    timestamp: Long,
    nowMillis: Long,
): String? {
    val today = calendarOf(nowMillis)
    val target = calendarOf(timestamp)
    val diffDays = ((startOfDay(today).timeInMillis - startOfDay(target).timeInMillis) / MILLIS_PER_DAY).toInt()
    return when (diffDays) {
        0 -> "今天"
        1 -> "昨天"
        2 -> "前天"
        else -> null
    }
}

private fun calendarOf(timestamp: Long): Calendar {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
}

private fun startOfDay(calendar: Calendar): Calendar {
    return (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
