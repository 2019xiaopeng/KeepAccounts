package com.qcb.keepaccounts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.model.AppThemePreset
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted

@Composable
fun AppSettingsScreen(
    type: String,
    theme: AppThemePreset,
    userName: String,
    onBack: () -> Unit,
    onThemeChange: (AppThemePreset) -> Unit,
    onUserNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var localName by rememberSaveable(userName) { mutableStateOf(userName) }
    var hintText by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "back",
                    tint = WarmBrown.copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onBack() },
                )
                Text(text = titleForType(type), color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Box(modifier = Modifier.size(20.dp))
            }
        }

        when (type) {
            KeepAccountsDestination.SETTINGS_TYPE_EXPORT -> {
                item {
                    GenericCard(
                        title = "本地导入与导出",
                        desc = "支持 CSV / JSON，本地文件交换",
                    )
                }
                item {
                    ActionButton(icon = Icons.Rounded.Download, text = "导出为 CSV") {
                        hintText = "已生成 CSV 导出任务"
                    }
                }
                item {
                    ActionButton(icon = Icons.Rounded.Save, text = "导出为 JSON") {
                        hintText = "已生成 JSON 导出任务"
                    }
                }
            }

            KeepAccountsDestination.SETTINGS_TYPE_THEME -> {
                item {
                    GenericCard(
                        title = "主题与外观",
                        desc = "将主题应用到首页、对话、账本和设置页",
                    )
                }
                item {
                    ThemePickerRow(
                        title = "水彩薄荷绿",
                        selected = theme == AppThemePreset.MINT,
                        colors = listOf(Color(0xFFA8E6CF), Color(0xFF8FD3BC)),
                        onClick = {
                            onThemeChange(AppThemePreset.MINT)
                            hintText = "已应用：水彩薄荷绿"
                        },
                    )
                }
                item {
                    ThemePickerRow(
                        title = "樱花粉红",
                        selected = theme == AppThemePreset.PINK,
                        colors = listOf(Color(0xFFFFB7B2), Color(0xFFFF9E99)),
                        onClick = {
                            onThemeChange(AppThemePreset.PINK)
                            hintText = "已应用：樱花粉红"
                        },
                    )
                }
                item {
                    ThemePickerRow(
                        title = "天空湛蓝",
                        selected = theme == AppThemePreset.BLUE,
                        colors = listOf(Color(0xFFA1C4FD), Color(0xFF8EB5F5)),
                        onClick = {
                            onThemeChange(AppThemePreset.BLUE)
                            hintText = "已应用：天空湛蓝"
                        },
                    )
                }
            }

            KeepAccountsDestination.SETTINGS_TYPE_MY_NAME -> {
                item {
                    GenericCard(title = "我的称呼", desc = "AI 对话中会使用这个称呼")
                }
                item {
                    TextField(
                        value = localName,
                        onValueChange = { localName = it },
                        placeholder = { Text("例如：主人、宝宝") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.75f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(18.dp), glowColor = MintGreen.copy(alpha = 0.1f)),
                    )
                }
                item {
                    ActionButton(icon = Icons.Rounded.CheckCircle, text = "保存称呼") {
                        onUserNameChange(localName.ifBlank { "主人" })
                        hintText = "称呼已更新"
                    }
                }
            }

            KeepAccountsDestination.SETTINGS_TYPE_LEDGER -> {
                item { GenericCard(title = "账本基础设置", desc = "默认账本、币种、提醒时间等") }
                item { GenericCard(title = "默认币种", desc = "CNY ¥") }
                item { GenericCard(title = "默认账本", desc = "日常账本") }
                item { GenericCard(title = "记账提醒", desc = "每天 21:00") }
            }

            else -> {
                item { GenericCard(title = "帮助与反馈", desc = "常见问题 / 版本日志 / 功能建议") }
                item { GenericCard(title = "常见问题", desc = "记账失败、分类缺失、同步说明") }
                item { GenericCard(title = "问题反馈", desc = "提交截图与日志") }
                item { GenericCard(title = "功能建议", desc = "告诉我们你想要的新能力") }
            }
        }

        if (hintText != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(16.dp), glowColor = MintGreen.copy(alpha = 0.1f))
                        .padding(10.dp),
                ) {
                    Text(text = hintText ?: "", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun GenericCard(
    title: String,
    desc: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(text = desc, color = WarmBrownMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(MintGreen, Color(0xFF88D4B4))),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Text(text = text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ThemePickerRow(
    title: String,
    selected: Boolean,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(18.dp), glowColor = MintGreen.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Rounded.Palette, contentDescription = null, tint = WarmBrown.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Text(text = title, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, RoundedCornerShape(999.dp)),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MintGreen,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

private fun titleForType(type: String): String {
    return when (type) {
        KeepAccountsDestination.SETTINGS_TYPE_EXPORT -> "导入与导出"
        KeepAccountsDestination.SETTINGS_TYPE_THEME -> "主题与外观"
        KeepAccountsDestination.SETTINGS_TYPE_LEDGER -> "账本基础设置"
        KeepAccountsDestination.SETTINGS_TYPE_MY_NAME -> "我的称呼"
        else -> "帮助与反馈"
    }
}
