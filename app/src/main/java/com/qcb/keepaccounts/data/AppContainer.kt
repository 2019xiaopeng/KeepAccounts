package com.qcb.keepaccounts.data

import android.content.Context
import androidx.room.Room
import com.qcb.keepaccounts.BuildConfig
import com.qcb.keepaccounts.data.local.AppDatabase
import com.qcb.keepaccounts.data.local.preferences.UserSettingsRepository
import com.qcb.keepaccounts.data.repository.ChatRepository
import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.data.repository.SiliconFlowAiGateway
import com.qcb.keepaccounts.data.repository.TransactionRepository
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

interface AppContainer {
    val transactionRepository: TransactionRepository
    val chatRepository: ChatRepository
    val aiChatGateway: AiChatGateway
    val userSettingsRepository: UserSettingsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val apiKey = BuildConfig.SILICONFLOW_API_KEY.trim()
    private val apiBaseUrl = normalizeBaseUrl(BuildConfig.SILICONFLOW_API_URL)

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "keep_accounts.db",
    ).build()

    private val aiHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Content-Type", "application/json")

                if (apiKey.isNotBlank()) {
                    builder.header("Authorization", "Bearer $apiKey")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    private val siliconFlowApi: SiliconFlowApi by lazy {
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(aiHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(SiliconFlowApi::class.java)
    }

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }

    override val chatRepository: ChatRepository by lazy {
        ChatRepository(
            chatMessageDao = database.chatMessageDao(),
            transactionDao = database.transactionDao(),
            aiChatGateway = aiChatGateway,
        )
    }

    override val aiChatGateway: AiChatGateway by lazy {
        SiliconFlowAiGateway(siliconFlowApi)
    }

    override val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(context)
    }
}

private fun normalizeBaseUrl(raw: String): String {
    val defaultBase = "https://api.siliconflow.cn/v1/"
    if (raw.isBlank()) return defaultBase

    val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) {
        raw
    } else {
        "https://$raw"
    }

    val withVersion = if (withScheme.contains("/v1")) {
        withScheme
    } else {
        withScheme.trimEnd('/') + "/v1"
    }
    return withVersion.trimEnd('/') + "/"
}
