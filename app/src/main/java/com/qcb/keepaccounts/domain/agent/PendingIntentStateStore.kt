package com.qcb.keepaccounts.domain.agent

import com.qcb.keepaccounts.domain.contract.AiReceiptDraft

data class PendingIntentState(
    val requestId: String,
    val source: String,
    val action: String,
    val draft: AiReceiptDraft,
    val missingSlots: List<String>,
    val createdAt: Long,
    val expiresAt: Long,
)

interface PendingIntentStateStore {
    fun getActive(nowMillis: Long = System.currentTimeMillis()): PendingIntentState?

    fun save(state: PendingIntentState)

    fun clear()
}

class InMemoryPendingIntentStateStore : PendingIntentStateStore {
    @Volatile
    private var state: PendingIntentState? = null

    override fun getActive(nowMillis: Long): PendingIntentState? {
        val current = state ?: return null
        if (current.expiresAt < nowMillis) {
            state = null
            return null
        }
        return current
    }

    override fun save(state: PendingIntentState) {
        this.state = state
    }

    override fun clear() {
        state = null
    }
}
