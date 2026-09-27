package com.codex.quota

import com.codex.quota.ui.util.displayAccountNickname
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDisplayNameTest {
    @Test fun oldGeneratedNicknameShowsOnlyPersonName() {
        assertEquals("li", displayAccountNickname("ChatGPT Plus (li)", "ChatGPT Plus", "li@example.com", "Codex"))
        assertEquals("masterli", displayAccountNickname("ChatGPT Plus (masterli)", "ChatGPT Plus", null, "Codex"))
    }

    @Test fun customNicknameIsPreserved() {
        assertEquals("Work (li)", displayAccountNickname("Work (li)", "ChatGPT Plus", "li@example.com", "Codex"))
    }

    @Test fun planOnlyNicknameUsesEmailWhenAvailable() {
        assertEquals("li", displayAccountNickname("ChatGPT Plus", "ChatGPT Plus", "li@example.com", "Codex"))
        assertEquals("Codex", displayAccountNickname("ChatGPT Plus", "ChatGPT Plus", null, "Codex"))
    }
}
