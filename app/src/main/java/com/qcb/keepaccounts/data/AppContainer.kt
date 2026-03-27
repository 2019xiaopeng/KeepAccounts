package com.qcb.keepaccounts.data

import android.content.Context
import androidx.room.Room
import com.qcb.keepaccounts.data.local.AppDatabase
import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.data.repository.SiliconFlowAiGatewayStub
import com.qcb.keepaccounts.data.repository.TransactionRepository
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

interface AppContainer {
    val transactionRepository: TransactionRepository
    val aiChatGateway: AiChatGateway
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "keep_accounts.db",
    ).build()

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.siliconflow.cn/v1/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    private val siliconFlowApi: SiliconFlowApi by lazy {
        retrofit.create(SiliconFlowApi::class.java)
    }

    override val aiChatGateway: AiChatGateway by lazy {
        SiliconFlowAiGatewayStub(
            api = siliconFlowApi,
            apiKeyProvider = { "" },
        )
    }
}
