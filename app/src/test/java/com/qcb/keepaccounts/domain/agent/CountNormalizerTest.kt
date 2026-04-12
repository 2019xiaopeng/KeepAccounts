package com.qcb.keepaccounts.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class CountNormalizerTest {

    private val normalizer = CountNormalizer()

    @Test
    fun parseCountHint_supportsChineseCountPhrase() {
        val count = normalizer.parseCountHint("删除前十二笔记录", maxValue = 50)

        assertEquals(12, count)
    }

    @Test
    fun parseCountHint_supportsSimpleCounterWord() {
        val count = normalizer.parseCountHint("删两条", maxValue = 50)

        assertEquals(2, count)
    }

    @Test
    fun parseStandaloneNumber_supportsChineseTens() {
        val value = normalizer.parseStandaloneNumber("二十")

        assertEquals(20, value)
    }
}
