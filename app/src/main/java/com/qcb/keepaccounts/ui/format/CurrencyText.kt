package com.qcb.keepaccounts.ui.format

import java.util.Locale

fun normalizeCurrencyLabel(currencyLabel: String): String {
    return currencyLabel.trim().ifBlank { "CNY ¥" }
}

fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%,.2f", value)
}

fun formatCurrency(currencyLabel: String, value: Double): String {
    return "${normalizeCurrencyLabel(currencyLabel)} ${formatNumber(value)}"
}

fun formatSignedCurrency(currencyLabel: String, value: Double, isIncome: Boolean): String {
    val sign = if (isIncome) "+" else "-"
    return sign + formatCurrency(currencyLabel, value)
}

fun primaryCurrencySymbol(currencyLabel: String): String {
    val normalized = normalizeCurrencyLabel(currencyLabel)
    return normalized.split(" ").lastOrNull()?.ifBlank { normalized } ?: normalized
}
