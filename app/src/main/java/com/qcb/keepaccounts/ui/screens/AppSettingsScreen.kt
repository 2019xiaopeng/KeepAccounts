package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qcb.keepaccounts.BuildConfig
import com.qcb.keepaccounts.data.local.media.persistImageForSlot
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.model.AppThemePreset
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(
    type: String,
    theme: AppThemePreset,
    userName: String,
    homeSlogan: String,
    userAvatarUri: String?,
    ledgerCurrency: String,
    defaultLedgerName: String,
    reminderTime: String,
    monthlyBudget: Double,
    accentColor: Color,
    onBack: () -> Unit,
    onThemeChange: (AppThemePreset) -> Unit,
    onUserNameChange: (String) -> Unit,
    onUserAvatarChange: (String?) -> Unit,
    onHomeSloganChange: (String) -> Unit,
    onLedgerSettingsChange: (String, String, String, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var localName by rememberSaveable(userName) { mutableStateOf(userName) }
    var localHomeSlogan by rememberSaveable(homeSlogan) { mutableStateOf(homeSlogan) }
    var localAvatarUri by rememberSaveable(userAvatarUri) { mutableStateOf(userAvatarUri) }
    var localLedgerCurrency by rememberSaveable(ledgerCurrency) { mutableStateOf(ledgerCurrency) }
    var localDefaultLedgerName by rememberSaveable(defaultLedgerName) { mutableStateOf(defaultLedgerName) }
    var localReminderTime by rememberSaveable(reminderTime) { mutableStateOf(reminderTime) }
    var localMonthlyBudget by rememberSaveable(monthlyBudget) { mutableStateOf(normalizeBudgetInput(monthlyBudget)) }
    var hintText by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val persisted = persistImageForSlot(
                    context = context,
                    sourceUri = uri,
                    slot = "user_avatar",
                )
                if (!persisted.isNullOrBlank()) {
                    localAvatarUri = persisted
                    hintText = "头像已保存"
                } else {
                    hintText = "头像保存失败，请重试"
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(20.dp), glowColor = accentColor.copy(alpha = 0.15f))
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
                        .appPressable { onBack() },
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
                    ActionButton(icon = Icons.Rounded.Download, text = "导出为 CSV", accentColor = accentColor) {
                        hintText = "已生成 CSV 导出任务"
                    }
                }
                item {
                    ActionButton(icon = Icons.Rounded.Save, text = "导出为 JSON", accentColor = accentColor) {
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
                        selectedColor = Color(0xFF7FD6C6),
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
                        selectedColor = Color(0xFFFF9E99),
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
                        selectedColor = Color(0xFF8EB5F5),
                        onClick = {
                            onThemeChange(AppThemePreset.BLUE)
                            hintText = "已应用：天空湛蓝"
                        },
                    )
                }
            }

            KeepAccountsDestination.SETTINGS_TYPE_MY_NAME -> {
                item {
                    GenericCard(title = "个人通用设置", desc = "设置你的昵称、头像和首页标语")
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(18.dp), glowColor = accentColor.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!localAvatarUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = localAvatarUri,
                                        contentDescription = "user-avatar-preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White, RoundedCornerShape(999.dp)),
                                    )
                                } else {
                                    Text(text = "🧑‍💻", fontSize = 24.sp)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = "个人头像", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "圆形中心裁切", color = WarmBrownMuted, fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = "上传",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.appPressable {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(18.dp), glowColor = accentColor.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "首页 slogan", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextField(
                            value = localHomeSlogan,
                            onValueChange = { localHomeSlogan = it },
                            placeholder = { Text("例如：劳动最光荣 💼") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.75f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(18.dp), glowColor = accentColor.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "个人名称", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextField(
                            value = localName,
                            onValueChange = { localName = it },
                            placeholder = { Text("请输入您的名称") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.75f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    ActionButton(icon = Icons.Rounded.CheckCircle, text = "保存个人设置", accentColor = accentColor) {
                        onUserNameChange(localName.ifBlank { "主人" })
                        onUserAvatarChange(localAvatarUri)
                        onHomeSloganChange(localHomeSlogan.trim().ifBlank { "劳动最光荣 💼" })
                        hintText = "个人设置已更新"
                    }
                }
            }

            KeepAccountsDestination.SETTINGS_TYPE_LEDGER -> {
                item {
                    GenericCard(
                        title = "账本基础设置",
                        desc = "支持修改默认账本、币种、月预算和提醒时间（本地持久化）",
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(18.dp), glowColor = accentColor.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(text = "默认币种", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextField(
                            value = localLedgerCurrency,
                            onValueChange = { localLedgerCurrency = it },
                            placeholder = { Text("例如：人民币") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.75f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(text = "默认账本", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextField(
                            value = localDefaultLedgerName,
                            onValueChange = { localDefaultLedgerName = it },
                            placeholder = { Text("例如：日常账本") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.75f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(text = "每月预算", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextField(
                            value = localMonthlyBudget,
                            onValueChange = { value ->
                                localMonthlyBudget = value.filter { it.isDigit() || it == '.' }
                            },
                            placeholder = { Text("例如：2000") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.75f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(text = "记账提醒时间", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextField(
                            value = localReminderTime,
                            onValueChange = { localReminderTime = it },
                            placeholder = { Text("24小时制，例如：21:00") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.75f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    ActionButton(icon = Icons.Rounded.CheckCircle, text = "保存账本设置", accentColor = accentColor) {
                        val normalizedReminder = localReminderTime.trim()
                        if (!isValidReminderTime(normalizedReminder)) {
                            hintText = "提醒时间格式需为 HH:mm，例如 21:00"
                            return@ActionButton
                        }

                        val normalizedBudget = localMonthlyBudget.trim().toDoubleOrNull()
                        if (normalizedBudget == null || normalizedBudget <= 0.0) {
                            hintText = "每月预算需为大于 0 的数字"
                            return@ActionButton
                        }

                        onLedgerSettingsChange(
                            localLedgerCurrency.trim().ifBlank { "人民币" },
                            localDefaultLedgerName.trim().ifBlank { "日常账本" },
                            normalizedReminder,
                            normalizedBudget,
                        )
                        hintText = "账本基础设置已保存"
                    }
                }
            }

            else -> {
                val issuesBaseUrl = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/issues"
                val newIssueUrl = "$issuesBaseUrl/new/choose"
                val featureIssueUrl = "$issuesBaseUrl/new?labels=enhancement&title=%5BFeature%5D%20"

                item {
                    GenericCard(
                        title = "帮助与反馈",
                        desc = "统一通过 GitHub Issues 追踪问题与需求",
                    )
                }
                item {
                    ActionButton(icon = Icons.Rounded.CheckCircle, text = "打开 Issues 列表", accentColor = accentColor) {
                        hintText = if (openExternalLink(context, issuesBaseUrl)) {
                            "已打开 GitHub Issues"
                        } else {
                            "打开链接失败，请稍后重试"
                        }
                    }
                }
                item {
                    ActionButton(icon = Icons.Rounded.Save, text = "提交问题反馈", accentColor = accentColor) {
                        hintText = if (openExternalLink(context, newIssueUrl)) {
                            "已跳转到新建 Issue 页面"
                        } else {
                            "打开链接失败，请稍后重试"
                        }
                    }
                }
                item {
                    ActionButton(icon = Icons.Rounded.Download, text = "提交功能建议", accentColor = accentColor) {
                        hintText = if (openExternalLink(context, featureIssueUrl)) {
                            "已跳转到功能建议页面"
                        } else {
                            "打开链接失败，请稍后重试"
                        }
                    }
                }
            }
        }

        if (hintText != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(16.dp), glowColor = accentColor.copy(alpha = 0.1f))
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
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.82f))),
                shape = RoundedCornerShape(999.dp),
            )
            .appPressable { onClick() }
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
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(18.dp), glowColor = MintGreen.copy(alpha = 0.1f))
            .appPressable { onClick() }
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
                    tint = selectedColor,
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
        KeepAccountsDestination.SETTINGS_TYPE_MY_NAME -> "个人设置"
        else -> "帮助与反馈"
    }
}

private fun isValidReminderTime(value: String): Boolean {
    return Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(value)
}

private fun normalizeBudgetInput(value: Double): String {
    val normalized = String.format("%.2f", value)
    return normalized.trimEnd('0').trimEnd('.')
}

private fun openExternalLink(context: Context, url: String): Boolean {
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess
}
