package com.codex.quota

import com.codex.quota.ui.feature.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun followSystemIsTheDefaultForAnEmptyAppLocaleList() {
        assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguage.fromLanguageTags(""))
    }

    @Test
    fun explicitAppLocalesRemainSelectable() {
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-CN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"))
    }
}
