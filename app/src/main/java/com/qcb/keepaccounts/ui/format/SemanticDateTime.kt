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

fun formatWeChatStyleTime(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val now = calendarOf(nowMillis)
    val target = calendarOf(timestamp)
    val dayDiff = ((startOfDay(now).timeInMillis - startOfDay(target).timeInMillis) / MILLIS_PER_DAY).toInt()
    val clock = formatClockTime(timestamp)

    return when {
        dayDiff == 0 -> clock
        dayDiff == 1 -> "昨天 $clock"
        dayDiff in 2..6 -> "${weekDayLabel(target.get(Calendar.DAY_OF_WEEK))} $clock"
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> {
            "${target.get(Calendar.MONTH) + 1}月${target.get(Calendar.DAY_OF_MONTH)}日 $clock"
        }

        else -> {
            "${target.get(Calendar.YEAR)}年${target.get(Calendar.MONTH) + 1}月${target.get(Calendar.DAY_OF_MONTH)}日 $clock"
        }
    }
}

fun buildSemanticDateSearchTexts(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis(),
): List<String> {
    val date = Date(timestamp)
    val semantic = semanticDateTimeText(timestamp, nowMillis)
    val absoluteTexts = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(date),
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(date),
        SimpleDateFormat("MM-dd", Locale.CHINA).format(date),
        SimpleDateFormat("M月d日", Locale.CHINA).format(date),
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(date),
        SimpleDateFormat("HH:mm", Locale.CHINA).format(date),
    )

    return buildList {
        addAll(absoluteTexts)
        add(semantic.dateLabel)
        add(semantic.dateText)
        add(semantic.dateTimeText)
    }.distinct()
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

private fun weekDayLabel(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        Calendar.MONDAY -> "星期一"
        Calendar.TUESDAY -> "星期二"
        Calendar.WEDNESDAY -> "星期三"
        Calendar.THURSDAY -> "星期四"
        Calendar.FRIDAY -> "星期五"
        Calendar.SATURDAY -> "星期六"
        else -> "星期日"
    }
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
