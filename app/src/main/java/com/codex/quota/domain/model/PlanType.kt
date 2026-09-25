package com.codex.quota.domain.model

enum class PlanType {
    PLUS,
    TEAM,
    ENTERPRISE,
    API_TIER_1,
    API_TIER_2,
    API_TIER_5,
    MOCK_DEMO;

    companion object {
        fun fromString(value: String): PlanType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: PLUS
        }
    }
}

val PlanType.isApiKeyPlan: Boolean
    get() = this == PlanType.API_TIER_1 || this == PlanType.API_TIER_2 || this == PlanType.API_TIER_5
