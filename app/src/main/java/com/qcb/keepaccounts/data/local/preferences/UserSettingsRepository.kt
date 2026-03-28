package com.qcb.keepaccounts.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiTone
import com.qcb.keepaccounts.ui.model.AppThemePreset
import com.qcb.keepaccounts.ui.model.ChatBackgroundPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userSettingsDataStore by preferencesDataStore(name = "user_settings")

data class UserSettingsState(
    val initialized: Boolean = false,
    val userName: String = "主人",
    val homeSlogan: String = defaultHomeSlogan(),
    val userAvatarUri: String? = null,
    val theme: AppThemePreset = AppThemePreset.MINT,
    val aiConfig: AiAssistantConfig = AiAssistantConfig(),
    val manualCategories: List<String> = defaultCategories(),
    val ledgerCurrency: String = defaultLedgerCurrency(),
    val defaultLedgerName: String = defaultLedgerName(),
    val reminderTime: String = defaultReminderTime(),
    val monthlyBudget: Double = defaultMonthlyBudget(),
)

class UserSettingsRepository(
    private val context: Context,
) {

    val settingsFlow: Flow<UserSettingsState> = context.userSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            UserSettingsState(
                initialized = preferences[Keys.INITIALIZED] ?: false,
                userName = preferences[Keys.USER_NAME] ?: "主人",
                homeSlogan = preferences[Keys.HOME_SLOGAN] ?: defaultHomeSlogan(),
                userAvatarUri = preferences[Keys.USER_AVATAR_URI],
                theme = parseTheme(preferences[Keys.THEME]),
                manualCategories = parseCategories(preferences[Keys.MANUAL_CATEGORIES]),
                ledgerCurrency = preferences[Keys.LEDGER_CURRENCY] ?: defaultLedgerCurrency(),
                defaultLedgerName = preferences[Keys.DEFAULT_LEDGER_NAME] ?: defaultLedgerName(),
                reminderTime = preferences[Keys.REMINDER_TIME] ?: defaultReminderTime(),
                monthlyBudget = preferences[Keys.MONTHLY_BUDGET] ?: defaultMonthlyBudget(),
                aiConfig = AiAssistantConfig(
                    name = preferences[Keys.AI_NAME] ?: "Nanami",
                    avatar = preferences[Keys.AI_AVATAR] ?: "🌊",
                    avatarUri = preferences[Keys.AI_AVATAR_URI],
                    tone = parseTone(preferences[Keys.AI_TONE]),
                    chatBackground = parseBackground(preferences[Keys.AI_CHAT_BG]),
                    customChatBackgroundUri = preferences[Keys.AI_CHAT_BG_URI],
                ),
            )
        }

    suspend fun saveInitialSetup(
        userName: String,
        userAvatarUri: String?,
        theme: AppThemePreset,
        aiConfig: AiAssistantConfig,
    ) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[Keys.INITIALIZED] = true
            preferences[Keys.USER_NAME] = userName.ifBlank { "主人" }
            writeNullable(preferences, Keys.USER_AVATAR_URI, userAvatarUri)
            preferences[Keys.THEME] = theme.name
            writeAiConfig(preferences, aiConfig)
        }
    }

    suspend fun saveTheme(theme: AppThemePreset) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[Keys.THEME] = theme.name
        }
    }

    suspend fun saveUserProfile(
        userName: String,
        userAvatarUri: String?,
    ) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[Keys.USER_NAME] = userName.ifBlank { "主人" }
            writeNullable(preferences, Keys.USER_AVATAR_URI, userAvatarUri)
        }
    }

    suspend fun saveHomeSlogan(homeSlogan: String) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[Keys.HOME_SLOGAN] = homeSlogan.ifBlank { defaultHomeSlogan() }
        }
    }

    suspend fun saveAiConfig(aiConfig: AiAssistantConfig) {
        context.userSettingsDataStore.edit { preferences ->
            writeAiConfig(preferences, aiConfig)
        }
    }

    suspend fun saveManualCategories(categories: List<String>) {
        context.userSettingsDataStore.edit { preferences ->
            val normalized = categories
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            preferences[Keys.MANUAL_CATEGORIES] = if (normalized.isEmpty()) {
                defaultCategories().joinToString(separator = CATEGORY_SEPARATOR)
            } else {
                normalized.joinToString(separator = CATEGORY_SEPARATOR)
            }
        }
    }

    suspend fun saveLedgerSettings(
        ledgerCurrency: String,
        defaultLedgerName: String,
        reminderTime: String,
        monthlyBudget: Double,
    ) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[Keys.LEDGER_CURRENCY] = ledgerCurrency.ifBlank { defaultLedgerCurrency() }
            preferences[Keys.DEFAULT_LEDGER_NAME] = defaultLedgerName.ifBlank { defaultLedgerName() }
            preferences[Keys.REMINDER_TIME] = reminderTime.ifBlank { defaultReminderTime() }
            preferences[Keys.MONTHLY_BUDGET] = monthlyBudget
                .takeIf { it.isFinite() && it > 0.0 }
                ?: defaultMonthlyBudget()
        }
    }

    private fun writeAiConfig(
        preferences: MutablePreferences,
        aiConfig: AiAssistantConfig,
    ) {
        preferences[Keys.AI_NAME] = aiConfig.name.ifBlank { "Nanami" }
        preferences[Keys.AI_AVATAR] = aiConfig.avatar.ifBlank { "🌊" }
        writeNullable(preferences, Keys.AI_AVATAR_URI, aiConfig.avatarUri)
        preferences[Keys.AI_TONE] = aiConfig.tone.name
        preferences[Keys.AI_CHAT_BG] = aiConfig.chatBackground.name
        writeNullable(preferences, Keys.AI_CHAT_BG_URI, aiConfig.customChatBackgroundUri)
    }

    private fun writeNullable(
        preferences: MutablePreferences,
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value.isNullOrBlank()) {
            preferences.remove(key)
        } else {
            preferences[key] = value
        }
    }

    private object Keys {
        val INITIALIZED = booleanPreferencesKey("initialized")
        val USER_NAME = stringPreferencesKey("user_name")
        val HOME_SLOGAN = stringPreferencesKey("home_slogan")
        val USER_AVATAR_URI = stringPreferencesKey("user_avatar_uri")
        val THEME = stringPreferencesKey("theme")
        val MANUAL_CATEGORIES = stringPreferencesKey("manual_categories")
        val LEDGER_CURRENCY = stringPreferencesKey("ledger_currency")
        val DEFAULT_LEDGER_NAME = stringPreferencesKey("default_ledger_name")
        val REMINDER_TIME = stringPreferencesKey("reminder_time")
        val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")

        val AI_NAME = stringPreferencesKey("ai_name")
        val AI_AVATAR = stringPreferencesKey("ai_avatar")
        val AI_AVATAR_URI = stringPreferencesKey("ai_avatar_uri")
        val AI_TONE = stringPreferencesKey("ai_tone")
        val AI_CHAT_BG = stringPreferencesKey("ai_chat_background")
        val AI_CHAT_BG_URI = stringPreferencesKey("ai_chat_background_uri")
    }
}

private const val CATEGORY_SEPARATOR = "|||"

private fun parseCategories(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return defaultCategories()
    val parsed = raw.split(CATEGORY_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return if (parsed.isEmpty()) defaultCategories() else parsed
}

private fun defaultCategories(): List<String> {
    return listOf(
        "餐饮美食",
        "交通出行",
        "购物消费",
        "居家生活",
        "娱乐休闲",
        "医疗健康",
        "人情交际",
        "其他",
    )
}

private fun defaultLedgerCurrency(): String = "人民币"

private fun defaultLedgerName(): String = "日常账本"

private fun defaultReminderTime(): String = "21:00"

private fun defaultMonthlyBudget(): Double = 2000.0

private fun defaultHomeSlogan(): String = "劳动最光荣 💼"

private fun parseTheme(raw: String?): AppThemePreset {
    return AppThemePreset.entries.firstOrNull { it.name == raw } ?: AppThemePreset.MINT
}

private fun parseTone(raw: String?): AiTone {
    return AiTone.entries.firstOrNull { it.name == raw } ?: AiTone.HEALING
}

private fun parseBackground(raw: String?): ChatBackgroundPreset {
    return if (raw == ChatBackgroundPreset.NONE.name) {
        ChatBackgroundPreset.NONE
    } else {
        ChatBackgroundPreset.NONE
    }
}
