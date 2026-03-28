package com.qcb.keepaccounts.ui.format

import java.util.Locale

fun normalizeCurrencyLabel(currencyLabel: String): String {
    return currencyLabel.trim()
}

fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%,.2f", value)
}

fun formatCurrency(currencyLabel: String, value: Double): String {
    return formatNumber(value)
}

fun formatSignedCurrency(currencyLabel: String, value: Double, isIncome: Boolean): String {
    val sign = if (isIncome) "+" else "-"
    return sign + formatNumber(value)
}

fun primaryCurrencySymbol(currencyLabel: String): String {
    return ""
}
