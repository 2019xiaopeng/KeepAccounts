package com.qcb.keepaccounts.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountNormalizerTest {

    private val normalizer = AmountNormalizer()

    @Test
    fun parseAmountCandidates_supportsChineseColloquialAmount() {
        val amounts = normalizer.parseAmountCandidates("今天花了一百五")

        assertTrue(amounts.isNotEmpty())
        assertEquals(150.0, amounts.first(), 0.001)
    }

    @Test
    fun parseAmountCandidates_supportsSimpleExpression() {
        val amounts = normalizer.parseAmountCandidates("打车 10加20")

        assertTrue(amounts.any { kotlin.math.abs(it - 30.0) < 0.001 })
    }

    @Test
    fun parseAmountCandidates_supportsChineseCurrencyToken() {
        val amounts = normalizer.parseAmountCandidates("中饭三十二元")

        assertTrue(amounts.isNotEmpty())
        assertEquals(32.0, amounts.first(), 0.001)
    }
}
