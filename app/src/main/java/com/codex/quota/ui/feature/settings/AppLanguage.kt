package com.codex.quota.ui.feature.settings

/** AppCompat uses an empty locale list to follow the device language. */
enum class AppLanguage(val languageTag: String) {
    FOLLOW_SYSTEM(""),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en");

    companion object {
        fun fromLanguageTags(tags: String): AppLanguage =
            entries.firstOrNull { it.languageTag == tags } ?: FOLLOW_SYSTEM
    }
}
