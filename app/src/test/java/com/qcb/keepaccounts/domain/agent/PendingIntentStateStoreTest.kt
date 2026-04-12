package com.qcb.keepaccounts.domain.agent

import com.qcb.keepaccounts.domain.contract.AiReceiptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingIntentStateStoreTest {

    @Test
    fun getActive_returnsNullAfterExpiry() {
        val store = InMemoryPendingIntentStateStore()
        val now = 1_700_000_000_000L
        val state = PendingIntentState(
            requestId = "req-1",
            source = "planner_primary",
            action = "create",
            draft = AiReceiptDraft(
                isReceipt = true,
                action = "create",
                amount = null,
                category = "餐饮美食",
                desc = "午饭",
                recordTime = null,
                date = null,
                transactionId = null,
            ),
            missingSlots = listOf("amount"),
            createdAt = now,
            expiresAt = now + 60_000,
        )

        store.save(state)

        assertEquals(state, store.getActive(now + 30_000))
        assertNull(store.getActive(now + 60_001))
        assertNull(store.getActive(now + 60_002))
    }

    @Test
    fun clear_removesStateImmediately() {
        val store = InMemoryPendingIntentStateStore()
        val state = PendingIntentState(
            requestId = "req-2",
            source = "planner_primary",
            action = "update",
            draft = AiReceiptDraft(
                isReceipt = true,
                action = "update",
                amount = 30.0,
                category = "交通出行",
                desc = "打车",
                recordTime = null,
                date = null,
                transactionId = 99L,
            ),
            missingSlots = emptyList(),
            createdAt = 1L,
            expiresAt = 999_999L,
        )

        store.save(state)
        store.clear()

        assertNull(store.getActive(100L))
    }
}
