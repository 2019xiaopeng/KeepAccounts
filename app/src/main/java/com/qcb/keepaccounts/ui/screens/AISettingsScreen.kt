package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.qcb.keepaccounts.data.local.media.persistImageForSlot
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiChatRecord
import com.qcb.keepaccounts.ui.model.AiRolePreset
import com.qcb.keepaccounts.ui.model.AiTone
import com.qcb.keepaccounts.ui.model.ChatBackgroundPreset
import com.qcb.keepaccounts.ui.model.OocGuardLevel
import com.qcb.keepaccounts.ui.model.displayName
import com.qcb.keepaccounts.ui.model.summary
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val avatarOptions = listOf("🌊", "🐶", "🐱", "🐰", "🦊", "🐼", "🌸", "✨", "🤖")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    config: AiAssistantConfig,
    chatRecords: List<AiChatRecord>,
    accentColor: Color,
    onBack: () -> Unit,
    onSave: (AiAssistantConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(config.name) }
    var avatar by rememberSaveable { mutableStateOf(config.avatar) }
    var avatarUri by rememberSaveable { mutableStateOf(config.avatarUri) }
    var tone by rememberSaveable { mutableStateOf(config.tone) }
    var rolePreset by rememberSaveable { mutableStateOf(config.rolePreset) }
    var oocGuardEnabled by rememberSaveable { mutableStateOf(config.oocGuardEnabled) }
    var oocGuardLevel by rememberSaveable { mutableStateOf(config.oocGuardLevel) }
    var background by rememberSaveable { mutableStateOf(config.chatBackground) }
    var customBackgroundUri by rememberSaveable { mutableStateOf(config.customChatBackgroundUri) }
    var selectedDateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showHistoryCalendar by rememberSaveable { mutableStateOf(false) }
    var tipText by rememberSaveable { mutableStateOf<String?>(null) }

    val dayChatRecords = remember(chatRecords, selectedDateMillis) {
        chatRecords
            .filter { isSameDay(it.timestamp, selectedDateMillis) }
            .sortedByDescending { it.timestamp }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val persisted = persistImageForSlot(
                    context = context,
                    sourceUri = uri,
                    slot = "ai_avatar",
                )
                if (!persisted.isNullOrBlank()) {
                    avatarUri = persisted
                    tipText = "已保存管家头像"
                } else {
                    tipText = "头像保存失败，请重试"
                }
            }
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val persisted = persistImageForSlot(
                    context = context,
                    sourceUri = uri,
                    slot = "ai_chat_background",
                )
                if (!persisted.isNullOrBlank()) {
                    customBackgroundUri = persisted
                    background = ChatBackgroundPreset.NONE
                    tipText = "已保存自定义对话背景"
                } else {
                    tipText = "背景保存失败，请重试"
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 16.dp),
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
                Text(text = "AI 专属管家", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Box(modifier = Modifier.size(20.dp))
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = accentColor.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "管家称呼", color = WarmBrown, fontWeight = FontWeight.Bold)
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("给你的管家起个名字", color = WarmBrownMuted) },
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
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = accentColor.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "角色设定", color = WarmBrown, fontWeight = FontWeight.Bold)
                Text(
                    text = "选择你希望 AI 管家长期扮演的角色基调",
                    color = WarmBrownMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )

                AiRoleRow(
                    title = AiRolePreset.XAVIER.displayName(),
                    desc = AiRolePreset.XAVIER.summary(),
                    selected = rolePreset == AiRolePreset.XAVIER,
                    accentColor = accentColor,
                    onClick = { rolePreset = AiRolePreset.XAVIER },
                )
                AiRoleRow(
                    title = AiRolePreset.ZAYNE.displayName(),
                    desc = AiRolePreset.ZAYNE.summary(),
                    selected = rolePreset == AiRolePreset.ZAYNE,
                    accentColor = accentColor,
                    onClick = { rolePreset = AiRolePreset.ZAYNE },
                )
                AiRoleRow(
                    title = AiRolePreset.RAFAYEL.displayName(),
                    desc = AiRolePreset.RAFAYEL.summary(),
                    selected = rolePreset == AiRolePreset.RAFAYEL,
                    accentColor = accentColor,
                    onClick = { rolePreset = AiRolePreset.RAFAYEL },
                )
                AiRoleRow(
                    title = AiRolePreset.SYLUS.displayName(),
                    desc = AiRolePreset.SYLUS.summary(),
                    selected = rolePreset == AiRolePreset.SYLUS,
                    accentColor = accentColor,
                    onClick = { rolePreset = AiRolePreset.SYLUS },
                )
                AiRoleRow(
                    title = AiRolePreset.CALEB.displayName(),
                    desc = AiRolePreset.CALEB.summary(),
                    selected = rolePreset == AiRolePreset.CALEB,
                    accentColor = accentColor,
                    onClick = { rolePreset = AiRolePreset.CALEB },
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = accentColor.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "防 OOC 工程", color = WarmBrown, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "启用角色一致性保护", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "自动抑制跳戏、元叙事和串角色口癖", color = WarmBrownMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = oocGuardEnabled,
                        onCheckedChange = { oocGuardEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor,
                        ),
                    )
                }

                if (oocGuardEnabled) {
                    OocGuardLevelRow(
                        title = "轻量",
                        desc = "只拦截明显元提示词泄露",
                        selected = oocGuardLevel == OocGuardLevel.RELAXED,
                        accentColor = accentColor,
                        onClick = { oocGuardLevel = OocGuardLevel.RELAXED },
                    )
                    OocGuardLevelRow(
                        title = "平衡",
                        desc = "默认推荐，兼顾自然度与角色稳定",
                        selected = oocGuardLevel == OocGuardLevel.BALANCED,
                        accentColor = accentColor,
                        onClick = { oocGuardLevel = OocGuardLevel.BALANCED },
                    )
                    OocGuardLevelRow(
                        title = "严格",
                        desc = "高强度角色护栏，优先不跑偏",
                        selected = oocGuardLevel == OocGuardLevel.STRICT,
                        accentColor = accentColor,
                        onClick = { oocGuardLevel = OocGuardLevel.STRICT },
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = accentColor.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "管家形象", color = WarmBrown, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = "assistant-avatar-preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White, RoundedCornerShape(999.dp)),
                        )
                    } else {
                        Text(text = avatar, fontSize = 34.sp)
                    }
                }

                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(130.dp)) {
                    items(avatarOptions) { item ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    color = if (avatar == item && avatarUri.isNullOrBlank()) {
                                        accentColor.copy(alpha = 0.2f)
                                    } else {
                                        Color.White.copy(alpha = 0.5f)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .appPressable {
                                    avatar = item
                                    avatarUri = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = item, fontSize = 22.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .appPressable {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "从相册上传头像", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "圆形裁切", color = WarmBrownMuted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = accentColor.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "性格与语气", color = WarmBrown, fontWeight = FontWeight.Bold)
                ToneRow(
                    title = "贴心治愈",
                    desc = "温柔体贴，像朋友一样关心你",
                    selected = tone == AiTone.HEALING,
                    accentColor = accentColor,
                    onClick = { tone = AiTone.HEALING },
                )
                ToneRow(
                    title = "傲娇毒舌",
                    desc = "嘴硬心软，偶尔会吐槽你的花销",
                    selected = tone == AiTone.TSUNDERE,
                    accentColor = accentColor,
                    onClick = { tone = AiTone.TSUNDERE },
                )
                ToneRow(
                    title = "理智管家",
                    desc = "冷静客观，帮你理性分析每一笔账",
                    selected = tone == AiTone.RATIONAL,
                    accentColor = accentColor,
                    onClick = { tone = AiTone.RATIONAL },
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = accentColor.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Wallpaper, contentDescription = null, tint = WarmBrown.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Text(text = "对话背景", color = WarmBrown, fontWeight = FontWeight.Bold)
                }

                BackgroundChip(
                    text = "默认随主题",
                    selected = background == ChatBackgroundPreset.NONE && customBackgroundUri.isNullOrBlank(),
                    onClick = {
                        background = ChatBackgroundPreset.NONE
                        customBackgroundUri = null
                        tipText = "已切换为默认随主题"
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .appPressable {
                            backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "上传自定义对话背景", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "长图中心裁切", color = WarmBrownMuted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }

                if (!customBackgroundUri.isNullOrBlank()) {
                    Text(
                        text = "当前使用：自定义背景",
                        color = WarmBrownMuted,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                    AsyncImage(
                        model = customBackgroundUri,
                        contentDescription = "custom-chat-background-preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.8f)
                            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(24.dp), glowColor = accentColor.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "对话记录日历", color = WarmBrown, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (showHistoryCalendar) "隐藏" else "展开",
                        color = WarmBrownMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.appPressable { showHistoryCalendar = !showHistoryCalendar },
                    )
                }

                if (showHistoryCalendar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                            .appPressable { showDatePicker = true }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "calendar",
                                tint = WarmBrown.copy(alpha = 0.72f),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = formatDateText(selectedDateMillis),
                                color = WarmBrown,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                        Text(text = "查看当天记录", color = WarmBrownMuted, fontSize = 12.sp)
                    }

                    if (dayChatRecords.isEmpty()) {
                        Text(
                            text = "当天暂无对话记录",
                            color = WarmBrownMuted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    } else {
                        dayChatRecords.forEach { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (record.role == "user") "你" else "${name.ifBlank { "AI" }}",
                                        color = if (record.role == "user") accentColor else WatermelonRed,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        text = record.content,
                                        color = WarmBrown,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    text = formatTimeText(record.timestamp),
                                    color = WarmBrownMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!tipText.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(text = tipText.orEmpty(), color = WarmBrownMuted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.82f))),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .appPressable {
                        onSave(
                            AiAssistantConfig(
                                name = name.ifBlank { "Nanami" },
                                avatar = avatar,
                                avatarUri = avatarUri,
                                tone = tone,
                                rolePreset = rolePreset,
                                oocGuardEnabled = oocGuardEnabled,
                                oocGuardLevel = oocGuardLevel,
                                chatBackground = background,
                                customChatBackgroundUri = customBackgroundUri,
                            ),
                        )
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(text = "保存设置", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateMillis = pickerState.selectedDateMillis ?: selectedDateMillis
                        showDatePicker = false
                    },
                ) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "取消")
                }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

private fun isSameDay(timestamp: Long, selectedDateMillis: Long): Boolean {
    val lhs = Calendar.getInstance().apply { timeInMillis = timestamp }
    val rhs = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    return lhs.get(Calendar.YEAR) == rhs.get(Calendar.YEAR) && lhs.get(Calendar.DAY_OF_YEAR) == rhs.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateText(timestamp: Long): String {
    return SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date(timestamp))
}

private fun formatTimeText(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
}

@Composable
private fun ToneRow(
    title: String,
    desc: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .appPressable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = desc, color = WarmBrownMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AiRoleRow(
    title: String,
    desc: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .appPressable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = desc, color = WarmBrownMuted, fontSize = 12.sp)
    }
}

@Composable
private fun OocGuardLevelRow(
    title: String,
    desc: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .appPressable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = desc, color = WarmBrownMuted, fontSize = 12.sp)
    }
}

@Composable
private fun BackgroundChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            )
            .appPressable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) WarmBrown else WarmBrown.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
