package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qcb.keepaccounts.ui.components.CollapsibleTopBar
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.components.rememberTopBarCollapseProgress
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AppThemePreset
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted

@Composable
fun ProfileScreen(
    aiConfig: AiAssistantConfig,
    userName: String,
    userAvatarUri: String?,
    recordedDays: Int,
    theme: AppThemePreset,
    highlightColor: Color,
    onNavigateToOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topBarProgress = rememberTopBarCollapseProgress(listState)

    Column(modifier = modifier.fillMaxSize()) {
        CollapsibleTopBar(
            title = "设置 ⚙️",
            subtitle = "个性化与偏好",
            progress = topBarProgress,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(32.dp), glowColor = MintGreen.copy(alpha = 0.2f))
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    brush = Brush.linearGradient(listOf(Color.White, Color(0xFFF0FDF4))),
                                    shape = RoundedCornerShape(22.dp),
                                )
                                .glassCard(shape = RoundedCornerShape(22.dp), glowColor = highlightColor.copy(alpha = 0.26f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!userAvatarUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = userAvatarUri,
                                    contentDescription = "profile-user-avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White, RoundedCornerShape(22.dp)),
                                )
                            } else {
                                Text(text = "🧑‍💻", fontSize = 28.sp)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(text = userName, color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text(
                                text = if (recordedDays > 0) {
                                    "已坚持记账 ${recordedDays} 天"
                                } else {
                                    "开始记账吧"
                                },
                                color = WarmBrownMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        WarmBrown.copy(alpha = 0.12f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                            .appPressable { onNavigateToOption(KeepAccountsDestination.AI_SETTINGS) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(highlightColor.copy(alpha = 0.28f), RoundedCornerShape(999.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!aiConfig.avatarUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = aiConfig.avatarUri,
                                        contentDescription = "ai-avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White, RoundedCornerShape(999.dp)),
                                    )
                                } else {
                                    Text(text = aiConfig.avatar, fontSize = 22.sp)
                                }
                            }
                            Column {
                                Text(text = aiConfig.name, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    text = when (aiConfig.tone.name) {
                                        "TSUNDERE" -> "傲娇毒舌"
                                        "RATIONAL" -> "理智管家"
                                        else -> "贴心治愈"
                                    },
                                    color = WarmBrown.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "next",
                            tint = WarmBrown.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.SmartToy, contentDescription = null, tint = WarmBrown.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                        Text(text = "AI 专属管家", color = WarmBrown.copy(alpha = 0.82f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Box(
                            modifier = Modifier
                                .background(Color(0x33A8E6CF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(text = "PRO", color = Color(0xFF22C55E), fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        item {
            MenuGroup(
                title = "资产与分类",
                items = listOf(
                    MenuEntry(Icons.Rounded.Category, "分类管理", "支持换色与图标修改", null, KeepAccountsDestination.CATEGORY_MANAGEMENT),
                    MenuEntry(
                        Icons.Rounded.MenuBook,
                        "账本基础设置",
                        null,
                        null,
                        KeepAccountsDestination.settingsRoute(KeepAccountsDestination.SETTINGS_TYPE_LEDGER),
                    ),
                ),
                highlightColor = highlightColor,
                onNavigate = onNavigateToOption,
            )
        }

        item {
            MenuGroup(
                title = "数据安全与清理",
                items = listOf(
                    MenuEntry(
                        Icons.Rounded.ImportExport,
                        "账单导入与导出",
                        "纯本地 CSV/JSON 文件交换",
                        null,
                        KeepAccountsDestination.settingsRoute(KeepAccountsDestination.SETTINGS_TYPE_EXPORT),
                    ),
                    MenuEntry(Icons.Rounded.DeleteSweep, "清除缓存", null, null, KeepAccountsDestination.CLEAR_CACHE),
                ),
                highlightColor = highlightColor,
                onNavigate = onNavigateToOption,
            )
        }

        item {
            MenuGroup(
                title = "个性化与反馈",
                items = listOf(
                    MenuEntry(
                        Icons.Rounded.RecordVoiceOver,
                        "我的称呼",
                        null,
                        userName,
                        KeepAccountsDestination.settingsRoute(KeepAccountsDestination.SETTINGS_TYPE_MY_NAME),
                    ),
                    MenuEntry(
                        Icons.Rounded.Palette,
                        "主题与外观",
                        null,
                        theme.displayName(),
                        KeepAccountsDestination.settingsRoute(KeepAccountsDestination.SETTINGS_TYPE_THEME),
                        true,
                    ),
                    MenuEntry(
                        Icons.Rounded.SmartToy,
                        "帮助与反馈中心",
                        null,
                        null,
                        KeepAccountsDestination.settingsRoute(KeepAccountsDestination.SETTINGS_TYPE_HELP),
                    ),
                ),
                highlightColor = highlightColor,
                onNavigate = onNavigateToOption,
            )
        }
        }
    }
}

private fun AppThemePreset.displayName(): String {
    return when (this) {
        AppThemePreset.MINT -> "水彩薄荷绿"
        AppThemePreset.PINK -> "樱花粉红"
        AppThemePreset.BLUE -> "天空湛蓝"
    }
}

private data class MenuEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val value: String?,
    val route: String,
    val highlightValue: Boolean = false,
)

@Composable
private fun MenuGroup(
    title: String,
    items: List<MenuEntry>,
    highlightColor: Color,
    onNavigate: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, color = WarmBrown.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(30.dp), glowColor = MintGreen.copy(alpha = 0.14f))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                MenuRow(item = item, highlightColor = highlightColor, onClick = { onNavigate(item.route) })
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuEntry, highlightColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .appPressable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(Color.White, Color(0xFFF0FDF4))),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = item.icon, contentDescription = item.title, tint = WarmBrown, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = item.title, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (item.subtitle != null) {
                    Text(text = item.subtitle, color = WarmBrownMuted, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.value != null) {
                Text(
                    text = item.value,
                    color = if (item.highlightValue) highlightColor else WarmBrown.copy(alpha = 0.45f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "next",
                tint = WarmBrown.copy(alpha = 0.35f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
