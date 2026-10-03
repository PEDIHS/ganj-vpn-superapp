package com.ganj.vpn.ui

import com.ganj.vpn.core.controlapi.CurrentAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramAccountIdentityTest {
    @Test
    fun `linked account exposes only display label and normalized username`() {
        val account = CurrentAccount(
            id = "00000000-0000-4000-8000-000000000010",
            displayName = "  کاربر گنج  ",
            locale = "fa-IR",
            telegramLinked = true,
            telegramUsername = "@ganj_user",
        )

        assertEquals("کاربر گنج", telegramIdentityDisplayName(account))
        assertEquals("@ganj_user", telegramIdentityUsername(account))
    }

    @Test
    fun `unlinked account never renders stale identity`() {
        val account = CurrentAccount(
            id = "00000000-0000-4000-8000-000000000010",
            displayName = "کاربر قدیمی",
            locale = "fa-IR",
            telegramLinked = false,
            telegramUsername = "old_user",
        )

        assertNull(telegramIdentityDisplayName(account))
        assertNull(telegramIdentityUsername(account))
    }

    @Test
    fun `blank optional identity falls back without inventing a label`() {
        val account = CurrentAccount(
            id = "00000000-0000-4000-8000-000000000010",
            displayName = "   ",
            locale = "fa-IR",
            telegramLinked = true,
            telegramUsername = "   ",
        )

        assertNull(telegramIdentityDisplayName(account))
        assertNull(telegramIdentityUsername(account))
    }
}
