package com.ganj.vpn.ui

import com.ganj.vpn.core.controlapi.SupportCategory
import com.ganj.vpn.core.controlapi.SupportPriority
import com.ganj.vpn.core.controlapi.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GanjSupportUiTest {
    @Test
    fun `support statuses have explicit Persian labels`() {
        assertEquals("باز", supportStatusLabel(SupportStatus.OPEN))
        assertEquals("منتظر پاسخ شما", supportStatusLabel(SupportStatus.WAITING_USER))
        assertEquals("منتظر پشتیبانی", supportStatusLabel(SupportStatus.WAITING_SUPPORT))
        assertEquals("حل‌شده", supportStatusLabel(SupportStatus.RESOLVED))
        assertEquals("بسته", supportStatusLabel(SupportStatus.CLOSED))
    }

    @Test
    fun `all support categories and priorities have non blank labels`() {
        SupportCategory.entries.forEach { category ->
            assertEquals(false, supportCategoryLabel(category).isBlank())
        }
        SupportPriority.entries.forEach { priority ->
            assertEquals(false, supportPriorityLabel(priority).isBlank())
        }
    }
}
