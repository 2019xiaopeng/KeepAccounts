package com.qcb.keepaccounts.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

object KeepAccountsIcons {
    val Assistant: ImageVector = Icons.Rounded.AutoAwesome
    val RecordDefault: ImageVector = Icons.AutoMirrored.Rounded.ReceiptLong
    val Category: ImageVector = Icons.Rounded.Category
    val Money: ImageVector = Icons.Rounded.AttachMoney
}

fun resolveCategoryIcon(category: String, remark: String = ""): ImageVector {
    val text = (category + remark).lowercase(Locale.getDefault())
    return when {
        text.contains("餐") || text.contains("吃") || text.contains("饮") -> Icons.Rounded.Restaurant
        text.contains("交") || text.contains("车") || text.contains("出行") -> Icons.Rounded.DirectionsCar
        text.contains("购") || text.contains("衣") || text.contains("电商") -> Icons.Rounded.ShoppingBag
        text.contains("居") || text.contains("家") || text.contains("房") -> Icons.Rounded.Home
        text.contains("娱") || text.contains("游") || text.contains("玩") -> Icons.Rounded.SportsEsports
        text.contains("收") || text.contains("工") || text.contains("薪") || text.contains("兼") || text.contains("稿") -> Icons.Rounded.AttachMoney
        else -> Icons.AutoMirrored.Rounded.ReceiptLong
    }
}
