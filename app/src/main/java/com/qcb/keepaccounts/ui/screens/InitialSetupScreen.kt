package com.qcb.keepaccounts.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.qcb.keepaccounts.ui.model.AiTone
import com.qcb.keepaccounts.ui.model.AppThemePreset
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import kotlinx.coroutines.launch

private val onboardingAvatarOptions = listOf("🌊", "🐶", "🐱", "🐰", "🦊", "🐼", "🌸", "✨", "🤖")

@Composable
fun InitialSetupScreen(
    initialUserName: String,
    initialUserAvatarUri: String?,
    initialTheme: AppThemePreset,
    initialAiConfig: AiAssistantConfig,
    initialMonthlyBudget: Double,
    onComplete: (String, String?, AppThemePreset, AiAssistantConfig, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userName by rememberSaveable {
        mutableStateOf(initialUserName.takeIf { it.isNotBlank() && it != "主人" } ?: "")
    }
    var userAvatarUri by rememberSaveable { mutableStateOf(initialUserAvatarUri) }
    var theme by rememberSaveable { mutableStateOf(initialTheme) }
    var monthlyBudgetInput by rememberSaveable(initialMonthlyBudget) {
        mutableStateOf(normalizeBudgetInput(initialMonthlyBudget))
    }

    var aiName by rememberSaveable { mutableStateOf(initialAiConfig.name) }
    var aiAvatar by rememberSaveable { mutableStateOf(initialAiConfig.avatar) }
    var aiAvatarUri by rememberSaveable { mutableStateOf(initialAiConfig.avatarUri) }
    var aiTone by rememberSaveable { mutableStateOf(initialAiConfig.tone) }

    val userAvatarPicker = rememberLauncherForActivityResult(
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
                    userAvatarUri = persisted
                }
            }
        }
    }

    val aiAvatarPicker = rememberLauncherForActivityResult(
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
                    aiAvatarUri = persisted
                }
            }
        }
    }

    val selectedColor = when (theme) {
        AppThemePreset.MINT -> Color(0xFF7FD6C6)
        AppThemePreset.PINK -> Color(0xFFFF9E99)
        AppThemePreset.BLUE -> Color(0xFF8EB5F5)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        selectedColor.copy(alpha = 0.24f),
                        selectedColor.copy(alpha = 0.12f),
                        Color.White,
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .glassCard(shape = RoundedCornerShape(22.dp), glowColor = selectedColor.copy(alpha = 0.16f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = "欢迎来到 KeepAccounts", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(text = "先完成一次初始化，后续可在设置中修改", color = WarmBrownMuted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(22.dp), glowColor = selectedColor.copy(alpha = 0.14f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "你的称呼与头像", color = WarmBrown, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(999.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!userAvatarUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = userAvatarUri,
                                    contentDescription = "setup-user-avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White, RoundedCornerShape(999.dp)),
                                )
                            } else {
                                Text(text = "🧑‍💻", fontSize = 24.sp)
                            }
                        }
                        Column {
                            Text(text = "个人头像", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "支持从相册上传", color = WarmBrownMuted, fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = "上传",
                        color = selectedColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable {
                            userAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
                TextField(
                    value = userName,
                    onValueChange = { userName = it },
                    placeholder = { Text("请输入AI对您的称呼", color = WarmBrownMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.75f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.62f),
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
                    .glassCard(shape = RoundedCornerShape(22.dp), glowColor = selectedColor.copy(alpha = 0.14f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "初始化每月预算", color = WarmBrown, fontWeight = FontWeight.Bold)
                TextField(
                    value = monthlyBudgetInput,
                    onValueChange = { value ->
                        monthlyBudgetInput = value.filter { it.isDigit() || it == '.' }
                    },
                    placeholder = { Text("请输入每月预算，例如：2000", color = WarmBrownMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.75f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.62f),
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
                    .glassCard(shape = RoundedCornerShape(22.dp), glowColor = selectedColor.copy(alpha = 0.14f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Palette, contentDescription = null, tint = WarmBrown.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                    Text(text = "主题颜色", color = WarmBrown, fontWeight = FontWeight.Bold)
                }

                ThemeOptionRow("水彩薄荷绿", theme == AppThemePreset.MINT, listOf(Color(0xFFA8E6CF), Color(0xFF8FD3BC))) {
                    theme = AppThemePreset.MINT
                }
                ThemeOptionRow("樱花粉红", theme == AppThemePreset.PINK, listOf(Color(0xFFFFB7B2), Color(0xFFFF9E99))) {
                    theme = AppThemePreset.PINK
                }
                ThemeOptionRow("天空湛蓝", theme == AppThemePreset.BLUE, listOf(Color(0xFFA1C4FD), Color(0xFF8EB5F5))) {
                    theme = AppThemePreset.BLUE
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(22.dp), glowColor = selectedColor.copy(alpha = 0.14f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "AI 管家设置", color = WarmBrown, fontWeight = FontWeight.Bold)

                TextField(
                    value = aiName,
                    onValueChange = { aiName = it },
                    placeholder = { Text("AI 管家称呼，例如：Nanami", color = WarmBrownMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.75f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.62f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "管家形象", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "上传头像",
                        color = selectedColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable {
                            aiAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!aiAvatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = aiAvatarUri,
                            contentDescription = "setup-ai-avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White, RoundedCornerShape(999.dp)),
                        )
                    } else {
                        Text(text = aiAvatar, fontSize = 28.sp)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(122.dp),
                ) {
                    items(onboardingAvatarOptions) { item ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    color = if (aiAvatar == item && aiAvatarUri.isNullOrBlank()) selectedColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable {
                                    aiAvatar = item
                                    aiAvatarUri = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = item, fontSize = 22.sp)
                        }
                    }
                }

                ToneOptionRow("贴心治愈", aiTone == AiTone.HEALING, selectedColor) { aiTone = AiTone.HEALING }
                ToneOptionRow("傲娇毒舌", aiTone == AiTone.TSUNDERE, selectedColor) { aiTone = AiTone.TSUNDERE }
                ToneOptionRow("理智管家", aiTone == AiTone.RATIONAL, selectedColor) { aiTone = AiTone.RATIONAL }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(listOf(selectedColor, selectedColor.copy(alpha = 0.84f))),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .clickable {
                        val setupBudget = monthlyBudgetInput.trim().toDoubleOrNull()
                            ?.takeIf { it > 0.0 }
                            ?: initialMonthlyBudget.coerceAtLeast(1.0)
                        onComplete(
                            userName.trim().ifBlank { "我" },
                            userAvatarUri,
                            theme,
                            AiAssistantConfig(
                                name = aiName.ifBlank { "Nanami" },
                                avatar = aiAvatar,
                                avatarUri = aiAvatarUri,
                                tone = aiTone,
                                chatBackground = initialAiConfig.chatBackground,
                                customChatBackgroundUri = initialAiConfig.customChatBackgroundUri,
                            ),
                            setupBudget,
                        )
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(text = "完成初始化并进入应用", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.58f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(color, RoundedCornerShape(999.dp)),
                )
            }
            if (selected) {
                Text(text = "已选", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ToneOptionRow(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

private fun normalizeBudgetInput(value: Double): String {
    val normalized = String.format("%.2f", value)
    return normalized.trimEnd('0').trimEnd('.')
}
