package com.qcb.keepaccounts.domain.agent

class CountNormalizer {

    fun parseCountHint(
        text: String,
        maxValue: Int = 100,
    ): Int? {
        val input = text.trim()
        if (input.isBlank()) return null

        digitCounterRegex.find(input)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
            return value.coerceIn(1, maxValue)
        }

        chineseCounterRegex.find(input)?.groupValues?.getOrNull(1)?.let { raw ->
            parseStandaloneNumber(raw)?.let { value ->
                if (value > 0) return value.coerceIn(1, maxValue)
            }
        }

        frontDigitRegex.find(input)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
            return value.coerceIn(1, maxValue)
        }

        frontChineseRegex.find(input)?.groupValues?.getOrNull(1)?.let { raw ->
            parseStandaloneNumber(raw)?.let { value ->
                if (value > 0) return value.coerceIn(1, maxValue)
            }
        }

        return null
    }

    fun parseStandaloneNumber(raw: String): Int? {
        val token = raw.trim()
        if (token.isBlank()) return null

        token.toIntOrNull()?.let { return it }

        if (token == "十") return 10

        if (colloquialHundredRegex.matches(token)) {
            val first = chineseDigitMap[token.substring(0, 1)] ?: return null
            val second = chineseDigitMap[token.substring(2, 3)] ?: return null
            return first * 100 + second * 10
        }

        var total = 0
        var section = 0
        var number = 0

        token.forEach { ch ->
            val current = ch.toString()
            val digit = chineseDigitMap[current]
            if (digit != null) {
                number = digit
                return@forEach
            }

            when (current) {
                "十", "百", "千" -> {
                    val unit = smallUnitMap[current] ?: return null
                    if (number == 0) number = 1
                    section += number * unit
                    number = 0
                }

                "万" -> {
                    section += number
                    if (section == 0) section = 1
                    total += section * 10000
                    section = 0
                    number = 0
                }

                else -> return null
            }
        }

        val result = total + section + number
        return if (result > 0) result else null
    }

    companion object {
        private val digitCounterRegex = Regex("(\\d{1,3})\\s*(?:笔|条|个)")
        private val chineseCounterRegex = Regex("([零一二两三四五六七八九十百千]{1,6})\\s*(?:笔|条|个)")
        private val frontDigitRegex = Regex("前\\s*(\\d{1,3})\\s*(?:笔|条|个)?")
        private val frontChineseRegex = Regex("前\\s*([零一二两三四五六七八九十百千]{1,6})\\s*(?:笔|条|个)?")
        private val colloquialHundredRegex = Regex("[一二两三四五六七八九]百[一二两三四五六七八九]")

        private val chineseDigitMap = mapOf(
            "零" to 0,
            "一" to 1,
            "二" to 2,
            "两" to 2,
            "三" to 3,
            "四" to 4,
            "五" to 5,
            "六" to 6,
            "七" to 7,
            "八" to 8,
            "九" to 9,
        )

        private val smallUnitMap = mapOf(
            "十" to 10,
            "百" to 100,
            "千" to 1000,
        )
    }
}
