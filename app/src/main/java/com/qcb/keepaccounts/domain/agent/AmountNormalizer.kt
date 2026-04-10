package com.qcb.keepaccounts.domain.agent

import kotlin.math.round

class AmountNormalizer(
    private val countNormalizer: CountNormalizer = CountNormalizer(),
) {

    fun parseAmountCandidates(text: String): List<Double> {
        val input = text.trim()
        if (input.isBlank()) return emptyList()

        val candidates = mutableListOf<Double>()

        expressionRegex.findAll(input).forEach { match ->
            evaluateExpression(match.value)?.let { value ->
                if (value > 0.0) candidates += value
            }
        }

        withCurrencyRegex.findAll(input).forEach { match ->
            parseAmountToken(match.groupValues.getOrNull(1).orEmpty())?.let { value ->
                if (value > 0.0) candidates += value
            }
        }

        plainAmountRegex.findAll(input).forEach { match ->
            match.groupValues.getOrNull(1)?.toDoubleOrNull()?.let { value ->
                if (value > 0.0 && value < 100000.0) {
                    candidates += value
                }
            }
        }

        if (candidates.isEmpty() && hasAmountContext(input)) {
            chineseTokenRegex.findAll(input).forEach { match ->
                parseAmountToken(match.value)?.let { value ->
                    if (value > 0.0 && value < 100000.0) {
                        candidates += value
                    }
                }
            }
        }

        return dedupe(candidates)
    }

    private fun hasAmountContext(text: String): Boolean {
        return amountContextHints.any { hint -> text.contains(hint) }
    }

    private fun dedupe(values: List<Double>): List<Double> {
        val distinct = mutableListOf<Double>()
        values.forEach { value ->
            if (distinct.none { existing -> kotlin.math.abs(existing - value) < 0.0001 }) {
                distinct += round(value * 100.0) / 100.0
            }
        }
        return distinct
    }

    private fun evaluateExpression(segment: String): Double? {
        val normalized = segment
            .replace(" ", "")
            .replace("＋", "+")
            .replace("－", "-")

        val tokens = expressionTokenRegex.findAll(normalized).map { it.value }.toList()
        if (tokens.size < 3) return null

        var index = 0
        var current = parseAmountToken(tokens[index]) ?: return null
        index += 1

        while (index < tokens.size - 1) {
            val op = tokens[index]
            val right = parseAmountToken(tokens[index + 1]) ?: return null
            current = when (op) {
                "+", "加" -> current + right
                "-", "减" -> current - right
                else -> return null
            }
            index += 2
        }

        return current
    }

    private fun parseAmountToken(raw: String): Double? {
        val token = raw.trim()
        if (token.isBlank()) return null

        token.toDoubleOrNull()?.let { return it }

        if (token.contains('点')) {
            val parts = token.split('点')
            if (parts.size != 2) return null

            val integer = parseChineseInteger(parts[0]).toDouble()
            val fractionDigits = parts[1].mapNotNull { ch -> chineseDigitMap[ch.toString()] }
            if (fractionDigits.isEmpty()) return null

            val fraction = fractionDigits.joinToString(separator = "")
            return "$integer.$fraction".toDoubleOrNull()
        }

        return parseChineseInteger(token).toDouble()
    }

    private fun parseChineseInteger(raw: String): Int {
        val token = raw.trim()
        if (token.isBlank()) return 0

        if (colloquialHundredRegex.matches(token)) {
            val first = chineseDigitMap[token.substring(0, 1)] ?: return 0
            val second = chineseDigitMap[token.substring(2, 3)] ?: return 0
            return first * 100 + second * 10
        }

        if (colloquialThousandRegex.matches(token)) {
            val first = chineseDigitMap[token.substring(0, 1)] ?: return 0
            val second = chineseDigitMap[token.substring(2, 3)] ?: return 0
            return first * 1000 + second * 100
        }

        var total = 0
        var section = 0
        var number = 0

        token.forEach { ch ->
            val char = ch.toString()
            val digit = chineseDigitMap[char]
            if (digit != null) {
                number = digit
                return@forEach
            }

            when (char) {
                "十", "百", "千" -> {
                    val unit = smallUnitMap[char] ?: 1
                    if (number == 0) number = 1
                    section += number * unit
                    number = 0
                }

                "万", "亿" -> {
                    val unit = largeUnitMap[char] ?: 1
                    section += number
                    if (section == 0) section = 1
                    total += section * unit
                    section = 0
                    number = 0
                }

                else -> return 0
            }
        }

        return total + section + number
    }

    companion object {
        private val withCurrencyRegex = Regex("([\\d.零一二两三四五六七八九十百千万亿点]+)\\s*(?:元|块|块钱|人民币|RMB|rmb|￥|¥)")
        private val plainAmountRegex = Regex("(?<!\\d)(\\d{1,6}(?:\\.\\d{1,2})?)(?!\\d)")
        private val chineseTokenRegex = Regex("[零一二两三四五六七八九十百千万亿点]{2,12}")
        private val expressionRegex = Regex("[\\d零一二两三四五六七八九十百千万亿点\\s]+(?:加|减|\\+|\\-)[\\d零一二两三四五六七八九十百千万亿点\\s]+(?:\\s*(?:加|减|\\+|\\-)\\s*[\\d零一二两三四五六七八九十百千万亿点\\s]+)*")
        private val expressionTokenRegex = Regex("[\\d.零一二两三四五六七八九十百千万亿点]+|[+\\-加减]")
        private val colloquialHundredRegex = Regex("[一二两三四五六七八九]百[一二两三四五六七八九]")
        private val colloquialThousandRegex = Regex("[一二两三四五六七八九]千[一二两三四五六七八九]")

        private val amountContextHints = listOf(
            "花了", "金额", "改成", "改为", "付款", "支付", "消费", "支出", "收款", "收入", "记账", "记一笔",
        )

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

        private val largeUnitMap = mapOf(
            "万" to 10000,
            "亿" to 100000000,
        )
    }
}
