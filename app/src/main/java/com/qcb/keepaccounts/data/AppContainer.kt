package com.qcb.keepaccounts.data

import android.content.Context
import androidx.room.Room
import com.qcb.keepaccounts.BuildConfig
import com.qcb.keepaccounts.data.agent.AgentJsonlMirrorStore
import com.qcb.keepaccounts.data.agent.AgentQualityFeedbackRepository
import com.qcb.keepaccounts.data.agent.AgentReplayService
import com.qcb.keepaccounts.data.agent.RoomAgentRunLogger
import com.qcb.keepaccounts.data.local.AppDatabase
import com.qcb.keepaccounts.data.local.preferences.UserSettingsRepository
import com.qcb.keepaccounts.data.remote.github.GitHubReleaseApi
import com.qcb.keepaccounts.data.repository.AppUpdateRepository
import com.qcb.keepaccounts.data.repository.ChatRepository
import com.qcb.keepaccounts.data.repository.SiliconFlowPlannerGateway
import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.data.repository.SiliconFlowAiGateway
import com.qcb.keepaccounts.data.repository.TransactionRepository
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

interface AppContainer {
    val transactionRepository: TransactionRepository
    val chatRepository: ChatRepository
    val aiChatGateway: AiChatGateway
    val userSettingsRepository: UserSettingsRepository
    val appUpdateRepository: AppUpdateRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val apiKey = normalizeApiKey(BuildConfig.SILICONFLOW_API_KEY)
    private val apiBaseUrl = normalizeBaseUrl(BuildConfig.SILICONFLOW_API_URL)
    private val modelName = normalizeModel(BuildConfig.SILICONFLOW_MODEL)
    private val githubOwner = BuildConfig.GITHUB_OWNER
    private val githubRepo = BuildConfig.GITHUB_REPO

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "keep_accounts.db",
    ).addMigrations(
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
    ).build()

    private val jsonlMirrorStore: AgentJsonlMirrorStore by lazy {
        AgentJsonlMirrorStore(
            File(context.filesDir, "agent_logs/agent_tool_calls.jsonl"),
        )
    }

    private val agentRunLogger by lazy {
        RoomAgentRunLogger(
            runDao = database.agentRunDao(),
            toolCallDao = database.agentToolCallDao(),
            jsonlMirrorStore = jsonlMirrorStore,
        )
    }

    private val agentReplayService by lazy {
        AgentReplayService(
            runDao = database.agentRunDao(),
            toolCallDao = database.agentToolCallDao(),
            jsonlMirrorStore = jsonlMirrorStore,
        )
    }

    private val agentQualityFeedbackRepository by lazy {
        AgentQualityFeedbackRepository(
            dao = database.agentQualityFeedbackDao(),
        )
    }

    private val agentPlanner by lazy {
        SiliconFlowPlannerGateway(
            api = siliconFlowApi,
            model = modelName,
        )
    }

    private val aiHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
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
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(aiHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SiliconFlowApi::class.java)
    }

    private val gitHubReleaseApi: GitHubReleaseApi by lazy {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubReleaseApi::class.java)
    }

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }

    override val chatRepository: ChatRepository by lazy {
        ChatRepository(
            chatMessageDao = database.chatMessageDao(),
            transactionDao = database.transactionDao(),
            aiChatGateway = aiChatGateway,
            aiModel = modelName,
            agentRunLogger = agentRunLogger,
            agentReplayService = agentReplayService,
            qualityFeedbackRepository = agentQualityFeedbackRepository,
            agentPlanner = agentPlanner,
            plannerShadowEnabled = true,
            plannerPrimaryEnabled = true,
            plannerPrimaryRolloutPercent = 10,
            plannerPrimaryMinConfidence = 0.75,
        )
    }

    override val aiChatGateway: AiChatGateway by lazy {
        SiliconFlowAiGateway(siliconFlowApi)
    }

    override val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(context)
    }

    override val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepository(
            api = gitHubReleaseApi,
            owner = githubOwner,
            repo = githubRepo,
            context = context,
        )
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

    val normalizedHost = withScheme.replace("api.siliconflow.com", "api.siliconflow.cn")

    val withVersion = if (normalizedHost.contains("/v1")) {
        normalizedHost
    } else {
        normalizedHost.trimEnd('/') + "/v1"
    }
    return withVersion.trimEnd('/') + "/"
}

private fun normalizeApiKey(raw: String): String {
    return raw.trim()
        .replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "")
        .trim()
}

private fun normalizeModel(raw: String): String {
    return raw.trim().ifBlank { "deepseek-ai/DeepSeek-V3" }
}
