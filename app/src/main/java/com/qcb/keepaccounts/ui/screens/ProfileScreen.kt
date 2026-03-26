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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted

data class AIPersona(
    val id: String,
    val name: String,
    val tag: String,
    val icon: String,
    val desc: String,
    val gradient: Brush,
)

private val aiPersonas = listOf(
    AIPersona(
        id = "nanami",
        name = "Nanami",
        tag = "治愈系管家",
        icon = "🌸",
        desc = "温柔体贴，情绪价值拉满",
        gradient = Brush.linearGradient(listOf(Color(0xFFA8E6CF), Color(0xFFBBF7D0))),
    ),
    AIPersona(
        id = "yuki",
        name = "Yuki",
        tag = "高冷御姐",
        icon = "❄️",
        desc = "理性分析，严格控制预算",
        gradient = Brush.linearGradient(listOf(Color(0xFFA1C4FD), Color(0xFFC2E9FB))),
    ),
    AIPersona(
        id = "kuro",
        name = "Kuro",
        tag = "傲娇毒舌",
        icon = "🐈‍⬛",
        desc = "嘴硬心软，花钱会被吐槽",
        gradient = Brush.linearGradient(listOf(Color(0xFFD4A5A5), Color(0xFFFFB6B9))),
    ),
)

@Composable
fun ProfileScreen(
    onNavigateToOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePersona by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "我的", color = WarmBrown, fontWeight = FontWeight.ExtraBold)

        ProfileHeroCard(
            activePersona = activePersona,
            onSelectPersona = { activePersona = it },
        )

        MenuGroup(
            title = "资产与分类",
            items = listOf(
                MenuEntry("📁", "分类管理", "支持换色与图标修改", null, KeepAccountsDestination.CATEGORY_MANAGEMENT),
                MenuEntry("📒", "账本基础设置", null, null, KeepAccountsDestination.LEDGER_SETTINGS),
            ),
            onNavigate = onNavigateToOption,
        )

        MenuGroup(
            title = "数据安全与清理",
            items = listOf(
                MenuEntry("💾", "账单导入与导出", "纯本地 CSV/JSON 文件交换", null, KeepAccountsDestination.IMPORT_EXPORT),
                MenuEntry("🗑️", "清除缓存", null, "14.2 MB", KeepAccountsDestination.CLEAR_CACHE),
            ),
            onNavigate = onNavigateToOption,
        )

        MenuGroup(
            title = "个性化与反馈",
            items = listOf(
                MenuEntry("🎨", "主题与外观", null, "水彩薄荷绿", KeepAccountsDestination.THEME_APPEARANCE, true),
                MenuEntry("💡", "帮助与反馈中心", null, null, KeepAccountsDestination.HELP_FEEDBACK),
            ),
            onNavigate = onNavigateToOption,
        )
    }
}

@Composable
private fun ProfileHeroCard(
    activePersona: Int,
    onSelectPersona: (Int) -> Unit,
) {
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
                        .glassCard(shape = RoundedCornerShape(22.dp), glowColor = MintGreen.copy(alpha = 0.26f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🧑‍💻")
                }
                Column {
                    Text(text = "记账达人", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                    Text(text = "已坚持记账 128 天 ✨", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "AI 专属管家", color = WarmBrown.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .background(Color(0x33A8E6CF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(text = "PRO", color = Color(0xFF22C55E), fontWeight = FontWeight.ExtraBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    aiPersonas.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .background(
                                    color = if (index == activePersona) MintGreen else WarmBrown.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = if (index == activePersona) 7.dp else 2.dp),
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(aiPersonas) { index, persona ->
                    val selected = index == activePersona
                    Column(
                        modifier = Modifier
                            .fillParentMaxWidth(0.86f)
                            .background(
                                color = if (selected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(24.dp),
                            )
                            .clickable { onSelectPersona(index) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(persona.gradient, RoundedCornerShape(999.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = persona.icon)
                            }
                            Column {
                                Text(text = persona.name, color = WarmBrown, fontWeight = FontWeight.Bold)
                                Text(
                                    text = persona.tag,
                                    color = WarmBrown.copy(alpha = 0.65f),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Text(text = persona.desc, color = WarmBrownMuted, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private data class MenuEntry(
    val icon: String,
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
    onNavigate: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, color = WarmBrown.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(30.dp), glowColor = MintGreen.copy(alpha = 0.14f))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                MenuRow(item = item, onClick = { onNavigate(item.route) })
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .clickable { onClick() }
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
                Text(text = item.icon)
            }
            Column {
                Text(text = item.title, color = WarmBrown, fontWeight = FontWeight.Bold)
                if (item.subtitle != null) {
                    Text(text = item.subtitle, color = WarmBrownMuted, fontWeight = FontWeight.Medium)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.value != null) {
                Text(
                    text = item.value,
                    color = if (item.highlightValue) MintGreen else WarmBrown.copy(alpha = 0.45f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(text = "›", color = WarmBrown.copy(alpha = 0.35f), fontWeight = FontWeight.ExtraBold)
        }
    }
}
