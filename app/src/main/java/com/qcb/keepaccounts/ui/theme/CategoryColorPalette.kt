package com.qcb.keepaccounts.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

private val canonicalCategoryColors = mapOf(
    "餐饮美食" to Color(0xFF1D2A52),
    "交通出行" to Color(0xFF3D4470),
    "购物消费" to Color(0xFF6A9CFF),
    "居家生活" to Color(0xFF8DD6A5),
    "娱乐休闲" to Color(0xFFFFC75F),
    "医疗健康" to Color(0xFFFF7F9D),
    "人情交际" to Color(0xFFFF9F68),
    "其他" to Color(0xFF9FA0A6),
    "收入" to IncomeGreen,
)

private val fallbackCategoryColors = listOf(
    Color(0xFF6E6F75),
    Color(0xFF7C83FD),
    Color(0xFF56C596),
    Color(0xFFFFA94D),
    Color(0xFFFF8787),
)

fun canonicalCategoryName(raw: String): String {
    val name = raw.trim()
    if (name.isBlank()) return "其他"

    return when {
        name.contains("餐饮") || name.contains("美食") || name.contains("奶茶") || name.contains("饮品") -> "餐饮美食"
        name.contains("交通") || name.contains("出行") || name.contains("打车") || name.contains("公交") || name.contains("地铁") -> "交通出行"
        name.contains("购物") || name.contains("消费") || name.contains("网购") || name.contains("服饰") || name.contains("数码") -> "购物消费"
        name.contains("居家") || name.contains("家居") || name.contains("房租") || name.contains("水电") || name.contains("日用") -> "居家生活"
        name.contains("娱乐") || name.contains("休闲") || name.contains("电影") || name.contains("游戏") || name.contains("旅游") -> "娱乐休闲"
        name.contains("医疗") || name.contains("健康") || name.contains("药") || name.contains("医院") || name.contains("体检") -> "医疗健康"
        name.contains("人情") || name.contains("交际") || name.contains("礼") || name.contains("社交") || name.contains("红包") -> "人情交际"
        name.contains("收入") || name.contains("工资") || name.contains("薪资") || name.contains("奖金") || name.contains("报销") || name.contains("收款") -> "收入"
        else -> name
    }
}

fun categoryRankColor(rawCategory: String): Color {
    val canonical = canonicalCategoryName(rawCategory)
    canonicalCategoryColors[canonical]?.let { return it }
    val index = abs(canonical.hashCode()) % fallbackCategoryColors.size
    return fallbackCategoryColors[index]
}
