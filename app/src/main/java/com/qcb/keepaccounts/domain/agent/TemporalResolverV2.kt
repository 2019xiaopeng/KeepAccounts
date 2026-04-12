package com.qcb.keepaccounts.domain.agent

import java.util.Calendar

data class TemporalDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

data class TemporalTime(
    val hour: Int,
    val minute: Int,
)

data class TemporalResolution(
    val date: TemporalDate? = null,
    val time: TemporalTime? = null,
    val confidence: Double,
    val trace: String,
)

class TemporalResolverV2(
    private val countNormalizer: CountNormalizer = CountNormalizer(),
) {

    fun resolve(
        text: String,
        now: Calendar = Calendar.getInstance(),
    ): TemporalResolution? {
        val input = text.trim()
        if (input.isBlank()) return null

        val dateCandidate = resolveDate(input, now)
        val timeCandidate = resolveTime(input)

        if (dateCandidate == null && timeCandidate == null) return null

        val confidence = listOfNotNull(dateCandidate?.confidence, timeCandidate?.confidence)
            .minOrNull()
            ?: 0.0
        val trace = buildString {
            dateCandidate?.let { append("date:").append(it.trace) }
            if (dateCandidate != null && timeCandidate != null) append("; ")
            timeCandidate?.let { append("time:").append(it.trace) }
        }

        return TemporalResolution(
            date = dateCandidate?.date,
            time = timeCandidate?.time,
            confidence = confidence,
            trace = trace,
        )
    }

    private fun resolveDate(text: String, now: Calendar): DateCandidate? {
        fullDateRegex.find(text)?.let { match ->
            val year = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@let
            val month = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@let
            val day = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return@let
            if (isValidDate(year, month, day)) {
                return DateCandidate(
                    date = TemporalDate(year, month, day),
                    confidence = 0.98,
                    trace = "explicit_full_date",
                )
            }
        }

        monthDayRegex.find(text)?.let { match ->
            val year = now.get(Calendar.YEAR)
            val month = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@let
            val day = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@let
            if (isValidDate(year, month, day)) {
                return DateCandidate(
                    date = TemporalDate(year, month, day),
                    confidence = 0.92,
                    trace = "explicit_month_day",
                )
            }
        }

        relativeDayOffsets.entries.firstOrNull { (keyword, _) -> text.contains(keyword) }?.let { entry ->
            val resolved = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, entry.value)
            }
            return DateCandidate(
                date = TemporalDate(
                    year = resolved.get(Calendar.YEAR),
                    month = resolved.get(Calendar.MONTH) + 1,
                    day = resolved.get(Calendar.DAY_OF_MONTH),
                ),
                confidence = 0.9,
                trace = "relative_day:${entry.key}",
            )
        }

        relativeWeekdayRegex.find(text)?.let { match ->
            val prefix = match.groupValues.getOrNull(1).orEmpty()
            val weekdayRaw = match.groupValues.getOrNull(2).orEmpty()
            val targetWeekday = chineseWeekdayMap[weekdayRaw] ?: return@let

            val currentWeekday = now.get(Calendar.DAY_OF_WEEK).toMondayBased()
            val baseOffset = targetWeekday - currentWeekday
            val weekShift = when (prefix) {
                "上" -> -7
                "下" -> 7
                else -> 0
            }

            val resolved = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, baseOffset + weekShift)
            }
            return DateCandidate(
                date = TemporalDate(
                    year = resolved.get(Calendar.YEAR),
                    month = resolved.get(Calendar.MONTH) + 1,
                    day = resolved.get(Calendar.DAY_OF_MONTH),
                ),
                confidence = 0.88,
                trace = "relative_weekday:$prefix$weekdayRaw",
            )
        }

        relativeMonthDayRegex.find(text)?.let { match ->
            val prefix = match.groupValues.getOrNull(1).orEmpty()
            val dayToken = match.groupValues.getOrNull(2).orEmpty()
            val day = dayToken.toIntOrNull() ?: countNormalizer.parseStandaloneNumber(dayToken) ?: return@let

            val resolved = (now.clone() as Calendar).apply {
                val monthOffset = when (prefix) {
                    "上" -> -1
                    "下" -> 1
                    else -> 0
                }
                add(Calendar.MONTH, monthOffset)
            }

            val year = resolved.get(Calendar.YEAR)
            val month = resolved.get(Calendar.MONTH) + 1
            if (isValidDate(year, month, day)) {
                return DateCandidate(
                    date = TemporalDate(year = year, month = month, day = day),
                    confidence = 0.86,
                    trace = "relative_month_day:$prefix$dayToken",
                )
            }
        }

        return null
    }

    private fun resolveTime(text: String): TimeCandidate? {
        val normalized = text.replace("：", ":")

        colonTimeRegex.find(normalized)?.let { match ->
            val rawHour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@let
            val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@let
            if (rawHour !in 0..23 || minute !in 0..59) return@let

            val hour = adjustHourByPeriodHint(rawHour, normalized)
            return TimeCandidate(
                time = TemporalTime(hour = hour, minute = minute),
                confidence = 0.96,
                trace = "explicit_clock",
            )
        }

        chineseTimeRegex.find(normalized)?.let { match ->
            val rawHour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@let
            if (rawHour !in 0..23) return@let

            val minute = when {
                match.groupValues.getOrNull(2) == "半" -> 30
                match.groupValues.getOrNull(2) == "一刻" -> 15
                match.groupValues.getOrNull(2) == "三刻" -> 45
                !match.groupValues.getOrNull(3).isNullOrBlank() -> match.groupValues[3].toIntOrNull() ?: return@let
                else -> 0
            }
            if (minute !in 0..59) return@let

            val hour = adjustHourByPeriodHint(rawHour, normalized)
            return TimeCandidate(
                time = TemporalTime(hour = hour, minute = minute),
                confidence = 0.94,
                trace = "explicit_chinese_clock",
            )
        }

        fuzzyPeriodHints.entries.firstOrNull { (hint, _) -> normalized.contains(hint) }?.let { entry ->
            return TimeCandidate(
                time = entry.value,
                confidence = 0.65,
                trace = "fuzzy_period:${entry.key}",
            )
        }

        return null
    }

    private fun adjustHourByPeriodHint(rawHour: Int, text: String): Int {
        var hour = rawHour

        val hasMorning = morningHints.any { hint -> text.contains(hint) }
        val hasNoon = noonHints.any { hint -> text.contains(hint) }
        val hasAfternoon = afternoonHints.any { hint -> text.contains(hint) }
        val hasEvening = eveningHints.any { hint -> text.contains(hint) }

        if (hasMorning && hour == 12) hour = 0
        if (hasNoon && hour in 1..10) hour += 12
        if (hasAfternoon && hour in 1..11) hour += 12
        if (hasEvening && hour in 1..11) hour += 12

        return hour.coerceIn(0, 23)
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        return runCatching {
            val calendar = Calendar.getInstance().apply {
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        }.isSuccess
    }

    private fun Int.toMondayBased(): Int {
        return if (this == Calendar.SUNDAY) 7 else this - 1
    }

    private data class DateCandidate(
        val date: TemporalDate,
        val confidence: Double,
        val trace: String,
    )

    private data class TimeCandidate(
        val time: TemporalTime,
        val confidence: Double,
        val trace: String,
    )

    companion object {
        private val fullDateRegex = Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?")
        private val monthDayRegex = Regex("(\\d{1,2})月(\\d{1,2})日?")
        private val relativeWeekdayRegex = Regex("(上|本|这|下)(?:周|星期)([一二三四五六日天])")
        private val relativeMonthDayRegex = Regex("(上|本|这|下)个?月\\s*([0-3]?\\d|[零一二两三四五六七八九十]{1,3})\\s*(?:号|日)?")
        private val colonTimeRegex = Regex("(?<!\\d)(\\d{1,2}):(\\d{1,2})(?!\\d)")
        private val chineseTimeRegex = Regex("(?<!\\d)(\\d{1,2})\\s*(?:点|时)(?:\\s*(半|一刻|三刻|(\\d{1,2})\\s*分?))?")

        private val relativeDayOffsets = linkedMapOf(
            "大前天" to -3,
            "前天" to -2,
            "昨天" to -1,
            "昨日" to -1,
            "今天" to 0,
            "今日" to 0,
            "明天" to 1,
            "后天" to 2,
        )

        private val chineseWeekdayMap = mapOf(
            "一" to 1,
            "二" to 2,
            "三" to 3,
            "四" to 4,
            "五" to 5,
            "六" to 6,
            "日" to 7,
            "天" to 7,
        )

        private val morningHints = listOf("凌晨", "清晨", "早晨", "早上", "上午", "今早")
        private val noonHints = listOf("中午", "午间")
        private val afternoonHints = listOf("下午", "午后")
        private val eveningHints = listOf("晚上", "傍晚", "夜里", "晚间", "今晚", "昨晚", "昨夜", "深夜")

        private val fuzzyPeriodHints = linkedMapOf(
            "清晨" to TemporalTime(8, 0),
            "早晨" to TemporalTime(8, 0),
            "早上" to TemporalTime(8, 0),
            "上午" to TemporalTime(10, 0),
            "中午" to TemporalTime(12, 0),
            "午餐" to TemporalTime(12, 0),
            "午饭" to TemporalTime(12, 0),
            "下午" to TemporalTime(15, 0),
            "傍晚" to TemporalTime(19, 0),
            "晚餐" to TemporalTime(19, 0),
            "晚饭" to TemporalTime(19, 0),
            "晚上" to TemporalTime(19, 0),
            "夜里" to TemporalTime(19, 0),
            "深夜" to TemporalTime(23, 0),
            "昨晚" to TemporalTime(19, 0),
            "昨夜" to TemporalTime(19, 0),
        )
    }
}
