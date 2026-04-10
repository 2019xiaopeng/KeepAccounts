package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.domain.agent.AiReceiptDraft
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class ChatRepositoryTryUpdateTest {

    @Test
    fun testResolveUpdateTargetTransaction() = runBlocking {
        val mockDao = mockk<TransactionDao>()
        val repository = ChatRepository(
            chatMessageDao = mockk(),
            transactionDao = mockDao,
            aiChatGateway = mockk()
        )

        val now = Calendar.getInstance().timeInMillis
        // The most recent one is coffee
        val tx1 = TransactionEntity(id = 1, type = 0, amount = 10.0, categoryName = "咖啡", categoryIcon = "", remark = "", recordTimestamp = now, createdTimestamp = now)
        // The second one is taxi
        val tx2 = TransactionEntity(id = 2, type = 0, amount = 20.0, categoryName = "交通出行", categoryIcon = "", remark = "打车", recordTimestamp = now - 10000, createdTimestamp = now)

        coEvery { mockDao.getRecentTransactions(120) } returns listOf(tx1, tx2)

        val method = repository.javaClass.getDeclaredMethod("resolveUpdateTargetTransaction", AiReceiptDraft::class.java, String::class.java)
        method.isAccessible = true

        val draft = AiReceiptDraft(action = "update", category = "交通出行")
        val userInput = "帮我把刚刚打车记录改成15"
        
        val result = method.invoke(repository, draft, userInput) as TransactionEntity?
        
        assertEquals(2L, result?.id)
    }
}
