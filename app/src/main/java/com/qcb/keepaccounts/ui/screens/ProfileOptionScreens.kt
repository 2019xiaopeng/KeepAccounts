package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.categoryRankColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class OptionRowData(
    val icon: String,
    val title: String,
    val subtitle: String? = null,
    val value: String? = null,
)

@Composable
fun CategoryManagementScreen(
    categories: List<String>,
    usedCategoryCount: Map<String, Int>,
    accentColor: Color,
    onBack: () -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }
    var pendingDeleteUsedCount by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OptionHeader(title = "分类管理", subtitle = "与手动记账分类实时联动", onBack = onBack) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("新增分类名称，例如：学习成长") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.75f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accentColor, RoundedCornerShape(999.dp))
                        .appPressable {
                            val name = input.trim()
                            when {
                                name.isBlank() -> hint = "分类名称不能为空"
                                categories.any { it == name } -> hint = "分类已存在"
                                else -> {
                                    onAddCategory(name)
                                    input = ""
                                    hint = "已新增分类：$name"
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "add-category",
                        tint = Color.White,
                    )
                }
            }
        }

        items(categories) { category ->
            val used = usedCategoryCount[category] ?: 0
            val categoryColor = categoryRankColor(category)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(20.dp), glowColor = accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        modifier = Modifier
                            .size(14.dp)
                            .background(categoryColor, CircleShape),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = category, color = WarmBrown, fontWeight = FontWeight.Bold)
                        Text(text = "关联账单：$used", color = WarmBrownMuted)
                    }
                }
                Row(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (used == 0 && categories.size > 1) Color(0xFFFFF0F0) else Color(0xFFF3F4F6),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .appPressable {
                            when {
                                categories.size <= 1 -> hint = "至少保留一个分类"
                                else -> {
                                    pendingDeleteCategory = category
                                    pendingDeleteUsedCount = used
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "delete-category",
                        tint = if (used == 0 && categories.size > 1) Color(0xFFFF8B94) else WarmBrownMuted,
                    )
                }
            }
        }

        if (!pendingDeleteCategory.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(20.dp), glowColor = accentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "确认删除分类？", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                        Text(text = pendingDeleteCategory ?: "", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                        if (pendingDeleteUsedCount > 0) {
                            Text(
                                text = "该分类已关联 $pendingDeleteUsedCount 条记录，删除后仅从可选分类中移除",
                                color = WarmBrownMuted.copy(alpha = 0.88f),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "取消",
                            color = WarmBrownMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.appPressable {
                                pendingDeleteCategory = null
                                pendingDeleteUsedCount = 0
                            },
                        )
                        Text(
                            text = "删除",
                            color = Color(0xFFFF8B94),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.appPressable {
                                val toDelete = pendingDeleteCategory ?: return@appPressable
                                onDeleteCategory(toDelete)
                                hint = "已删除分类：$toDelete"
                                pendingDeleteCategory = null
                                pendingDeleteUsedCount = 0
                            },
                        )
                    }
                }
            }
        }

        if (!hint.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(20.dp), glowColor = accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(text = hint ?: "", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LedgerSettingsScreen(onBack: () -> Unit) {
    OptionPageScaffold(
        title = "账本基础设置",
        subtitle = "账本币种与记账默认项",
        rows = listOf(
            OptionRowData("", "默认币种", value = "人民币"),
            OptionRowData("", "默认账本", value = "日常账本"),
            OptionRowData("", "周起始日", value = "周一"),
            OptionRowData("", "记账提醒", value = "每天 21:00"),
        ),
        onBack = onBack,
    )
}

@Composable
fun ImportExportScreen(onBack: () -> Unit) {
    OptionPageScaffold(
        title = "账单导入与导出",
        subtitle = "纯本地 CSV/JSON 文件交换",
        rows = listOf(
            OptionRowData("", "导出 CSV", "适用于表格软件"),
            OptionRowData("", "导出 JSON", "适用于备份与迁移"),
            OptionRowData("", "导入 CSV", "自动字段映射"),
            OptionRowData("", "导入 JSON", "保留分类与备注"),
        ),
        onBack = onBack,
    )
}

@Composable
fun CacheCleanupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clearThumb by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf(formatCacheSize(calculateDirectorySize(context.cacheDir))) }
    var statusText by remember { mutableStateOf("包含应用缓存文件") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OptionHeader(title = "清除缓存", subtitle = "释放空间并保持流畅体验", onBack = onBack) }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(30.dp), glowColor = MintGreen.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "当前缓存占用", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                Text(text = cacheSize, color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                Text(text = statusText, color = WarmBrownMuted)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "保留缩略图缓存", color = WarmBrown, fontWeight = FontWeight.Bold)
                    Text(text = "开启后下次打开更快", color = WarmBrownMuted)
                }
                Switch(checked = clearThumb, onCheckedChange = { clearThumb = it })
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(listOf(Color(0xFFFFE7E9), Color(0xFFFFF2F4))),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .appPressable {
                        scope.launch {
                            val updatedSize = withContext(Dispatchers.IO) {
                                clearCacheDirectory(context.cacheDir, keepThumbnail = clearThumb)
                                calculateDirectorySize(context.cacheDir)
                            }
                            cacheSize = formatCacheSize(updatedSize)
                            statusText = "缓存已清理完成，可继续正常使用"
                        }
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "立即清理缓存", color = Color(0xFFFF8B94), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun calculateDirectorySize(file: File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { calculateDirectorySize(it) } ?: 0L
}

private fun clearCacheDirectory(root: File?, keepThumbnail: Boolean) {
    if (root == null || !root.exists() || !root.isDirectory) return

    root.listFiles()?.forEach { child ->
        if (child.isDirectory) {
            clearCacheDirectory(child, keepThumbnail)
            if (child.listFiles().isNullOrEmpty()) {
                child.delete()
            }
        } else if (!(keepThumbnail && isThumbnailFile(child))) {
            child.delete()
        }
    }
}

private fun isThumbnailFile(file: File): Boolean {
    val name = file.name.lowercase()
    return name.contains("thumb") || name.contains("thumbnail")
}

private fun formatCacheSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        bytes >= kb -> String.format("%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
fun ThemeAppearanceScreen(onBack: () -> Unit) {
    val palettes = listOf("水彩薄荷绿", "杏桃奶油", "浅海蓝")
    var selectedTheme by remember { mutableStateOf("水彩薄荷绿") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OptionHeader(title = "主题与外观", subtitle = "选择更适合你的治愈感视觉", onBack = onBack) }

        items(palettes) { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = MintGreen.copy(alpha = 0.16f))
                    .appPressable { selectedTheme = name }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ColorDot(Color(0xFFA8E6CF))
                        ColorDot(Color(0xFFFFB6B9))
                        ColorDot(Color(0xFFFFFDF7))
                    }
                    Text(text = name, color = WarmBrown, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = if (selectedTheme == name) "已应用" else "应用",
                    color = if (selectedTheme == name) MintGreen else WarmBrownMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Spacer(
        modifier = Modifier
            .size(10.dp)
            .background(color, RoundedCornerShape(999.dp)),
    )
}

@Composable
fun HelpFeedbackScreen(onBack: () -> Unit) {
    OptionPageScaffold(
        title = "帮助与反馈中心",
        subtitle = "常见问题与问题反馈入口",
        rows = listOf(
            OptionRowData("", "常见问题", "记账失败/分类缺失/同步说明"),
            OptionRowData("", "问题反馈", "提交截图与日志"),
            OptionRowData("", "功能建议", "告诉我们你想要的新能力"),
            OptionRowData("", "版本更新日志", "查看最近功能迭代"),
        ),
        onBack = onBack,
    )
}

@Composable
private fun OptionPageScaffold(
    title: String,
    subtitle: String,
    rows: List<OptionRowData>,
    onBack: () -> Unit,
) {
    var clickedTitle by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { OptionHeader(title = title, subtitle = subtitle, onBack = onBack) }
        items(rows) { row ->
            OptionRow(
                data = row,
                onClick = { clickedTitle = row.title },
            )
        }
        if (clickedTitle != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "已打开：${clickedTitle ?: ""}",
                        color = WarmBrownMuted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .glassCard(shape = RoundedCornerShape(22.dp), glowColor = MintGreen.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = " 返回",
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.appPressable { onBack() },
            )
            Text(text = title, color = WarmBrown, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.width(40.dp))
        }
        Text(text = subtitle, color = WarmBrownMuted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OptionRow(
    data: OptionRowData,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp), glowColor = MintGreen.copy(alpha = 0.15f))
            .appPressable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = data.icon)
            Column {
                Text(text = data.title, color = WarmBrown, fontWeight = FontWeight.Bold)
                if (data.subtitle != null) {
                    Text(text = data.subtitle, color = WarmBrownMuted)
                }
            }
        }
        Text(text = data.value ?: "", color = WarmBrown.copy(alpha = 0.45f), fontWeight = FontWeight.Bold)
    }
}
