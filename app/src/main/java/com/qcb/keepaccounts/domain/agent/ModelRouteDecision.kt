package com.qcb.keepaccounts.domain.agent

enum class ModelTier {
    LITE,
    PRO,
}

data class ModelRouteDecision(
    val tier: ModelTier,
    val reason: String,
)
